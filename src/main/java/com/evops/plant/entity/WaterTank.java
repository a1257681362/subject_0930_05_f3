package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 营养液水箱（母液罐/贮液罐），台账余额不得超过容量。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_water_tank")
public class WaterTank extends BaseEntity {
    private String tankCode;
    private String tankName;
    private Long greenhouseId;
    private BigDecimal capacityLiters;
    private String status;
}
