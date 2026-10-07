package com.evops.plant.dto;

import lombok.Data;

/** 配方审批：每调用一次完成一级；两级集齐才发布，只完成一级时配方停留待处理。 */
@Data
public class ApproveRequest {
    private String comment;
}
