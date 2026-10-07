package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 补加单行：批号级快照（物料/批号/有效期/母液罐/实投克数）。
 * 原料批号与配方版本由此双向可追溯：批号 → 用在哪些单，配方版本 → 投了哪些批号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_dosing_item")
public class NutrientDosingItem extends BaseEntity {
    private Long dosingId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long stockId;
    private String lotNo;
    private LocalDate expiryDate;
    private String tankGroup;
    private BigDecimal doseGPerTonne;
    private BigDecimal dosedG;
}
