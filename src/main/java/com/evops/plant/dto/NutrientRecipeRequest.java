package com.evops.plant.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** 创建营养配方（草稿，版本号自动递增）：氮磷钾+微量元素+目标电导值；发布后只影响新播种批次。 */
@Data
public class NutrientRecipeRequest {
    @NotBlank(message = "配方编码不能为空")
    private String recipeCode;
    @NotNull(message = "品种不能为空")
    private Long varietyId;
    @NotNull(message = "目标电导值不能为空")
    private BigDecimal targetEc;
    @NotNull(message = "氮浓度不能为空")
    private BigDecimal nitrogenPpm;
    @NotNull(message = "磷浓度不能为空")
    private BigDecimal phosphorusPpm;
    @NotNull(message = "钾浓度不能为空")
    private BigDecimal potassiumPpm;
    private String remark;
    @NotEmpty(message = "配方至少包含一种微量元素")
    @Valid
    private List<NutrientElementRequest> traceElements;
}
