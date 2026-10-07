package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 营养液原料档案：element_code 为该原料主要提供的元素。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_material")
public class NutrientMaterial extends BaseEntity {
    private String materialCode;
    private String materialName;
    private String elementCode;
    private String unit;
    private String status;
}
