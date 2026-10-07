package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 投加登记：水箱在路径上，批号过期会被拒绝；操作人取当前登录主体。 */
@Data
public class NutrientDosingRequest {
    @NotNull(message = "原料不能为空")
    private Long materialId;
    @NotNull(message = "原料批号不能为空")
    private Long lotId;
    /** 服务的种植批次（可空） */
    private Long batchId;
    @NotNull(message = "投加浓度不能为空")
    private BigDecimal concentrationPpm;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dosedAt;
}
