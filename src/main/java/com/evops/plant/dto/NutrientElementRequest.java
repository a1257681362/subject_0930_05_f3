package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 微量元素明细：编码（FE/MN/ZN/...）+ 浓度 ppm；氮磷钾在配方主表，不在本明细。 */
@Data
public class NutrientElementRequest {
    @NotBlank(message = "微量元素编码不能为空")
    private String elementCode;
    @NotNull(message = "微量元素浓度不能为空")
    private BigDecimal concentrationPpm;
}
