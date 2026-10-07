package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.ApproveRequest;
import com.evops.plant.dto.NutrientRecipeRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.NutrientRecipe;
import com.evops.plant.entity.NutrientRecipeApproval;
import com.evops.plant.entity.NutrientRecipeElement;
import com.evops.plant.service.NutrientRecipeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 营养配方：氮磷钾+微量元素+目标电导值；版本只增不改。
 * 发布走两级审批，审批只完成一半时配方停留待处理；发布前校验兼容性；
 * 在田批次钉住播种时的版本，新发布不影响历史批次。
 */
@RestController
@RequestMapping("/api/pf/nutrient-recipes")
public class NutrientRecipeController {

    private final NutrientRecipeService recipeService;

    public NutrientRecipeController(NutrientRecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /** 创建草稿版本（版本号自动递增）。 */
    @PostMapping
    public ApiResponse<NutrientRecipe> create(@Valid @RequestBody NutrientRecipeRequest request) {
        return ApiResponse.ok(recipeService.create(request));
    }

    @GetMapping
    public ApiResponse<List<NutrientRecipe>> list(@RequestParam(required = false) Long varietyId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(recipeService.list(varietyId, status));
    }

    @GetMapping("/{recipeId}")
    public ApiResponse<NutrientRecipe> detail(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.requireRecipe(recipeId));
    }

    @GetMapping("/{recipeId}/elements")
    public ApiResponse<List<NutrientRecipeElement>> elements(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.elementsOf(recipeId));
    }

    /** 审批留痕：每级一行，可核对"审批只完成一半"的待处理状态。 */
    @GetMapping("/{recipeId}/approvals")
    public ApiResponse<List<NutrientRecipeApproval>> approvals(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.approvalsOf(recipeId));
    }

    /** 追溯：引用该配方版本的种植批次。 */
    @GetMapping("/{recipeId}/batches")
    public ApiResponse<List<Batch>> batches(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.batchesUsing(recipeId));
    }

    /** 提交审批：校验组成与兼容性，通过后进入待处理。 */
    @PostMapping("/{recipeId}/submit")
    public ApiResponse<NutrientRecipe> submit(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.submit(recipeId));
    }

    /** 完成一级审批：只完成一半时停留待处理，集齐两级才发布（旧发布版自动归档）。 */
    @PostMapping("/{recipeId}/approve")
    public ApiResponse<NutrientRecipe> approve(@PathVariable Long recipeId,
                                               @RequestBody(required = false) ApproveRequest request) {
        return ApiResponse.ok(recipeService.approve(recipeId, request == null ? null : request.getComment()));
    }
}
