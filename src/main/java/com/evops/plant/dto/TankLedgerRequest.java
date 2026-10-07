package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 水箱补加/排放登记：类型由接口路径决定（分别记账），操作人取当前登录主体。 */
@Data
public class TankLedgerRequest {
    @NotNull(message = "体积不能为空")
    private BigDecimal volumeL;
    /** 操作后实测电导值（mS/cm），可空 */
    private BigDecimal ecAfter;
    private String reason;
}
