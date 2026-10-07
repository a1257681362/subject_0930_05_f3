package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 营养液水箱（隶属温室/分区）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_nutrient_tank")
public class NutrientTank extends BaseEntity {
    private String tankCode;
    private Long greenhouseId;
    private Long zoneId;
    private Integer capacityL;
    private String status;
}
