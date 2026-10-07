package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水箱台账（只追加）：补加正向、排放负向，余额逐笔滚算。
 * change_type: DOSING 补加 / DISCHARGE 排放；ref 指向来源单据，支撑对账与追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_tank_ledger")
public class TankLedger extends BaseEntity {
    private Long tankId;
    private String changeType;
    private String refType;
    private Long refId;
    private String refNo;
    private BigDecimal volumeChangeLiters;
    private BigDecimal balanceVolumeLiters;
    private BigDecimal measuredEc;
    private Long recipeId;
    private Integer recipeVersion;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime occurredAt;
}
