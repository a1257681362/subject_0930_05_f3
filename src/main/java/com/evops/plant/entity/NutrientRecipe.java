package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营养配方版本主表：氮磷钾盐类 + 微量元素 + 目标电导值 EC（/pH）。
 * 同一 recipe_code 逐版递增；status: DRAFT / PUBLISHED / ARCHIVED，任一时间至多一个 PUBLISHED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_recipe")
public class NutrientRecipe extends BaseEntity {
    private String recipeCode;
    private Long varietyId;
    private Integer versionNo;
    private BigDecimal targetEc;
    private BigDecimal targetPh;
    private String status;
    private String remark;
    private LocalDateTime publishedAt;
    private Long publishedBy;
}
