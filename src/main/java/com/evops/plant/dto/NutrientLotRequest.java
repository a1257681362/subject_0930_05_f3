package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 原料批号入库：批号全局唯一，有效期必填（过期批号禁止投加）。 */
@Data
public class NutrientLotRequest {
    @NotBlank(message = "批号不能为空")
    private String lotNo;
    @NotNull(message = "入库数量不能为空")
    private BigDecimal quantity;
    @NotBlank(message = "计量单位不能为空")
    private String unit;
    private String supplier;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;
    @NotNull(message = "有效期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;
}
