package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 元素兼容性规则：两元素共存浓度之和超过 max_sum_ppm 时禁止发布。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_incompatibility")
public class NutrientIncompatibility extends BaseEntity {
    private String elementA;
    private String elementB;
    private BigDecimal maxSumPpm;
    private String reason;
}
