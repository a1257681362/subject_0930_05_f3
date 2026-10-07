package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.DosingItemRequest;
import com.evops.plant.dto.DosingRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientDosingItem;
import com.evops.plant.entity.NutrientRecipe;
import com.evops.plant.entity.NutrientRecipeItem;
import com.evops.plant.entity.NutrientStock;
import com.evops.plant.entity.TankLedger;
import com.evops.plant.entity.WaterTank;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.NutrientDosingItemMapper;
import com.evops.plant.mapper.NutrientDosingMapper;
import com.evops.plant.mapper.NutrientStockMapper;
import com.evops.plant.mapper.TankLedgerMapper;
import com.evops.plant.support.NumberGenerator;
import com.evops.plant.support.OperatorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营养液补加（投加记账）：
 *  - 提交只生成 PENDING 待处理单：不扣库存、不记台账；记录水箱、配液水量、实测 EC（浓度）、操作人；
 *  - 审批为独立第二步，且审批人不得是提交人（职责分离）；批准时再次校验批号效期，
 *    条件更新原子扣减库存，余额不足整笔回滚，并写水箱台账（只追加）；
 *  - 批次钉版：批次首次批准的补加钉住配方版本，之后该批次只能引用同一版本（归档版仍可用），
 *    历史批次因此继续引用当时的配方版本；行级快照保证配方与批号双向可追溯。
 */
@Service
public class NutrientDosingService {

    private static final Logger log = LoggerFactory.getLogger(NutrientDosingService.class);

    /** 克 → 千克 */
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal(1000);

    private final NutrientDosingMapper dosingMapper;
    private final NutrientDosingItemMapper itemMapper;
    private final NutrientStockMapper stockMapper;
    private final TankLedgerMapper ledgerMapper;
    private final BatchMapper batchMapper;
    private final NutrientRecipeService recipeService;
    private final NutrientMaterialService materialService;
    private final WaterTankService tankService;
    private final NumberGenerator numberGenerator;
    private final OperatorResolver operatorResolver;

    public NutrientDosingService(NutrientDosingMapper dosingMapper, NutrientDosingItemMapper itemMapper,
                                 NutrientStockMapper stockMapper, TankLedgerMapper ledgerMapper,
                                 BatchMapper batchMapper, NutrientRecipeService recipeService,
                                 NutrientMaterialService materialService, WaterTankService tankService,
                                 NumberGenerator numberGenerator, OperatorResolver operatorResolver) {
        this.dosingMapper = dosingMapper;
        this.itemMapper = itemMapper;
        this.stockMapper = stockMapper;
        this.ledgerMapper = ledgerMapper;
        this.batchMapper = batchMapper;
        this.recipeService = recipeService;
        this.materialService = materialService;
        this.tankService = tankService;
        this.numberGenerator = numberGenerator;
        this.operatorResolver = operatorResolver;
    }

    // ------------------------------------------------------------------
    // 提交：PENDING 待处理（不扣库存、不记台账）
    // ------------------------------------------------------------------

