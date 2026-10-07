package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 营养液原料目录。
 * nutrient_role: N / P / K / NP / NK / PK / NPK / MG / MICRO 等营养角色
 * compat_group: CALCIUM 钙系 / PHOS_SULFUR 磷硫系 / NEUTRAL 通用（母液罐兼容校验用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_material")
public class NutrientMaterial extends BaseEntity {
    private String materialCode;
    private String materialName;
    private String nutrientRole;
    private String compatGroup;
    /** 默认母液罐分组 A/B/C */
    private String defaultTank;
    private String unit;
    private String status;
    private String remark;
}
