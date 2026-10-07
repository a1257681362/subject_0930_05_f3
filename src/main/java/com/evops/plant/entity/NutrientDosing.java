package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补加单（投加记账）。
 * 两步审批：PENDING 待处理 → APPROVED（批准：扣批号库存 + 记水箱台账）/ REJECTED（驳回）。
 * 提交不记账；钉配方版本（recipe_id/version_no），历史批次继续引用当时版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_dosing")
public class NutrientDosing extends BaseEntity {
    private String dosingNo;
    private Long batchId;
    private Long tankId;
    private Long recipeId;
    private Integer recipeVersion;
    /** 配液水量基数（升）：实投克数 = 每吨剂量 × 水量/1000 */
    private BigDecimal waterVolumeLiters;
    /** 实测电导值（浓度） */
    private BigDecimal measuredEc;
    private BigDecimal measuredPh;
    private String status;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submittedName;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private String approvedName;
    private LocalDateTime appliedAt;
    private String rejectReason;
    private String remark;
}
