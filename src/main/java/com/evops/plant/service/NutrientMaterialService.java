package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientRules;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.MaterialRequest;
import com.evops.plant.dto.StockInRequest;
import com.evops.plant.entity.NutrientDosingItem;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientStock;
import com.evops.plant.mapper.NutrientDosingItemMapper;
import com.evops.plant.mapper.NutrientMaterialMapper;
import com.evops.plant.mapper.NutrientStockMapper;
import com.evops.plant.support.OperatorResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 营养液原料与批号库存：
 *  - 原料按批号入库（批号 + 有效期），有效期必填，已过期批号禁止入库/使用；
 *  - 批号余额随补加单批准原子扣减（扣减在 NutrientDosingService 用条件更新完成）；
 *  - 批号用途追溯：按 stock 反查所有补加单行。
 */
@Service
public class NutrientMaterialService {

    private final NutrientMaterialMapper materialMapper;
    private final NutrientStockMapper stockMapper;
    private final NutrientDosingItemMapper dosingItemMapper;
    private final OperatorResolver operatorResolver;

    public NutrientMaterialService(NutrientMaterialMapper materialMapper, NutrientStockMapper stockMapper,
                                   NutrientDosingItemMapper dosingItemMapper,
                                   OperatorResolver operatorResolver) {
        this.materialMapper = materialMapper;
        this.stockMapper = stockMapper;
        this.dosingItemMapper = dosingItemMapper;
        this.operatorResolver = operatorResolver;
    }

    // ------------------------------------------------------------------
    // 原料目录
    // ------------------------------------------------------------------

    @Transactional
    public NutrientMaterial createMaterial(MaterialRequest request) {
        NutrientMaterial material = new NutrientMaterial();
        material.setMaterialCode(request.getMaterialCode());
        material.setMaterialName(request.getMaterialName());
        material.setNutrientRole(request.getNutrientRole().toUpperCase());
        material.setCompatGroup(NutrientRules.normalizeGroup(request.getCompatGroup()));
        if (request.getDefaultTank() != null) {
            material.setDefaultTank(NutrientRules.normalizeTank(request.getDefaultTank()));
        }
        material.setUnit(request.getUnit() == null ? "KG" : request.getUnit().trim().toUpperCase());
        material.setStatus(PfStatus.ACTIVE);
        material.setRemark(request.getRemark());
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("原料编码已存在: " + request.getMaterialCode());
        }
        return material;
    }

    public List<NutrientMaterial> listMaterials() {
        return materialMapper.selectList(new QueryWrapper<NutrientMaterial>()
                .orderByAsc("material_code"));
    }

    public NutrientMaterial requireMaterial(Long materialId) {
        NutrientMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new IllegalArgumentException("原料不存在: " + materialId);
        }
        return material;
    }

    // ------------------------------------------------------------------
    // 批号入库
    // ------------------------------------------------------------------

    @Transactional
    public NutrientStock stockIn(StockInRequest request) {
        NutrientMaterial material = requireMaterial(request.getMaterialId());
        if (!PfStatus.ACTIVE.equals(material.getStatus())) {
            throw new IllegalStateException("原料已停用，不能入库: " + material.getMaterialCode());
        }
        LocalDate today = LocalDate.now();
        if (request.getProductionDate() != null && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new IllegalArgumentException("有效期不能早于生产日期");
        }
        if (NutrientRules.isExpired(request.getExpiryDate(), today)) {
            throw new IllegalArgumentException("批号已过有效期，不能入库: " + request.getLotNo()
                    + "（有效期至 " + request.getExpiryDate() + "）");
        }
        if (request.getInboundQtyKg().signum() <= 0) {
            throw new IllegalArgumentException("入库数量必须为正数");
        }

        Long operatorId = CurrentOperator.id();
        NutrientStock stock = new NutrientStock();
        stock.setMaterialId(material.getId());
        stock.setLotNo(request.getLotNo());
        stock.setInboundQtyKg(request.getInboundQtyKg());
        stock.setRemainingQtyKg(request.getInboundQtyKg());
        stock.setProductionDate(request.getProductionDate());
        stock.setExpiryDate(request.getExpiryDate());
        stock.setSupplier(request.getSupplier());
        stock.setStatus(NutrientStatus.STOCK_IN);
        stock.setInboundBy(operatorId);
        stock.setInboundName(operatorResolver.nameOf(operatorId));
        stock.setInboundTime(LocalDateTime.now());
        try {
            stockMapper.insert(stock);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("该原料批号已存在: " + material.getMaterialCode() + "/" + request.getLotNo());
        }
        return stock;
    }

    public List<NutrientStock> listStocks(Long materialId, String status) {
        List<NutrientStock> stocks = stockMapper.selectList(new QueryWrapper<NutrientStock>()
                .eq(materialId != null, "material_id", materialId)
                .eq(status != null, "status", status)
                .orderByAsc("material_id")
                .orderByDesc("inbound_time"));
        stocks.forEach(this::fillExpired);
        return stocks;
    }

    public NutrientStock getStock(Long stockId) {
        NutrientStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new IllegalArgumentException("批号库存不存在: " + stockId);
        }
        return fillExpired(stock);
    }

    /** 批号用途追溯：该批号出现在哪些补加单行（含单号/审批状态由上层组装）。 */
    public List<NutrientDosingItem> usageOfStock(Long stockId) {
        getStock(stockId); // 存在性校验
        return dosingItemMapper.selectList(new QueryWrapper<NutrientDosingItem>()
                .eq("stock_id", stockId)
                .orderByDesc("id"));
    }

    /** 取可用于补加的批号：必须存在、在库、未过期；过期批号一律拦截。 */
    public NutrientStock requireUsableStock(Long stockId, LocalDate today) {
        NutrientStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new IllegalArgumentException("批号库存不存在: " + stockId);
        }
        if (NutrientStatus.STOCK_DISABLED.equals(stock.getStatus())) {
            throw new IllegalStateException("批号已停用，不能使用: " + stock.getLotNo());
        }
        if (NutrientRules.isExpired(stock.getExpiryDate(), today)) {
            throw new IllegalStateException("原料批号已过有效期，不能使用: " + stock.getLotNo()
                    + "（有效期至 " + stock.getExpiryDate() + "）");
        }
        return stock;
    }

    /** 填充只读过期标记，便于列表/详情直接识别（不写库、不改状态）。 */
    private NutrientStock fillExpired(NutrientStock stock) {
        stock.setExpired(NutrientRules.isExpired(stock.getExpiryDate(), LocalDate.now()));
        return stock;
    }
}
