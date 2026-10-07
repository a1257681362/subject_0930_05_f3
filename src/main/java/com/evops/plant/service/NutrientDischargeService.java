package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.DischargeRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.NutrientDischarge;
import com.evops.plant.entity.TankLedger;
import com.evops.plant.entity.WaterTank;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.NutrientDischargeMapper;
import com.evops.plant.mapper.TankLedgerMapper;
import com.evops.plant.support.NumberGenerator;
import com.evops.plant.support.OperatorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 营养液排放（与补加分别记账）：独立单据、独立台账变动类型 DISCHARGE。
 * 两步审批：提交只停留 PENDING（不动台账）；批准时校验余额充足后记负向台账。
 * 审批人不得是提交人（与补加同一两人原则）。
 */
@Service
public class NutrientDischargeService {

    private static final Logger log = LoggerFactory.getLogger(NutrientDischargeService.class);

    private final NutrientDischargeMapper dischargeMapper;
    private final TankLedgerMapper ledgerMapper;
    private final BatchMapper batchMapper;
    private final WaterTankService tankService;
    private final NumberGenerator numberGenerator;
    private final OperatorResolver operatorResolver;

    public NutrientDischargeService(NutrientDischargeMapper dischargeMapper, TankLedgerMapper ledgerMapper,
                                    BatchMapper batchMapper, WaterTankService tankService,
                                    NumberGenerator numberGenerator, OperatorResolver operatorResolver) {
        this.dischargeMapper = dischargeMapper;
        this.ledgerMapper = ledgerMapper;
        this.batchMapper = batchMapper;
        this.tankService = tankService;
        this.numberGenerator = numberGenerator;
        this.operatorResolver = operatorResolver;
    }

    @Transactional
    public NutrientDischarge submit(DischargeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = CurrentOperator.id();
        String operatorName = operatorResolver.nameOf(operatorId);

        WaterTank tank = tankService.requireTank(request.getTankId());
        if (!PfStatus.ACTIVE.equals(tank.getStatus())) {
            throw new IllegalStateException("水箱已停用，不能排放: " + tank.getTankCode());
        }
        if (request.getBatchId() != null) {
            Batch batch = batchMapper.selectById(request.getBatchId());
            if (batch == null) {
                throw new IllegalArgumentException("种植批次不存在: " + request.getBatchId());
            }
        }
        // 提交时做余额预检（批准时再以台账实时余额终审，避免排队中的单据叠加超排）
        BigDecimal balance = tankService.currentBalance(tank.getId());
        if (request.getVolumeLiters().compareTo(balance) > 0) {
            throw new IllegalStateException("排放体积 " + request.getVolumeLiters()
                    + "L 超过水箱当前液位 " + balance + "L: " + tank.getTankCode());
        }

        NutrientDischarge discharge = new NutrientDischarge();
        discharge.setDischargeNo(numberGenerator.nextDischargeNo());
        discharge.setTankId(tank.getId());
        discharge.setBatchId(request.getBatchId());
        discharge.setVolumeLiters(request.getVolumeLiters());
        discharge.setMeasuredEc(request.getMeasuredEc());
        discharge.setReason(request.getReason());
        discharge.setStatus(NutrientStatus.ORDER_PENDING);
        discharge.setOperatorId(operatorId);
        discharge.setOperatorName(operatorName);
        discharge.setSubmittedAt(now);
        discharge.setSubmittedBy(operatorId);
        discharge.setSubmittedName(operatorName);
        try {
            dischargeMapper.insert(discharge);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("排放单号冲突，请重试");
        }
        log.info("[营养液排放] 提交待处理单#{} {}: 水箱={}, 体积={}L, 提交人={}",
                discharge.getId(), discharge.getDischargeNo(), tank.getTankCode(),
                request.getVolumeLiters(), operatorName);
        return discharge;
    }

