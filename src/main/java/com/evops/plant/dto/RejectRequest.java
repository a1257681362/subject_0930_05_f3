package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 驳回补加/排放单时必填原因（批准与驳回是两种终态，留痕可追溯）。 */
@Data
public class RejectRequest {
    @NotBlank(message = "驳回原因不能为空")
    private String reason;
}
