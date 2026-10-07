package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投加记录（只追加）：水箱+原料批号+浓度+操作人；
 * 批号与原料名快照冗余，原料档案后续改名不影响历史留痕，批号全程可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_dosing")
public class NutrientDosing extends BaseEntity {
    private Long tankId;
    /** 服务的种植批次（可空：水箱共用时不指定） */
    private Long batchId;
    private Long materialId;
    private String materialName;
    private Long lotId;
    private String lotNo;
    private BigDecimal concentrationPpm;
    private LocalDateTime dosedAt;
    private Long operatorId;
    private String operatorName;
}
