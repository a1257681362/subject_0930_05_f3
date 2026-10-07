package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.NutrientChecks;
import com.evops.plant.domain.NutrientStatus;
import com.evops.plant.dto.NutrientElementRequest;
import com.evops.plant.dto.NutrientRecipeRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.NutrientIncompatibility;
import com.evops.plant.entity.NutrientRecipe;
import com.evops.plant.entity.NutrientRecipeApproval;
import com.evops.plant.entity.NutrientRecipeElement;
import com.evops.plant.entity.Variety;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.NutrientIncompatibilityMapper;
import com.evops.plant.mapper.NutrientRecipeApprovalMapper;
import com.evops.plant.mapper.NutrientRecipeElementMapper;
import com.evops.plant.mapper.NutrientRecipeMapper;
import com.evops.plant.mapper.VarietyMapper;
import com.evops.plant.support.OperatorResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营养配方：氮磷钾+微量元素+目标电导值；版本只增不改。
 * 发布走两级审批：提交后进入 PENDING_APPROVAL（待处理），每级审批留痕；
 * 审批只完成一半时配方一直停留待处理，集齐两级才发布并归档同编码旧发布版。
 * 发布前校验元素兼容性；在田批次钉住播种时的版本，不受新发布影响。
 */
@Service
public class NutrientRecipeService {

    /** 发布所需审批级数：1=农艺审核，2=生产批准。 */
    public static final int REQUIRED_APPROVAL_STEPS = 2;
    private static final String[] STEP_NAMES = {"农艺审核", "生产批准"};

    private final NutrientRecipeMapper recipeMapper;
    private final NutrientRecipeElementMapper elementMapper;
    private final NutrientRecipeApprovalMapper approvalMapper;
    private final NutrientIncompatibilityMapper incompatibilityMapper;
    private final VarietyMapper varietyMapper;
    private final BatchMapper batchMapper;
    private final OperatorResolver operatorResolver;

    public NutrientRecipeService(NutrientRecipeMapper recipeMapper,
                                 NutrientRecipeElementMapper elementMapper,
                                 NutrientRecipeApprovalMapper approvalMapper,
                                 NutrientIncompatibilityMapper incompatibilityMapper,
                                 VarietyMapper varietyMapper, BatchMapper batchMapper,
                                 OperatorResolver operatorResolver) {
        this.recipeMapper = recipeMapper;
        this.elementMapper = elementMapper;
        this.approvalMapper = approvalMapper;
        this.incompatibilityMapper = incompatibilityMapper;
        this.varietyMapper = varietyMapper;
        this.batchMapper = batchMapper;
        this.operatorResolver = operatorResolver;
    }

    /** 创建草稿版本：版本号 = 该配方编码当前最大版本 + 1。 */
    @Transactional
    public NutrientRecipe create(NutrientRecipeRequest request) {
        Variety variety = varietyMapper.selectById(request.getVarietyId());
        if (variety == null) {
            throw new IllegalArgumentException("品种不存在: " + request.getVarietyId());
        }
        validateComposition(request);

        NutrientRecipe recipe = new NutrientRecipe();
        recipe.setRecipeCode(request.getRecipeCode());
        recipe.setVarietyId(variety.getId());
        recipe.setVersionNo(nextVersionNo(request.getRecipeCode()));
        recipe.setTargetEc(request.getTargetEc());
        recipe.setNitrogenPpm(request.getNitrogenPpm());
        recipe.setPhosphorusPpm(request.getPhosphorusPpm());
        recipe.setPotassiumPpm(request.getPotassiumPpm());
        recipe.setStatus(NutrientStatus.RECIPE_DRAFT);
        recipe.setRemark(request.getRemark());
        try {
            recipeMapper.insert(recipe);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：并发创建同版本时只允许一个成功
            throw new IllegalStateException("配方版本冲突，请重试: " + request.getRecipeCode());
        }
        insertElements(recipe.getId(), request.getTraceElements());
        return recipe;
    }

    /** 提交审批：校验配方组成与元素兼容性，通过后进入待处理（PENDING_APPROVAL）。 */
    @Transactional
    public NutrientRecipe submit(Long recipeId) {
        NutrientRecipe recipe = requireRecipe(recipeId);
        if (!NutrientStatus.RECIPE_DRAFT.equals(recipe.getStatus())) {
            throw new IllegalStateException("仅草稿版本可提交审批，当前状态: " + recipe.getStatus());
        }
        checkCompatibilityOf(recipe);
        recipe.setStatus(NutrientStatus.RECIPE_PENDING_APPROVAL);
        recipe.setSubmittedAt(LocalDateTime.now());
        recipe.setSubmittedBy(CurrentOperator.id());
        recipeMapper.updateById(recipe);
        return recipe;
    }

