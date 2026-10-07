package com.evops.plant.domain;

import com.evops.plant.entity.NutrientIncompatibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营养配方参数校验（纯函数，不依赖 Spring/数据库）：
 * 目标电导值、氮磷钾浓度、微量元素编码与浓度、发布前元素兼容性、原料批号过期判定。
 */
public final class NutrientChecks {

    /** 氮磷钾为大量元素，固定在配方主表，微量元素明细不允许重复这三个编码。 */
    public static final Set<String> MACRO_ELEMENTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("N", "P", "K")));

    /** 允许登记的微量/中量元素编码。 */
    public static final Set<String> ALLOWED_TRACE_ELEMENTS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("FE", "MN", "ZN", "CU", "B", "MO", "CA", "MG", "S", "SI", "CL", "NA")));

    private static final BigDecimal EC_MIN = new BigDecimal("0.10");
    private static final BigDecimal EC_MAX = new BigDecimal("10.00");
    private static final BigDecimal MACRO_PPM_MAX = new BigDecimal("2000");
    private static final BigDecimal TRACE_PPM_MAX = new BigDecimal("1000");

    private NutrientChecks() {
    }

    /** 目标电导值（mS/cm）：水培常见区间 0.1–10。 */
    public static void checkTargetEc(BigDecimal targetEc) {
        if (targetEc == null || targetEc.compareTo(EC_MIN) < 0 || targetEc.compareTo(EC_MAX) > 0) {
            throw new IllegalArgumentException("目标电导值必须在 " + EC_MIN + " ~ " + EC_MAX + " mS/cm 之间: " + targetEc);
        }
    }

    /** 氮/磷/钾浓度（ppm）：允许为 0，上限 2000。 */
    public static void checkMacroPpm(String elementName, BigDecimal ppm) {
        if (ppm == null || ppm.compareTo(BigDecimal.ZERO) < 0 || ppm.compareTo(MACRO_PPM_MAX) > 0) {
            throw new IllegalArgumentException(elementName + "浓度必须在 0 ~ " + MACRO_PPM_MAX + " ppm 之间: " + ppm);
        }
    }

    /** 微量元素编码：大写归一；必须在允许集合内，且不允许占用氮磷钾（它们在主表）。 */
    public static String normalizeTraceElementCode(String elementCode) {
        String code = elementCode == null ? "" : elementCode.trim().toUpperCase();
        if (MACRO_ELEMENTS.contains(code)) {
            throw new IllegalArgumentException("氮磷钾(" + code + ")已在配方主表登记，不能作为微量元素明细");
        }
        if (!ALLOWED_TRACE_ELEMENTS.contains(code)) {
            throw new IllegalArgumentException("未知微量元素编码: " + elementCode
                    + "，允许值: " + ALLOWED_TRACE_ELEMENTS);
        }
        return code;
    }

    /** 原料主要提供元素编码：大写归一；氮磷钾与微量元素都允许（如磷酸二氢钾提供磷）。 */
    public static String normalizeKnownElementCode(String elementCode) {
        String code = elementCode == null ? "" : elementCode.trim().toUpperCase();
        if (!MACRO_ELEMENTS.contains(code) && !ALLOWED_TRACE_ELEMENTS.contains(code)) {
            throw new IllegalArgumentException("未知元素编码: " + elementCode);
        }
        return code;
    }

    /** 微量元素浓度（ppm）：必须大于 0，上限 1000。 */
    public static void checkTraceConcentration(BigDecimal concentrationPpm) {
        if (concentrationPpm == null || concentrationPpm.compareTo(BigDecimal.ZERO) <= 0
                || concentrationPpm.compareTo(TRACE_PPM_MAX) > 0) {
            throw new IllegalArgumentException("微量元素浓度必须在 0 ~ " + TRACE_PPM_MAX + " ppm 之间: " + concentrationPpm);
        }
    }

    /**
     * 发布前兼容性校验：任一规则涉及的两个元素都在配方中，且浓度之和超过上限即拒绝。
     * elementToPpm 为全量元素浓度（含主表 N/P/K 与微量元素明细）。
     */
    public static void checkCompatibility(Map<String, BigDecimal> elementToPpm,
                                           List<NutrientIncompatibility> rules) {
        for (NutrientIncompatibility rule : rules) {
            BigDecimal a = elementToPpm.get(rule.getElementA());
            BigDecimal b = elementToPpm.get(rule.getElementB());
            if (a == null || b == null) {
                continue; // 只含其中一种元素，不构成共存冲突
            }
            BigDecimal sum = a.add(b);
            if (sum.compareTo(rule.getMaxSumPpm()) > 0) {
                throw new IllegalStateException("配方兼容性校验不通过: " + rule.getElementA() + " + "
                        + rule.getElementB() + " 浓度合计 " + sum + " ppm，超过上限 "
                        + rule.getMaxSumPpm() + " ppm（" + rule.getReason() + "）");
            }
        }
    }

    /** 原料批号过期判定：有效期早于今天即过期，过期批号禁止投加。 */
    public static boolean isExpired(LocalDate expiryDate, LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }
}
