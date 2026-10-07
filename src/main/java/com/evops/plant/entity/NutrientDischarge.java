package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排放单（与补加分别记账）。
 * 两步审批：PENDING 待处理 → APPROVED（批准：记负向水箱台账）/ REJECTED（驳回）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_discharge")
public class NutrientDischarge extends BaseEntity {
    private String dischargeNo;
    private Long tankId;
    private Long batchId;
    private BigDecimal volumeLiters;
    private BigDecimal measuredEc;
    private String reason;
    private String status;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submittedName;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private String approvedName;
    private LocalDateTime dischargedAt;
    private String rejectReason;
}
