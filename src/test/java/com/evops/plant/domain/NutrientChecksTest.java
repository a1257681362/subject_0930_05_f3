package com.evops.plant.domain;

import com.evops.plant.entity.NutrientIncompatibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 营养配方参数校验：电导值/氮磷钾/微量元素、发布前兼容性、批号过期判定。 */
class NutrientChecksTest {

    private static NutrientIncompatibility rule(String a, String b, String maxSum) {
        NutrientIncompatibility rule = new NutrientIncompatibility();
        rule.setElementA(a);
        rule.setElementB(b);
        rule.setMaxSumPpm(new BigDecimal(maxSum));
        rule.setReason("测试规则");
        return rule;
    }

    @Test
    @DisplayName("目标电导值：区间内通过，越界拒绝")
    void targetEcRange() {
        assertDoesNotThrow(() -> NutrientChecks.checkTargetEc(new BigDecimal("1.80")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkTargetEc(new BigDecimal("0")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkTargetEc(new BigDecimal("10.01")));
    }

    @Test
    @DisplayName("氮磷钾浓度：允许 0，负数与超上限拒绝")
    void macroPpmRange() {
        assertDoesNotThrow(() -> NutrientChecks.checkMacroPpm("氮(N)", BigDecimal.ZERO));
        assertDoesNotThrow(() -> NutrientChecks.checkMacroPpm("钾(K)", new BigDecimal("220")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkMacroPpm("磷(P)", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkMacroPpm("氮(N)", new BigDecimal("2001")));
    }

    @Test
    @DisplayName("微量元素编码：合法通过；氮磷钾占用与未知编码拒绝；小写归一")
    void traceElementCode() {
        assertDoesNotThrow(() -> NutrientChecks.normalizeTraceElementCode("FE"));
        org.junit.jupiter.api.Assertions.assertEquals("FE", NutrientChecks.normalizeTraceElementCode("fe"));
        assertThrows(IllegalArgumentException.class, () -> NutrientChecks.normalizeTraceElementCode("P"));
        assertThrows(IllegalArgumentException.class, () -> NutrientChecks.normalizeTraceElementCode("XX"));
    }

    @Test
    @DisplayName("微量元素浓度：必须大于 0 且不超过上限")
    void traceConcentration() {
        assertDoesNotThrow(() -> NutrientChecks.checkTraceConcentration(new BigDecimal("2.000")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkTraceConcentration(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientChecks.checkTraceConcentration(new BigDecimal("1001")));
    }

    @Test
    @DisplayName("兼容性：两元素共存浓度超限拒绝，并带规则原因")
    void compatibilityViolation() {
        Map<String, BigDecimal> ppm = new HashMap<>();
        ppm.put("CA", new BigDecimal("400"));
        ppm.put("P", new BigDecimal("300")); // 合计 700 > 600
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> NutrientChecks.checkCompatibility(ppm,
                        Collections.singletonList(rule("CA", "P", "600"))));
        assertTrue(e.getMessage().contains("兼容性校验不通过"));
    }

    @Test
    @DisplayName("兼容性：只含其中一种元素、或未超上限，均通过")
    void compatibilityPass() {
        Map<String, BigDecimal> onlyOne = new HashMap<>();
        onlyOne.put("CA", new BigDecimal("400"));
        assertDoesNotThrow(() -> NutrientChecks.checkCompatibility(onlyOne,
                Collections.singletonList(rule("CA", "P", "600"))));

        Map<String, BigDecimal> underLimit = new HashMap<>();
        underLimit.put("CA", new BigDecimal("200"));
        underLimit.put("P", new BigDecimal("300")); // 合计 500 <= 600
        assertDoesNotThrow(() -> NutrientChecks.checkCompatibility(underLimit,
                Collections.singletonList(rule("CA", "P", "600"))));
    }

    @Test
    @DisplayName("批号过期判定：有效期早于今天即过期，当天仍可用")
    void lotExpiry() {
        LocalDate today = LocalDate.of(2026, 10, 7);
        assertTrue(NutrientChecks.isExpired(LocalDate.of(2026, 10, 6), today));
        assertFalse(NutrientChecks.isExpired(today, today));
        assertFalse(NutrientChecks.isExpired(LocalDate.of(2027, 1, 1), today));
    }
}