    @Transactional
    public NutrientDosing submit(DosingRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        Long operatorId = CurrentOperator.id();
        String operatorName = operatorResolver.nameOf(operatorId);

        WaterTank tank = tankService.requireTank(request.getTankId());
        if (!PfStatus.ACTIVE.equals(tank.getStatus())) {
            throw new IllegalStateException("水箱已停用，不能补加: " + tank.getTankCode());
        }
        Batch batch = null;
        if (request.getBatchId() != null) {
            batch = batchMapper.selectById(request.getBatchId());
            if (batch == null) {
                throw new IllegalArgumentException("种植批次不存在: " + request.getBatchId());
            }
            if (PfStatus.BATCH_HARVESTED.equals(batch.getStatus())) {
                throw new IllegalStateException("批次已采收，不能继续补加: " + batch.getBatchNo());
            }
        }

        NutrientRecipe recipe = null;
        Map<Long, NutrientRecipeItem> recipeItems = new HashMap<>();
        if (request.getRecipeId() != null) {
            recipe = recipeService.requireRecipe(request.getRecipeId());
            if (NutrientStatus.RECIPE_DRAFT.equals(recipe.getStatus())) {
                throw new IllegalStateException("配方草稿未发布，不能用于补加: " + recipe.getRecipeCode());
            }
            for (NutrientRecipeItem item : recipeService.itemsOf(recipe.getId())) {
                recipeItems.put(item.getMaterialId(), item);
            }
        }
        // 批次钉版：已钉版本必须与本次一致；历史批次继续引用当时版本
        if (batch != null) {
            NutrientDosing pinned = latestApprovedForBatch(batch.getId());
            if (pinned != null && pinned.getRecipeId() != null) {
                if (recipe == null || !recipe.getId().equals(pinned.getRecipeId())) {
                    NutrientRecipe pinnedRecipe = recipeService.requireRecipe(pinned.getRecipeId());
                    throw new IllegalStateException("批次 " + batch.getBatchNo()
                            + " 已钉住营养配方 " + pinnedRecipe.getRecipeCode() + " v" + pinnedRecipe.getVersionNo()
                            + "，历史批次只能继续引用当时版本");
                }
            }
        }

        // 容量预检（批准时会再次校验）
        BigDecimal balance = tankService.currentBalance(tank.getId());
        if (tank.getCapacityLiters() != null
                && balance.add(request.getWaterVolumeLiters()).compareTo(tank.getCapacityLiters()) > 0) {
            throw new IllegalStateException("补加后液位 " + balance.add(request.getWaterVolumeLiters())
                    + "L 超过水箱容量 " + tank.getCapacityLiters() + "L: " + tank.getTankCode());
        }

        NutrientDosing dosing = new NutrientDosing();
        dosing.setDosingNo(numberGenerator.nextDosingNo());
        dosing.setBatchId(batch == null ? null : batch.getId());
        dosing.setTankId(tank.getId());
        dosing.setRecipeId(recipe == null ? null : recipe.getId());
        dosing.setRecipeVersion(recipe == null ? null : recipe.getVersionNo());
        dosing.setWaterVolumeLiters(request.getWaterVolumeLiters());
        dosing.setMeasuredEc(request.getMeasuredEc());
        dosing.setMeasuredPh(request.getMeasuredPh());
        dosing.setStatus(NutrientStatus.ORDER_PENDING);
        dosing.setOperatorId(operatorId);
        dosing.setOperatorName(operatorName);
        dosing.setSubmittedAt(now);
        dosing.setSubmittedBy(operatorId);
        dosing.setSubmittedName(operatorName);
        dosing.setRemark(request.getRemark());
        try {
            dosingMapper.insert(dosing);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("补加单号冲突，请重试");
        }

        // 行：批号必须存在、在库、未过期；配方剂量×水量折算克数（允许手工覆盖）
        Set<Long> stockIds = new HashSet<>();
        List<NutrientDosingItem> lines = new ArrayList<>();
        for (DosingItemRequest lineReq : request.getItems()) {
            if (!stockIds.add(lineReq.getStockId())) {
                throw new IllegalArgumentException("同一批号在一张补加单中只能出现一行: stockId=" + lineReq.getStockId());
            }
            NutrientStock stock = materialService.requireUsableStock(lineReq.getStockId(), today);

            NutrientRecipeItem recipeItem = recipe == null ? null : recipeItems.get(stock.getMaterialId());
            if (recipe != null && recipeItem == null) {
                throw new IllegalStateException("批号所属原料不在配方 " + recipe.getRecipeCode()
                        + " v" + recipe.getVersionNo() + " 中，不能按该配方补加");
            }
            String tankGroup;
            BigDecimal dosedG;
            if (recipeItem != null) {
                tankGroup = recipeItem.getTankGroup();
                BigDecimal auto = recipeItem.getDoseGPerTonne()
                        .multiply(request.getWaterVolumeLiters())
                        .divide(new BigDecimal(1000), 3, RoundingMode.HALF_UP);
                dosedG = lineReq.getDosedG() == null ? auto : lineReq.getDosedG();
            } else {
                // 手工配液（不钉配方）：母液罐取原料默认罐，实投克数必填
                tankGroup = materialService.requireMaterial(stock.getMaterialId()).getDefaultTank();
                if (tankGroup == null) {
                    throw new IllegalStateException("原料未配置默认母液罐，手工补加必须显式提供配方行");
                }
                if (lineReq.getDosedG() == null) {
                    throw new IllegalArgumentException("非配方补加必须填写实投克数: stockId=" + stock.getId());
                }
                dosedG = lineReq.getDosedG();
            }
            if (dosedG.signum() <= 0) {
                throw new IllegalArgumentException("实投克数必须为正数: 批号 " + stock.getLotNo());
            }

            NutrientDosingItem line = new NutrientDosingItem();
            line.setDosingId(dosing.getId());
            line.setMaterialId(stock.getMaterialId());
            line.setMaterialCode(stockMaterialCode(stock));
            line.setMaterialName(stockMaterialName(stock));
            line.setStockId(stock.getId());
            line.setLotNo(stock.getLotNo());
            line.setExpiryDate(stock.getExpiryDate());
            line.setTankGroup(tankGroup);
            line.setDoseGPerTonne(recipeItem == null ? null : recipeItem.getDoseGPerTonne());
            line.setDosedG(dosedG);
            itemMapper.insert(line);
            lines.add(line);
        }
        log.info("[营养液补加] 提交待处理单#{} {}: 水箱={}, 批次={}, 配方={}v{}, 水量={}L, 行={}, 提交人={}",
                dosing.getId(), dosing.getDosingNo(), tank.getTankCode(),
                batch == null ? "-" : batch.getBatchNo(),
                recipe == null ? "手工" : recipe.getRecipeCode(), dosing.getRecipeVersion(),
                request.getWaterVolumeLiters(), lines.size(), operatorName);
        return dosing;
    }

