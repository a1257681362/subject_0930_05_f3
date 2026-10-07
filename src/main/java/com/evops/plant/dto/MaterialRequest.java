package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 原料建档。compat_group 留空时默认 NEUTRAL；CALCIUM/PHOS_SULFUR 必须显式指定。 */
@Data
public class MaterialRequest {
    @NotBlank(message = "原料编码不能为空")
    private String materialCode;
    @NotBlank(message = "原料名称不能为空")
    private String materialName;
    @NotBlank(message = "营养角色不能为空")
    private String nutrientRole;
    /** CALCIUM / PHOS_SULFUR / NEUTRAL，空按 NEUTRAL */
    private String compatGroup;
    /** 默认母液罐 A/B/C */
    private String defaultTank;
    private String unit;
    private String remark;
}
