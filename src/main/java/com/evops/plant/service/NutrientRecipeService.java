package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientRules;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.dto.NutrientRecipeItemRequest;
import com.evops.plant.dto.NutrientRecipeRequest;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientRecipe;
import com.evops.plant.entity.NutrientRecipeItem;
import com.evops.plant.entity.Variety;
import com.evops.plant.mapper.NutrientMaterialMapper;
import com.evops.plant.mapper.NutrientRecipeItemMapper;
import com.evops.plant.mapper.NutrientRecipeMapper;
import com.evops.plant.mapper.VarietyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 营养配方：氮磷钾盐类 + 微量元素 + 目标电导值 EC。
 * 版本只增不改（明细不可变）；发布前必须通过母液罐兼容性校验（钙系/磷硫系分罐）；
 * 发布时同 recipe_code 旧发布版自动归档；历史批次继续引用当时版本（钉版在补加服务处理）。
 */
@Service
public class NutrientRecipeService {

    private final NutrientRecipeMapper recipeMapper;
    private final NutrientRecipeItemMapper itemMapper;
    private final NutrientMaterialMapper materialMapper;
    private final VarietyMapper varietyMapper;

    public NutrientRecipeService(NutrientRecipeMapper recipeMapper, NutrientRecipeItemMapper itemMapper,
                                 NutrientMaterialMapper materialMapper, VarietyMapper varietyMapper) {
        this.recipeMapper = recipeMapper;
        this.itemMapper = itemMapper;
        this.materialMapper = materialMapper;
        this.varietyMapper = varietyMapper;
    }

    /** 创建草稿版本：版本号 = 该配方编码当前最大版本 + 1；创建时即做兼容性校验。 */
    @Transactional
    public NutrientRecipe create(NutrientRecipeRequest request) {
        Variety variety = varietyMapper.selectById(request.getVarietyId());
        if (variety == null) {
            throw new IllegalArgumentException("品种不存在: " + request.getVarietyId());
        }
        NutrientRules.checkEc(request.getTargetEc());
        NutrientRules.checkPh(request.getTargetPh());

        // 先把每行解析成原料快照并做兼容校验（同一原料不允许重复行）
        List<NutrientRecipeItem> snapshots = resolveItems(request.getItems());

        NutrientRecipe recipe = new NutrientRecipe();
        recipe.setRecipeCode(request.getRecipeCode());
        recipe.setVarietyId(variety.getId());
        recipe.setVersionNo(nextVersionNo(request.getRecipeCode()));
        recipe.setTargetEc(request.getTargetEc());
        recipe.setTargetPh(request.getTargetPh());
        recipe.setStatus(NutrientStatus.RECIPE_DRAFT);
        recipe.setRemark(request.getRemark());
        try {
            recipeMapper.insert(recipe);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("配方版本冲突，请重试: " + request.getRecipeCode());
        }
        for (NutrientRecipeItem item : snapshots) {
            item.setRecipeId(recipe.getId());
            itemMapper.insert(item);
        }
        return recipe;
    }

    /** 发布：发布前再次校验兼容性；同配方编码旧发布版归档。 */
    @Transactional
    public NutrientRecipe publish(Long recipeId) {
        NutrientRecipe recipe = requireRecipe(recipeId);
        if (!NutrientStatus.RECIPE_DRAFT.equals(recipe.getStatus())) {
            throw new IllegalStateException("仅草稿版本可发布，当前状态: " + recipe.getStatus());
        }
        List<NutrientRecipeItem> items = itemsOf(recipeId);
        if (items.isEmpty()) {
            throw new IllegalStateException("配方没有原料明细，不能发布: " + recipe.getRecipeCode());
        }
        // 发布前兼容校验（从不可变明细重读，双重保险）
        List<NutrientRules.Slot> slots = new ArrayList<>();
        for (NutrientRecipeItem item : items) {
            slots.add(new NutrientRules.Slot(item.getMaterialName(), item.getCompatGroup(), item.getTankGroup()));
        }
        NutrientRules.checkCompatibility(slots);

        NutrientRecipe current = publishedForCode(recipe.getRecipeCode());
        if (current != null) {
            current.setStatus(NutrientStatus.RECIPE_ARCHIVED);
            recipeMapper.updateById(current);
        }
        recipe.setStatus(NutrientStatus.RECIPE_PUBLISHED);
        recipe.setPublishedAt(LocalDateTime.now());
        recipe.setPublishedBy(CurrentOperator.id());
        recipeMapper.updateById(recipe);
        return recipe;
    }