    // ------------------------------------------------------------------
    // 审批：批准（原子扣库存 + 台账）/ 驳回；审批人不得是提交人
    // ------------------------------------------------------------------

    @Transactional
    public NutrientDosing approve(Long dosingId) {
        NutrientDosing dosing = requirePending(dosingId);
        Long approverId = CurrentOperator.id();
        ensureDifferentPerson(approverId, dosing.getSubmittedBy(), dosing.getDosingNo());

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        WaterTank tank = tankService.requireTank(dosing.getTankId());
        List<NutrientDosingItem> lines = itemsOf(dosingId);

        // 审批时二次校验：效期以批准时刻为准（提交后过期的批号同样禁止出库）
        for (NutrientDosingItem line : lines) {
            materialService.requireUsableStock(line.getStockId(), today);
        }
        BigDecimal balance = tankService.currentBalance(tank.getId());
        BigDecimal after = balance.add(dosing.getWaterVolumeLiters());
        if (tank.getCapacityLiters() != null && after.compareTo(tank.getCapacityLiters()) > 0) {
            throw new IllegalStateException("补加后液位 " + after + "L 超过水箱容量 "
                    + tank.getCapacityLiters() + "L，不能批准");
        }

        // 原子扣减：条件更新保证余额充足，并发/超扣时 affected=0，整笔回滚
        for (NutrientDosingItem line : lines) {
            BigDecimal needKg = line.getDosedG().divide(GRAMS_PER_KG, 6, RoundingMode.HALF_UP);
            int rows = stockMapper.update(null, new UpdateWrapper<NutrientStock>()
                    .eq("id", line.getStockId())
                    .eq("status", NutrientStatus.STOCK_IN)
                    .ge("remaining_qty_kg", needKg)
                    .setSql("remaining_qty_kg = remaining_qty_kg - " + needKg.toPlainString()));
            if (rows == 0) {
                NutrientStock stock = stockMapper.selectById(line.getStockId());
                BigDecimal remain = stock == null ? BigDecimal.ZERO : stock.getRemainingQtyKg();
                throw new IllegalStateException("批号 " + line.getLotNo() + " 可用余额不足（需要 "
                        + needKg + "kg，剩余 " + remain + "kg），不能批准: " + dosing.getDosingNo());
            }
            NutrientStock updated = stockMapper.selectById(line.getStockId());
            if (updated.getRemainingQtyKg().signum() == 0) {
                updated.setStatus(NutrientStatus.STOCK_CONSUMED);
                stockMapper.updateById(updated);
            }
        }

        // 水箱台账（只追加）：补加正向
        TankLedger ledger = new TankLedger();
        ledger.setTankId(tank.getId());
        ledger.setChangeType(NutrientStatus.CHANGE_DOSING);
        ledger.setRefType(NutrientStatus.CHANGE_DOSING);
        ledger.setRefId(dosing.getId());
        ledger.setRefNo(dosing.getDosingNo());
        ledger.setVolumeChangeLiters(dosing.getWaterVolumeLiters());
        ledger.setBalanceVolumeLiters(after);
        ledger.setMeasuredEc(dosing.getMeasuredEc());
        ledger.setRecipeId(dosing.getRecipeId());
        ledger.setRecipeVersion(dosing.getRecipeVersion());
        ledger.setOperatorId(approverId);
        ledger.setOperatorName(operatorResolver.nameOf(approverId));
        ledger.setOccurredAt(now);
        ledgerMapper.insert(ledger);

        dosing.setStatus(NutrientStatus.ORDER_APPROVED);
        dosing.setApprovedAt(now);
        dosing.setApprovedBy(approverId);
        dosing.setApprovedName(operatorResolver.nameOf(approverId));
        dosing.setAppliedAt(now);
        dosingMapper.updateById(dosing);
        log.info("[营养液补加] 批准单#{} {}：扣减批号 {} 个，水箱 {} 余额 {}L，审批人={}",
                dosing.getId(), dosing.getDosingNo(), lines.size(), tank.getTankCode(), after,
                dosing.getApprovedName());
        return dosing;
    }