    /**
     * 完成一级审批：每级留痕（只追加）。只完成一半（1/2）时配方停留 PENDING_APPROVAL 待处理；
     * 集齐两级后重新校验兼容性（规则可能在审批期间调整），通过才发布并归档同编码旧发布版。
     */
    @Transactional
    public NutrientRecipe approve(Long recipeId, String comment) {
        NutrientRecipe recipe = requireRecipe(recipeId);
        if (!NutrientStatus.RECIPE_PENDING_APPROVAL.equals(recipe.getStatus())) {
            throw new IllegalStateException("仅待处理（审批中）的配方可审批，当前状态: " + recipe.getStatus());
        }
        int stepNo = approvalsOf(recipeId).size() + 1;
        if (stepNo > REQUIRED_APPROVAL_STEPS) {
            throw new IllegalStateException("审批级数已集齐，配方不应停留在待处理: " + recipe.getRecipeCode());
        }

        Long operatorId = CurrentOperator.id();
        NutrientRecipeApproval approval = new NutrientRecipeApproval();
        approval.setRecipeId(recipeId);
        approval.setStepNo(stepNo);
        approval.setStepName(STEP_NAMES[stepNo - 1]);
        approval.setApproverId(operatorId);
        approval.setApproverName(operatorResolver.nameOf(operatorId));
        approval.setComment(comment);
        approval.setApprovedAt(LocalDateTime.now());
        try {
            approvalMapper.insert(approval);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：并发审批同一级时只允许一个成功
            throw new IllegalStateException("审批步骤冲突，请重试: 第 " + stepNo + " 级");
        }

        if (stepNo < REQUIRED_APPROVAL_STEPS) {
            // 审批只完成一半：停留待处理，不发布
            return recipe;
        }

        checkCompatibilityOf(recipe);
        NutrientRecipe current = publishedForCode(recipe.getRecipeCode());
        if (current != null) {
            current.setStatus(NutrientStatus.RECIPE_ARCHIVED);
            recipeMapper.updateById(current);
        }
        recipe.setStatus(NutrientStatus.RECIPE_PUBLISHED);
        recipe.setPublishedAt(LocalDateTime.now());
        recipe.setPublishedBy(operatorId);
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

    public List<NutrientRecipeElement> elementsOf(Long recipeId) {
        return elementMapper.selectList(new QueryWrapper<NutrientRecipeElement>()
                .eq("recipe_id", recipeId)
                .orderByAsc("element_code"));
    }

    public List<NutrientRecipeApproval> approvalsOf(Long recipeId) {
        return approvalMapper.selectList(new QueryWrapper<NutrientRecipeApproval>()
                .eq("recipe_id", recipeId)
                .orderByAsc("step_no"));
    }

    /** 追溯：引用该配方版本的种植批次（历史批次钉版，不随新发布漂移）。 */
    public List<Batch> batchesUsing(Long recipeId) {
        requireRecipe(recipeId);
        return batchMapper.selectList(new QueryWrapper<Batch>()
                .eq("nutrient_recipe_id", recipeId)
                .orderByDesc("id"));
    }

    /** 品种当前已发布版本（无则 null）；播种时用于给批次钉版。 */
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

    private void insertElements(Long recipeId, List<NutrientElementRequest> elements) {
        for (NutrientElementRequest e : elements) {
            NutrientRecipeElement element = new NutrientRecipeElement();
            element.setRecipeId(recipeId);
            element.setElementCode(NutrientChecks.normalizeTraceElementCode(e.getElementCode()));
            element.setConcentrationPpm(e.getConcentrationPpm());
            elementMapper.insert(element);
        }
    }

    /** 发布前兼容性校验：主表氮磷钾 + 微量元素明细合成全量浓度表，对照兼容规则。 */
    private void checkCompatibilityOf(NutrientRecipe recipe) {
        Map<String, BigDecimal> elementToPpm = new HashMap<>();
        elementToPpm.put("N", recipe.getNitrogenPpm());
        elementToPpm.put("P", recipe.getPhosphorusPpm());
        elementToPpm.put("K", recipe.getPotassiumPpm());
        for (NutrientRecipeElement element : elementsOf(recipe.getId())) {
            elementToPpm.put(element.getElementCode(), element.getConcentrationPpm());
        }
        List<NutrientIncompatibility> rules = incompatibilityMapper.selectList(null);
        NutrientChecks.checkCompatibility(elementToPpm, rules);
    }

    /** 组成校验：目标电导值、氮磷钾浓度、微量元素编码/浓度、元素不重复。 */
    static void validateComposition(NutrientRecipeRequest request) {
        NutrientChecks.checkTargetEc(request.getTargetEc());
        NutrientChecks.checkMacroPpm("氮(N)", request.getNitrogenPpm());
        NutrientChecks.checkMacroPpm("磷(P)", request.getPhosphorusPpm());
        NutrientChecks.checkMacroPpm("钾(K)", request.getPotassiumPpm());
        if (request.getTraceElements() == null || request.getTraceElements().isEmpty()) {
            throw new IllegalArgumentException("配方至少包含一种微量元素");
        }
        Set<String> seen = new HashSet<>();
        for (NutrientElementRequest e : request.getTraceElements()) {
            String code = NutrientChecks.normalizeTraceElementCode(e.getElementCode());
            NutrientChecks.checkTraceConcentration(e.getConcentrationPpm());
            if (!seen.add(code)) {
                throw new IllegalArgumentException("微量元素重复登记: " + code);
            }
        }
    }
}
