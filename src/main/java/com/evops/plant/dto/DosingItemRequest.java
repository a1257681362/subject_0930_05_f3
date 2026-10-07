package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 补加单行：指定批号库存；dosed_g 空则按配方剂量×水量折算。 */
@Data
public class DosingItemRequest {
    @NotNull(message = "批号库存不能为空")
    private Long stockId;
    @DecimalMin(value = "0.001", message = "实投克数必须为正数")
    private BigDecimal dosedG;
}
