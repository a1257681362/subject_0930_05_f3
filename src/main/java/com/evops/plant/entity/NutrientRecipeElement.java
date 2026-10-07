package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 营养配方微量元素明细：氮磷钾在主表，本表只放微量/中量元素。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_recipe_element")
public class NutrientRecipeElement extends BaseEntity {
    private Long recipeId;
    private String elementCode;
    private BigDecimal concentrationPpm;
}
