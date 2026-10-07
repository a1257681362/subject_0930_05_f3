package com.evops.plant.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 补加单提交：挂水箱（可挂种植批次），按配方版本配 water_volume_liters 升母液。
 * 行内 stock_id 指定批号；dosed_g 可空（空=按配方每吨剂量 × 水量自动折算）。
 * 提交只停留 PENDING：不扣库存、不记台账，待审批（不得本人审批本人）。
 */
@Data
public class DosingRequest {
    @NotNull(message = "水箱不能为空")
    private Long tankId;
    private Long batchId;
    /** 配方版本；空表示手工配液（不钉配方），有批次时建议指定 */
    private Long recipeId;
    @NotNull(message = "配液水量不能为空")
    @DecimalMin(value = "0.01", message = "配液水量必须为正数")
    private BigDecimal waterVolumeLiters;
    /** 投加后实测电导值（浓度），可在投加现场补录 */
    private BigDecimal measuredEc;
    private BigDecimal measuredPh;
    private String remark;
    @NotEmpty(message = "补加至少包含一行原料批号")
    @Valid
    private List<DosingItemRequest> items;
}
