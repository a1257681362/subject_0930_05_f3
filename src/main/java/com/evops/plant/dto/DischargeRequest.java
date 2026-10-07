package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 排放单提交：与补加分别记账；提交只停留 PENDING，批准后记负向台账。 */
@Data
public class DischargeRequest {
    @NotNull(message = "水箱不能为空")
    private Long tankId;
    private Long batchId;
    @NotNull(message = "排放体积不能为空")
    @DecimalMin(value = "0.01", message = "排放体积必须为正数")
    private BigDecimal volumeLiters;
    private BigDecimal measuredEc;
    @NotBlank(message = "排放原因不能为空")
    private String reason;
}
