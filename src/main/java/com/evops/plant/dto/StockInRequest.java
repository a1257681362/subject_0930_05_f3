package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 原料按批号入库。有效期必填：过期批号任何补加都不得使用。
 * materialId 由路径 /nutrient-materials/{materialId}/stocks 提供并回填，不在此做 @NotNull。
 */
@Data
public class StockInRequest {
    private Long materialId;
    @NotBlank(message = "批号不能为空")
    private String lotNo;
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.001", message = "入库数量必须为正数")
    private BigDecimal inboundQtyKg;
    private LocalDate productionDate;
    @NotNull(message = "有效期不能为空")
    private LocalDate expiryDate;
    private String supplier;
}
