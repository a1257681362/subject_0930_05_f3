package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水箱补加/排放台账（只追加，分别记账）：
 * txn_type: REPLENISH 补加 / DISCHARGE 排放，两条账互不冲抵。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_tank_ledger")
public class NutrientTankLedger extends BaseEntity {
    private Long tankId;
    private String txnType;
    private BigDecimal volumeL;
    /** 操作后实测电导值（mS/cm），可空 */
    private BigDecimal ecAfter;
    private String reason;
    private LocalDateTime txnAt;
    private Long operatorId;
    private String operatorName;
}