    @Transactional
    public NutrientDosing reject(Long dosingId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("驳回原因不能为空");
        }
        NutrientDosing dosing = requirePending(dosingId);
        Long approverId = CurrentOperator.id();
        ensureDifferentPerson(approverId, dosing.getSubmittedBy(), dosing.getDosingNo());
        LocalDateTime now = LocalDateTime.now();
        dosing.setStatus(NutrientStatus.ORDER_REJECTED);
        dosing.setApprovedAt(now);
        dosing.setApprovedBy(approverId);
        dosing.setApprovedName(operatorResolver.nameOf(approverId));
        dosing.setRejectReason(reason.trim());
        dosingMapper.updateById(dosing);
        log.info("[营养液补加] 驳回单#{} {}：原因={}，驳回人={}",
                dosing.getId(), dosing.getDosingNo(), reason, dosing.getApprovedName());
        return dosing;
    }

    // ------------------------------------------------------------------
    // 查询 / 追溯
    // ------------------------------------------------------------------

    public NutrientDosing getDosing(Long dosingId) {
        NutrientDosing dosing = dosingMapper.selectById(dosingId);
        if (dosing == null) {
            throw new IllegalArgumentException("补加单不存在: " + dosingId);
        }
        return dosing;
    }

    public List<NutrientDosing> list(Long tankId, Long batchId, String status) {
        return dosingMapper.selectList(new QueryWrapper<NutrientDosing>()
                .eq(tankId != null, "tank_id", tankId)
                .eq(batchId != null, "batch_id", batchId)
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }

    public List<NutrientDosingItem> itemsOf(Long dosingId) {
        return itemMapper.selectList(new QueryWrapper<NutrientDosingItem>()
                .eq("dosing_id", dosingId)
                .orderByAsc("tank_group")
                .orderByAsc("id"));
    }

    /** 待处理（审批只完成一半）：所有 PENDING 补加单。 */
    public List<NutrientDosing> pending() {
        return dosingMapper.selectList(new QueryWrapper<NutrientDosing>()
                .eq("status", NutrientStatus.ORDER_PENDING)
                .orderByAsc("submitted_at"));
    }

    /** 配方版本追溯：哪些补加单（及批次/水箱）引用了该配方版本。 */
    public List<NutrientDosing> listByRecipe(Long recipeId) {
        return dosingMapper.selectList(new QueryWrapper<NutrientDosing>()
                .eq("recipe_id", recipeId)
                .orderByDesc("id"));
    }

    // ------------------------------------------------------------------

    private NutrientDosing requirePending(Long dosingId) {
        NutrientDosing dosing = getDosing(dosingId);
        if (!NutrientStatus.ORDER_PENDING.equals(dosing.getStatus())) {
            throw new IllegalStateException("仅待处理补加单可审批，当前状态: " + dosing.getStatus()
                    + "（" + dosing.getDosingNo() + "）");
        }
        return dosing;
    }

    /** 两人原则：审批人不得是提交人（系统兜底账号 0 也不允许审批 bootstrap 的单，避免自助闭环）。 */
    static void ensureDifferentPerson(Long approverId, Long submitterId, String orderNo) {
        if (approverId == null || approverId.equals(submitterId)) {
            throw new IllegalStateException("提交人与审批人不能为同一人，请由其他负责人审批: " + orderNo);
        }
    }

    private NutrientDosing latestApprovedForBatch(Long batchId) {
        return dosingMapper.selectOne(new QueryWrapper<NutrientDosing>()
                .eq("batch_id", batchId)
                .eq("status", NutrientStatus.ORDER_APPROVED)
                .isNotNull("recipe_id")
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    private String stockMaterialCode(NutrientStock stock) {
        return materialService.requireMaterial(stock.getMaterialId()).getMaterialCode();
    }

    private String stockMaterialName(NutrientStock stock) {
        return materialService.requireMaterial(stock.getMaterialId()).getMaterialName();
    }
}
