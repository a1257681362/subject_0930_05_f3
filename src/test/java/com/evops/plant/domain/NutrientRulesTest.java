package com.evops.plant.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 母液罐兼容性与批号效期规则：钙系/磷硫系同罐沉淀冲突；有效期当天仍可用。 */
class NutrientRulesTest {

    private static NutrientRules.Slot slot(String label, String group, String tank) {
        return new NutrientRules.Slot(label, group, tank);
    }

    @Test
    @DisplayName("钙系与磷硫系分罐（A/B）通过，通用组不限")
    void compatibleWhenSplitAcrossTanks() {
        assertDoesNotThrow(() -> NutrientRules.checkCompatibility(Arrays.asList(
                slot("硝酸钙", "CALCIUM", "A"),
                slot("硝酸钾", "NEUTRAL", "A"),
                slot("磷酸二氢钾", "PHOS_SULFUR", "B"),
                slot("硫酸镁", "PHOS_SULFUR", "B"),
                slot("螯合微肥", "NEUTRAL", "B"))));
    }

    @Test
    @DisplayName("钙系与磷硫系同罐拒绝（会沉淀）")
    void rejectCalciumAndPhosSulfurInSameTank() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkCompatibility(Arrays.asList(
                        slot("硝酸钙", "CALCIUM", "A"),
                        slot("硫酸镁", "PHOS_SULFUR", "A"))));
        assertTrue(e.getMessage().contains("母液罐 A"));
        assertTrue(e.getMessage().contains("沉淀"));
    }

    @Test
    @DisplayName("仅钙系同罐 / 仅磷硫系同罐 / 仅通用组都合法")
    void singleGroupTanksAreFine() {
        assertDoesNotThrow(() -> NutrientRules.checkCompatibility(
                Collections.singletonList(slot("硝酸钙", "CALCIUM", "A"))));
        assertDoesNotThrow(() -> NutrientRules.checkCompatibility(Arrays.asList(
                slot("磷酸二氢钾", "PHOS_SULFUR", "B"),
                slot("硫酸镁", "PHOS_SULFUR", "B"))));
        assertDoesNotThrow(() -> NutrientRules.checkCompatibility(
                Collections.singletonList(slot("螯合微肥", null, "C"))));
    }

    @Test
    @DisplayName("未知兼容组或非法母液罐拒绝")
    void rejectUnknownGroupOrTank() {
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkCompatibility(
                        Collections.singletonList(slot("神秘盐", "UNKNOWN", "A"))));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkCompatibility(
                        Collections.singletonList(slot("硝酸钙", "CALCIUM", "D"))));
    }

    @Test
    @DisplayName("空配方不能通过兼容校验")
    void emptyRecipeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkCompatibility(Collections.emptyList()));
    }

    @Test
    @DisplayName("效期：有效期当天仍可用，次日起过期")
    void expiryBoundary() {
        LocalDate expiry = LocalDate.of(2026, 10, 7);
        assertFalse(NutrientRules.isExpired(expiry, expiry), "有效期当天不应判定过期");
        assertTrue(NutrientRules.isExpired(expiry, expiry.plusDays(1)), "有效期次日应判定过期");
        assertFalse(NutrientRules.isExpired(expiry, expiry.minusDays(1)), "到期前不应判定过期");
    }

    @Test
    @DisplayName("EC/pH 合理区间")
    void ecAndPhRanges() {
        assertDoesNotThrow(() -> NutrientRules.checkEc(new java.math.BigDecimal("1.80")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkEc(new java.math.BigDecimal("12")));
        assertDoesNotThrow(() -> NutrientRules.checkPh(new java.math.BigDecimal("6.00")));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRules.checkPh(new java.math.BigDecimal("15")));
    }
}
