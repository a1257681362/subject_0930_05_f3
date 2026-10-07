package com.evops.plant.domain;

/** 植物营养液业务状态常量。 */
public final class NutrientStatus {
    private NutrientStatus() {
    }

    // 配方版本
    public static final String RECIPE_DRAFT = "DRAFT";
    public static final String RECIPE_PUBLISHED = "PUBLISHED";
    public static final String RECIPE_ARCHIVED = "ARCHIVED";

    // 批号库存
    public static final String STOCK_IN = "IN_STOCK";
    public static final String STOCK_CONSUMED = "CONSUMED";
    public static final String STOCK_DISABLED = "DISABLED";

    // 补加单 / 排放单（两步审批：提交只停留待处理）
    public static final String ORDER_PENDING = "PENDING";
    public static final String ORDER_APPROVED = "APPROVED";
    public static final String ORDER_REJECTED = "REJECTED";

    // 水箱台账变动类型
    public static final String CHANGE_DOSING = "DOSING";
    public static final String CHANGE_DISCHARGE = "DISCHARGE";
}
