package com.evops.plant.domain;

/** 植物营养液配方业务状态常量。 */
public final class NutrientStatus {
    private NutrientStatus() {
    }

    // 配方版本
    public static final String RECIPE_DRAFT = "DRAFT";
    /** 审批中（待处理）：两级审批未集齐前一直停留该状态 */
    public static final String RECIPE_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String RECIPE_PUBLISHED = "PUBLISHED";
    public static final String RECIPE_ARCHIVED = "ARCHIVED";

    // 原料批号
    public static final String LOT_IN_STOCK = "IN_STOCK";
    public static final String LOT_DEPLETED = "DEPLETED";

    // 水箱
    public static final String TANK_ACTIVE = "ACTIVE";
    public static final String TANK_DISABLED = "DISABLED";

    // 水箱台账（补加/排放分别记账）
    public static final String TXN_REPLENISH = "REPLENISH";
    public static final String TXN_DISCHARGE = "DISCHARGE";
}