    public NutrientRecipe requireRecipe(Long recipeId) {
        NutrientRecipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("营养配方不存在: " + recipeId);
        }
        return recipe;
    }

    public List<NutrientRecipe> list(Long varietyId, String status) {
        return recipeMapper.selectList(new QueryWrapper<NutrientRecipe>()
                .eq(varietyId != null, "variety_id", varietyId)
                .eq(status != null, "status", status)
                .orderByAsc("recipe_code")
                .orderByDesc("version_no"));
    }

    public List<NutrientRecipeItem> itemsOf(Long recipeId) {
        return itemMapper.selectList(new QueryWrapper<NutrientRecipeItem>()
                .eq("recipe_id", recipeId)
                .orderByAsc("tank_group")
                .orderByAsc("id"));
    }

    /** 品种当前已发布版本（无则 null）。 */
    public NutrientRecipe publishedForVariety(Long varietyId) {
        return recipeMapper.selectOne(new QueryWrapper<NutrientRecipe>()
                .eq("variety_id", varietyId)
                .eq("status", NutrientStatus.RECIPE_PUBLISHED)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
    }

    private NutrientRecipe publishedForCode(String recipeCode) {
        return recipeMapper.selectOne(new QueryWrapper<NutrientRecipe>()
                .eq("recipe_code", recipeCode)
                .eq("status", NutrientStatus.RECIPE_PUBLISHED)
                .last("LIMIT 1"));
    }

    private int nextVersionNo(String recipeCode) {
        NutrientRecipe latest = recipeMapper.selectOne(new QueryWrapper<NutrientRecipe>()
                .eq("recipe_code", recipeCode)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /** 解析请求行为不可变明细快照，并做：原料存在/启用、无重复、母液罐兼容校验。 */
    private List<NutrientRecipeItem> resolveItems(List<NutrientRecipeItemRequest> requests) {
        Set<Long> materialIds = new HashSet<>();
        List<NutrientRecipeItem> items = new ArrayList<>();
        List<NutrientRules.Slot> slots = new ArrayList<>();
        for (NutrientRecipeItemRequest req : requests) {
            if (!materialIds.add(req.getMaterialId())) {
                throw new IllegalArgumentException("同一原料在配方中只能出现一行: materialId=" + req.getMaterialId());
            }
            NutrientMaterial material = materialMapper.selectById(req.getMaterialId());
            if (material == null) {
                throw new IllegalArgumentException("原料不存在: " + req.getMaterialId());
            }
            if (!"ACTIVE".equals(material.getStatus())) {
                throw new IllegalStateException("原料已停用，不能进入配方: " + material.getMaterialCode());
            }
            if (req.getDoseGPerTonne().signum() <= 0) {
                throw new IllegalArgumentException("每吨剂量必须为正数: " + material.getMaterialCode());
            }
            String tank = NutrientRules.normalizeTank(req.getTankGroup());
            String group = NutrientRules.normalizeGroup(material.getCompatGroup());

            NutrientRecipeItem item = new NutrientRecipeItem();
            item.setMaterialId(material.getId());
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName());
            item.setNutrientRole(material.getNutrientRole());
            item.setCompatGroup(group);
            item.setTankGroup(tank);
            item.setDoseGPerTonne(req.getDoseGPerTonne());
            items.add(item);
            slots.add(new NutrientRules.Slot(material.getMaterialName(), group, tank));
        }
        // 发布前兼容性校验前置到创建时：草稿也不允许沉淀冲突，尽早暴露
        NutrientRules.checkCompatibility(slots);
        return items;
    }
}
