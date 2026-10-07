package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 水箱建档。 */
@Data
public class TankRequest {
    @NotBlank(message = "水箱编码不能为空")
    private String tankCode;
    @NotBlank(message = "水箱名称不能为空")
    private String tankName;
    private Long greenhouseId;
    @NotNull(message = "水箱容量不能为空")
    @DecimalMin(value = "0.01", message = "水箱容量必须为正数")
    private BigDecimal capacityLiters;
}
