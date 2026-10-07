package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 配方审批步骤留痕（只追加）：两级审批，每级一行；
 * 只完成一级时配方停留 PENDING_APPROVAL 待处理，集齐两级才发布。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_recipe_approval")
public class NutrientRecipeApproval extends BaseEntity {
    private Long recipeId;
    private Integer stepNo;
    private String stepName;
    private Long approverId;
    private String approverName;
    private String comment;
    private LocalDateTime approvedAt;
}
