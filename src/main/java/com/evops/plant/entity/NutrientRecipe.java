package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营养配方版本主表：氮磷钾+目标电导值在主表，微量元素在明细表；同一 recipe_code 逐版递增。
 * status: DRAFT 草稿 / PENDING_APPROVAL 待处理（审批中）/ PUBLISHED 已发布 / ARCHIVED 已归档
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_recipe")
public class NutrientRecipe extends BaseEntity {
    private String recipeCode;
    private Long varietyId;
    private Integer versionNo;
    /** 目标电导值（mS/cm） */
    private BigDecimal targetEc;
    private BigDecimal nitrogenPpm;
    private BigDecimal phosphorusPpm;
    private BigDecimal potassiumPpm;
    private String status;
    private String remark;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private LocalDateTime publishedAt;
    private Long publishedBy;
}
