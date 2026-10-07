package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 配方明细行：原料 + 母液罐分组 + 每吨配液剂量（克/吨）。
 * 母液罐以本行选择为准（不强制等于原料默认罐），但仍须通过兼容校验。
 */
@Data
public class NutrientRecipeItemRequest {
    @NotNull(message = "配方行原料不能为空")
    private Long materialId;
    @NotBlank(message = "母液罐分组不能为空")
    private String tankGroup;
    @NotNull(message = "每吨剂量不能为空")
    @DecimalMin(value = "0.001", message = "每吨剂量必须为正数")
    private BigDecimal doseGPerTonne;
}
