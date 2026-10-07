package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 原料建档。 */
@Data
public class NutrientMaterialRequest {
    @NotBlank(message = "原料编码不能为空")
    private String materialCode;
    @NotBlank(message = "原料名称不能为空")
    private String materialName;
    @NotBlank(message = "主要提供元素不能为空")
    private String elementCode;
    @NotBlank(message = "计量单位不能为空")
    private String unit;
}
