package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 原料批号库存：按批号入库，remaining_qty_kg 随补加单批准原子扣减。
 * status: IN_STOCK 在库 / CONSUMED 用尽 / DISABLED 停用（过期批号提交即拦截，不自动改状态，保留在库事实）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_stock")
public class NutrientStock extends BaseEntity {
    private Long materialId;
    private String lotNo;
    private BigDecimal inboundQtyKg;
    private BigDecimal remainingQtyKg;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String supplier;
    private String status;
    private Long inboundBy;
    private String inboundName;
    private LocalDateTime inboundTime;

    /** 只读瞬态：按当前日期判定是否已过期（不映射数据库列，过期批号提交即拦截）。 */
    @TableField(exist = false)
    private Boolean expired;
}
