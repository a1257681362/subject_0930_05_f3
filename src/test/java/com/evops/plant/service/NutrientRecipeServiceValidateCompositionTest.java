package com.evops.plant.service;

import com.evops.plant.dto.NutrientElementRequest;
import com.evops.plant.dto.NutrientRecipeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配方组成校验：目标电导值+氮磷钾+微量元素；微量元素不允许重复、不允许占用氮磷钾。 */
class NutrientRecipeServiceValidateCompositionTest {

    private static NutrientElementRequest trace(String code, String ppm) {
        NutrientElementRequest e = new NutrientElementRequest();
        e.setElementCode(code);
        e.setConcentrationPpm(new BigDecimal(ppm));
        return e;
    }

    private static NutrientRecipeRequest recipe(NutrientElementRequest... traces) {
        NutrientRecipeRequest request = new NutrientRecipeRequest();
        request.setRecipeCode("NR-TEST");
        request.setVarietyId(1L);
        request.setTargetEc(new BigDecimal("1.80"));
        request.setNitrogenPpm(new BigDecimal("180"));
        request.setPhosphorusPpm(new BigDecimal("50"));
        request.setPotassiumPpm(new BigDecimal("220"));
        request.setTraceElements(Arrays.asList(traces));
        return request;
    }

    @Test
    @DisplayName("合法配方通过")
    void validComposition() {
        assertDoesNotThrow(() -> NutrientRecipeService.validateComposition(
                recipe(trace("FE", "2.0"), trace("MN", "0.5"))));
    }

    @Test
    @DisplayName("微量元素重复登记拒绝")
    void duplicateTraceRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> NutrientRecipeService.validateComposition(
                        recipe(trace("FE", "2.0"), trace("fe", "3.0"))));
        assertTrue(e.getMessage().contains("重复"));
    }

    @Test
    @DisplayName("微量元素占用氮磷钾拒绝")
    void macroAsTraceRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRecipeService.validateComposition(recipe(trace("K", "100"))));
    }

    @Test
    @DisplayName("目标电导值越界拒绝")
    void targetEcOutOfRange() {
        NutrientRecipeRequest request = recipe(trace("FE", "2.0"));
        request.setTargetEc(new BigDecimal("20"));
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRecipeService.validateComposition(request));
    }

    @Test
    @DisplayName("空微量元素明细拒绝（配方至少包含一种微量元素）")
    void emptyTracesRejected() {
        NutrientRecipeRequest request = recipe(trace("FE", "2.0"));
        request.setTraceElements(Collections.emptyList());
        assertThrows(IllegalArgumentException.class,
                () -> NutrientRecipeService.validateComposition(request));
    }
}
