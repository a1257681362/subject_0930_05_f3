package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 原料批号（按批号入库）：lot_no 全局唯一；过期批号禁止投加。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_material_lot")
public class NutrientMaterialLot extends BaseEntity {
    private String lotNo;
    private Long materialId;
    private BigDecimal quantity;
    private String unit;
    private String supplier;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String status;
}
