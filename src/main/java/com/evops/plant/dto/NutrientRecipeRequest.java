package com.evops.plant.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 创建营养配方草稿（版本号自动递增）。
 * 配方 = 氮磷钾盐类 + 微量元素（items）+ 目标电导值 EC；发布前做母液罐兼容性校验。
 */
@Data
public class NutrientRecipeRequest {
    @NotBlank(message = "配方编码不能为空")
    private String recipeCode;
    @NotNull(message = "品种不能为空")
    private Long varietyId;
    @NotNull(message = "目标电导值 EC 不能为空")
    @DecimalMin(value = "0.01", message = "目标 EC 必须为正数")
    private BigDecimal targetEc;
    @DecimalMin(value = "0.01", message = "目标 pH 必须为正数")
    private BigDecimal targetPh;
    private String remark;
    @NotEmpty(message = "配方至少包含一种原料")
    @Valid
    private List<NutrientRecipeItemRequest> items;
}
