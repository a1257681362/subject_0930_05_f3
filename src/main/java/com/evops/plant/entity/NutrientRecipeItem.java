package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 配方明细（版本不可变）：每种原料进哪个母液罐、每吨配液投多少克。
 * 物料编码/名称/兼容组冗余为快照，原料目录后续改名不影响历史版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_recipe_item")
public class NutrientRecipeItem extends BaseEntity {
    private Long recipeId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String nutrientRole;
    private String compatGroup;
    private String tankGroup;
    private BigDecimal doseGPerTonne;
}
