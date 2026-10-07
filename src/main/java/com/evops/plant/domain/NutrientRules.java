package com.evops.plant.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养液纯业务规则（无数据库依赖，由单测锁定）：
 *  - 母液罐兼容性：钙系（硝酸钙）与磷硫系（磷酸二氢钾、硫酸镁等）同罐会生成
 *    磷酸钙/硫酸钙沉淀，二者必须分到不同母液罐；通用组（硝酸盐、螯合微肥）不限；
 *  - 批号效期：有效期当天仍可用，过期（today > expiry_date）一律不可用于补加；
 *  - 目标 EC/pH、母液罐编码（A/B/C）等基础口径。
 */
public final class NutrientRules {

    public static final String GROUP_CALCIUM = "CALCIUM";
    public static final String GROUP_PHOS_SULFUR = "PHOS_SULFUR";
    public static final String GROUP_NEUTRAL = "NEUTRAL";

    private NutrientRules() {
    }

    /** 配方明细槽位：label 用于报错定位（原料编码/名称）。 */
    public static final class Slot {
        private final String label;
        private final String compatGroup;
        private final String tankGroup;

        public Slot(String label, String compatGroup, String tankGroup) {
            this.label = label;
            this.compatGroup = compatGroup;
            this.tankGroup = tankGroup;
        }

        public String getLabel() {
            return label;
        }

        public String getCompatGroup() {
            return compatGroup;
        }

        public String getTankGroup() {
            return tankGroup;
        }
    }

    /** 规范化兼容组：空按 NEUTRAL；非已知取值直接拒绝（防止脏数据绕过沉淀校验）。 */
    public static String normalizeGroup(String compatGroup) {
        if (compatGroup == null || compatGroup.trim().isEmpty()) {
            return GROUP_NEUTRAL;
        }
        String g = compatGroup.trim().toUpperCase();
        if (!GROUP_CALCIUM.equals(g) && !GROUP_PHOS_SULFUR.equals(g) && !GROUP_NEUTRAL.equals(g)) {
            throw new IllegalArgumentException("未知原料兼容组: " + compatGroup
                    + "（允许 CALCIUM / PHOS_SULFUR / NEUTRAL）");
        }
        return g;
    }

    /** 规范化母液罐分组：仅允许 A/B/C。 */
    public static String normalizeTank(String tankGroup) {
        if (tankGroup == null || !tankGroup.trim().matches("[ABCabc]")) {
            throw new IllegalArgumentException("母液罐分组必须为 A / B / C: " + tankGroup);
        }
        return tankGroup.trim().toUpperCase();
    }

    /**
     * 配方发布前兼容性校验：同一母液罐内不允许同时出现钙系与磷硫系原料。
     * 返回按罐分组的结果供服务层复用（只读快照组装）。
     */
    public static Map<String, List<Slot>> checkCompatibility(List<Slot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("配方至少包含一种原料，无法校验兼容性");
        }
        Map<String, List<Slot>> byTank = new HashMap<>();
        for (Slot raw : slots) {
            Slot slot = new Slot(raw.label, normalizeGroup(raw.compatGroup), normalizeTank(raw.tankGroup));
            byTank.computeIfAbsent(slot.tankGroup, k -> new ArrayList<>()).add(slot);
        }
        for (Map.Entry<String, List<Slot>> entry : byTank.entrySet()) {
            boolean calcium = false;
            boolean phosSulfur = false;
            List<String> calciumLabels = new ArrayList<>();
            List<String> phosSulfurLabels = new ArrayList<>();
            for (Slot slot : entry.getValue()) {
                if (GROUP_CALCIUM.equals(slot.compatGroup)) {
                    calcium = true;
                    calciumLabels.add(slot.label);
                } else if (GROUP_PHOS_SULFUR.equals(slot.compatGroup)) {
                    phosSulfur = true;
                    phosSulfurLabels.add(slot.label);
                }
            }
            if (calcium && phosSulfur) {
                throw new IllegalArgumentException("母液罐 " + entry.getKey()
                        + " 存在不兼容原料：钙系 " + calciumLabels + " 与磷硫系 " + phosSulfurLabels
                        + " 同罐会产生沉淀，请拆分到不同母液罐");
            }
        }
        return byTank;
    }

    /** 批号是否已过期：有效期当天仍可用，today 晚于有效期即过期。 */
    public static boolean isExpired(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            throw new IllegalArgumentException("批号有效期不能为空");
        }
        return today.isAfter(expiryDate);
    }

    /** 目标电导值 EC（mS/cm）合理区间。 */
    public static void checkEc(BigDecimal ec) {
        if (ec == null || ec.signum() <= 0) {
            throw new IllegalArgumentException("目标电导值 EC 必须为正数");
        }
        if (ec.compareTo(new BigDecimal("10")) > 0) {
            throw new IllegalArgumentException("目标 EC 超出合理区间（0, 10] mS/cm: " + ec);
        }
    }

    public static void checkPh(BigDecimal ph) {
        if (ph == null) {
            return;
        }
        if (ph.signum() <= 0 || ph.compareTo(new BigDecimal("14")) > 0) {
            throw new IllegalArgumentException("目标 pH 必须在 (0, 14] 区间: " + ph);
        }
    }
}
