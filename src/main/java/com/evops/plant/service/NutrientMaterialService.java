package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.plant.domain.NutrientChecks;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.dto.NutrientLotRequest;
import com.evops.plant.dto.NutrientMaterialRequest;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientMaterialLot;
import com.evops.plant.mapper.NutrientDosingMapper;
import com.evops.plant.mapper.NutrientMaterialLotMapper;
import com.evops.plant.mapper.NutrientMaterialMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 营养液原料：档案建档、按批号入库、批号投加追溯。过期批号禁止入库与投加。 */
@Service
public class NutrientMaterialService {

    private final NutrientMaterialMapper materialMapper;
    private final NutrientMaterialLotMapper lotMapper;
    private final NutrientDosingMapper dosingMapper;

    public NutrientMaterialService(NutrientMaterialMapper materialMapper,
                                   NutrientMaterialLotMapper lotMapper,
                                   NutrientDosingMapper dosingMapper) {
        this.materialMapper = materialMapper;
        this.lotMapper = lotMapper;
        this.dosingMapper = dosingMapper;
    }

    /** 原料建档：编码唯一，主要提供元素必须是已知元素编码。 */
    @Transactional
    public NutrientMaterial createMaterial(NutrientMaterialRequest request) {
        NutrientMaterial material = new NutrientMaterial();
        material.setMaterialCode(request.getMaterialCode());
        material.setMaterialName(request.getMaterialName());
        material.setElementCode(NutrientChecks.normalizeKnownElementCode(request.getElementCode()));
        material.setUnit(request.getUnit());
        material.setStatus(NutrientStatus.TANK_ACTIVE);
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("原料编码已存在: " + request.getMaterialCode());
        }
        return material;
    }

    /** 批号入库：批号全局唯一；已过期批号直接拒绝入库（过期原料不能使用）。 */
    @Transactional
    public NutrientMaterialLot addLot(Long materialId, NutrientLotRequest request) {
        NutrientMaterial material = requireMaterial(materialId);
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        if (request.getProductionDate() != null
                && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new IllegalArgumentException("有效期不能早于生产日期");
        }
        if (NutrientChecks.isExpired(request.getExpiryDate(), LocalDate.now())) {
            throw new IllegalStateException("批号已过期，不能入库: " + request.getLotNo());
        }

        NutrientMaterialLot lot = new NutrientMaterialLot();
        lot.setLotNo(request.getLotNo());
        lot.setMaterialId(material.getId());
        lot.setQuantity(request.getQuantity());
        lot.setUnit(request.getUnit());
        lot.setSupplier(request.getSupplier());
        lot.setProductionDate(request.getProductionDate());
        lot.setExpiryDate(request.getExpiryDate());
        lot.setStatus(NutrientStatus.LOT_IN_STOCK);
        try {
            lotMapper.insert(lot);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：批号全局唯一，保证批号可追溯
            throw new IllegalStateException("批号已存在: " + request.getLotNo());
        }
        return lot;
    }

    public NutrientMaterial requireMaterial(Long materialId) {
        NutrientMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new IllegalArgumentException("原料不存在: " + materialId);
        }
        return material;
    }

    public NutrientMaterialLot requireLot(Long lotId) {
        NutrientMaterialLot lot = lotMapper.selectById(lotId);
        if (lot == null) {
            throw new IllegalArgumentException("原料批号不存在: " + lotId);
        }
        return lot;
    }

    public List<NutrientMaterial> listMaterials() {
        return materialMapper.selectList(new QueryWrapper<NutrientMaterial>().orderByAsc("material_code"));
    }

    public List<NutrientMaterialLot> listLots(Long materialId) {
        return lotMapper.selectList(new QueryWrapper<NutrientMaterialLot>()
                .eq(materialId != null, "material_id", materialId)
                .orderByAsc("expiry_date"));
    }

    /** 批号追溯：该批号的全部投加记录（水箱/浓度/操作人快照）。 */
    public List<NutrientDosing> dosingsOfLot(Long lotId) {
        requireLot(lotId);
        return dosingMapper.selectList(new QueryWrapper<NutrientDosing>()
                .eq("lot_id", lotId)
                .orderByDesc("dosed_at"));
    }
}
