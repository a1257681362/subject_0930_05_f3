package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientChecks;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.NutrientDosingRequest;
import com.evops.plant.dto.TankLedgerRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientMaterialLot;
import com.evops.plant.entity.NutrientTank;
import com.evops.plant.entity.NutrientTankLedger;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.NutrientDosingMapper;
import com.evops.plant.mapper.NutrientTankLedgerMapper;
import com.evops.plant.mapper.NutrientTankMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投加与水箱台账：投加记录水箱/批号/浓度/操作人（快照留痕，只追加）；
 * 过期批号禁止投加；补加与排放分别记账，两条账互不冲抵。
 */
@Service
public class NutrientDosingService {

    private final NutrientTankMapper tankMapper;
    private final NutrientDosingMapper dosingMapper;
    private final NutrientTankLedgerMapper ledgerMapper;
    private final BatchMapper batchMapper;
    private final NutrientMaterialService materialService;
    private final com.evops.plant.support.OperatorResolver operatorResolver;

    public NutrientDosingService(NutrientTankMapper tankMapper, NutrientDosingMapper dosingMapper,
                                 NutrientTankLedgerMapper ledgerMapper, BatchMapper batchMapper,
                                 NutrientMaterialService materialService,
                                 com.evops.plant.support.OperatorResolver operatorResolver) {
        this.tankMapper = tankMapper;
        this.dosingMapper = dosingMapper;
        this.ledgerMapper = ledgerMapper;
        this.batchMapper = batchMapper;
        this.materialService = materialService;
        this.operatorResolver = operatorResolver;
    }

    /** 投加登记：水箱+原料批号+浓度+操作人；批号过期或不在库直接拒绝。 */
    @Transactional
    public NutrientDosing dose(Long tankId, NutrientDosingRequest request) {
        NutrientTank tank = requireActiveTank(tankId);
        NutrientMaterial material = materialService.requireMaterial(request.getMaterialId());
        NutrientMaterialLot lot = materialService.requireLot(request.getLotId());
        if (!lot.getMaterialId().equals(material.getId())) {
            throw new IllegalArgumentException("批号 " + lot.getLotNo() + " 不属于原料 " + material.getMaterialCode());
        }
        if (!NutrientStatus.LOT_IN_STOCK.equals(lot.getStatus())) {
            throw new IllegalStateException("批号不在库，禁止投加: " + lot.getLotNo());
        }
        if (NutrientChecks.isExpired(lot.getExpiryDate(), LocalDate.now())) {
            // 原料过期不能使用
            throw new IllegalStateException("批号已过期，禁止投加: " + lot.getLotNo()
                    + "（有效期至 " + lot.getExpiryDate() + "）");
        }
        if (request.getConcentrationPpm() == null
                || request.getConcentrationPpm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("投加浓度必须大于 0");
        }
        if (request.getBatchId() != null) {
            Batch batch = batchMapper.selectById(request.getBatchId());
            if (batch == null) {
                throw new IllegalArgumentException("种植批次不存在: " + request.getBatchId());
            }
            if (PfStatus.BATCH_HARVESTED.equals(batch.getStatus())) {
                throw new IllegalStateException("批次已采收，不能再投加: " + batch.getBatchNo());
            }
        }

        Long operatorId = CurrentOperator.id();
        NutrientDosing dosing = new NutrientDosing();
        dosing.setTankId(tank.getId());
        dosing.setBatchId(request.getBatchId());
        dosing.setMaterialId(material.getId());
        dosing.setMaterialName(material.getMaterialName());
        dosing.setLotId(lot.getId());
        dosing.setLotNo(lot.getLotNo());
        dosing.setConcentrationPpm(request.getConcentrationPpm());
        dosing.setDosedAt(request.getDosedAt() == null ? LocalDateTime.now() : request.getDosedAt());
        dosing.setOperatorId(operatorId);
        dosing.setOperatorName(operatorResolver.nameOf(operatorId));
        dosingMapper.insert(dosing);
        return dosing;
    }

    /** 补加记账（REPLENISH）：与排放分别记账，只追加。 */
    @Transactional
    public NutrientTankLedger replenish(Long tankId, TankLedgerRequest request) {
        return appendLedger(tankId, NutrientStatus.TXN_REPLENISH, request);
    }

    /** 排放记账（DISCHARGE）：与补加分别记账，只追加。 */
    @Transactional
    public NutrientTankLedger discharge(Long tankId, TankLedgerRequest request) {
        return appendLedger(tankId, NutrientStatus.TXN_DISCHARGE, request);
    }

    private NutrientTankLedger appendLedger(Long tankId, String txnType, TankLedgerRequest request) {
        NutrientTank tank = requireActiveTank(tankId);
        if (request.getVolumeL() == null || request.getVolumeL().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("体积必须大于 0");
        }
        Long operatorId = CurrentOperator.id();
        NutrientTankLedger ledger = new NutrientTankLedger();
        ledger.setTankId(tank.getId());
        ledger.setTxnType(txnType);
        ledger.setVolumeL(request.getVolumeL());
        ledger.setEcAfter(request.getEcAfter());
        ledger.setReason(request.getReason());
        ledger.setTxnAt(LocalDateTime.now());
        ledger.setOperatorId(operatorId);
        ledger.setOperatorName(operatorResolver.nameOf(operatorId));
        ledgerMapper.insert(ledger);
        return ledger;
    }

    public NutrientTank requireActiveTank(Long tankId) {
        NutrientTank tank = tankMapper.selectById(tankId);
        if (tank == null) {
            throw new IllegalArgumentException("水箱不存在: " + tankId);
        }
        if (!NutrientStatus.TANK_ACTIVE.equals(tank.getStatus())) {
            throw new IllegalStateException("水箱已停用: " + tank.getTankCode());
        }
        return tank;
    }

    public List<NutrientTank> listTanks() {
        return tankMapper.selectList(new QueryWrapper<NutrientTank>().orderByAsc("tank_code"));
    }

    public List<NutrientDosing> dosingsOfTank(Long tankId) {
        return dosingMapper.selectList(new QueryWrapper<NutrientDosing>()
                .eq("tank_id", tankId)
                .orderByDesc("dosed_at"));
    }

    /** 台账查询：txnType 可空（空=全部）；补加/排放分开传参即分别查账。 */
    public List<NutrientTankLedger> ledgerOfTank(Long tankId, String txnType) {
        return ledgerMapper.selectList(new QueryWrapper<NutrientTankLedger>()
                .eq("tank_id", tankId)
                .eq(txnType != null, "txn_type", txnType)
                .orderByDesc("txn_at"));
    }
}