    @Transactional
    public NutrientDischarge approve(Long dischargeId) {
        NutrientDischarge discharge = requirePending(dischargeId);
        Long approverId = CurrentOperator.id();
        NutrientDosingService.ensureDifferentPerson(approverId, discharge.getSubmittedBy(),
                discharge.getDischargeNo());

        LocalDateTime now = LocalDateTime.now();
        WaterTank tank = tankService.requireTank(discharge.getTankId());
        BigDecimal balance = tankService.currentBalance(tank.getId());
        BigDecimal after = balance.subtract(discharge.getVolumeLiters());
        if (after.signum() < 0) {
            throw new IllegalStateException("排放后液位将为负（当前 " + balance + "L，申请排放 "
                    + discharge.getVolumeLiters() + "L），不能批准: " + discharge.getDischargeNo());
        }

        TankLedger ledger = new TankLedger();
        ledger.setTankId(tank.getId());
        ledger.setChangeType(NutrientStatus.CHANGE_DISCHARGE);
        ledger.setRefType(NutrientStatus.CHANGE_DISCHARGE);
        ledger.setRefId(discharge.getId());
        ledger.setRefNo(discharge.getDischargeNo());
        ledger.setVolumeChangeLiters(discharge.getVolumeLiters().negate());
        ledger.setBalanceVolumeLiters(after);
        ledger.setMeasuredEc(discharge.getMeasuredEc());
        ledger.setOperatorId(approverId);
        ledger.setOperatorName(operatorResolver.nameOf(approverId));
        ledger.setOccurredAt(now);
        ledgerMapper.insert(ledger);

        discharge.setStatus(NutrientStatus.ORDER_APPROVED);
        discharge.setApprovedAt(now);
        discharge.setApprovedBy(approverId);
        discharge.setApprovedName(operatorResolver.nameOf(approverId));
        discharge.setDischargedAt(now);
        dischargeMapper.updateById(discharge);
        log.info("[营养液排放] 批准单#{} {}：水箱 {} 余额 {}L，审批人={}",
                discharge.getId(), discharge.getDischargeNo(), tank.getTankCode(), after,
                discharge.getApprovedName());
        return discharge;
    }

    @Transactional
    public NutrientDischarge reject(Long dischargeId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("驳回原因不能为空");
        }
        NutrientDischarge discharge = requirePending(dischargeId);
        Long approverId = CurrentOperator.id();
        NutrientDosingService.ensureDifferentPerson(approverId, discharge.getSubmittedBy(),
                discharge.getDischargeNo());
        LocalDateTime now = LocalDateTime.now();
        discharge.setStatus(NutrientStatus.ORDER_REJECTED);
        discharge.setApprovedAt(now);
        discharge.setApprovedBy(approverId);
        discharge.setApprovedName(operatorResolver.nameOf(approverId));
        discharge.setRejectReason(reason.trim());
        dischargeMapper.updateById(discharge);
        log.info("[营养液排放] 驳回单#{} {}：原因={}，驳回人={}",
                discharge.getId(), discharge.getDischargeNo(), reason, discharge.getApprovedName());
        return discharge;
    }

    public NutrientDischarge getDischarge(Long dischargeId) {
        NutrientDischarge discharge = dischargeMapper.selectById(dischargeId);
        if (discharge == null) {
            throw new IllegalArgumentException("排放单不存在: " + dischargeId);
        }
        return discharge;
    }

    public List<NutrientDischarge> list(Long tankId, Long batchId, String status) {
        return dischargeMapper.selectList(new QueryWrapper<NutrientDischarge>()
                .eq(tankId != null, "tank_id", tankId)
                .eq(batchId != null, "batch_id", batchId)
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }

    /** 待处理（审批只完成一半）：所有 PENDING 排放单。 */
    public List<NutrientDischarge> pending() {
        return dischargeMapper.selectList(new QueryWrapper<NutrientDischarge>()
                .eq("status", NutrientStatus.ORDER_PENDING)
                .orderByAsc("submitted_at"));
    }

    private NutrientDischarge requirePending(Long dischargeId) {
        NutrientDischarge discharge = getDischarge(dischargeId);
        if (!NutrientStatus.ORDER_PENDING.equals(discharge.getStatus())) {
            throw new IllegalStateException("仅待处理排放单可审批，当前状态: " + discharge.getStatus()
                    + "（" + discharge.getDischargeNo() + "）");
        }
        return discharge;
    }
}
