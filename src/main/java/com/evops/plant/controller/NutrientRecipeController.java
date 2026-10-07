package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.NutrientRecipeRequest;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientRecipe;
import com.evops.plant.entity.NutrientRecipeItem;
import com.evops.plant.service.NutrientDosingService;
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

/** 营养配方：氮磷钾+微量元素+目标 EC；发布前母液罐兼容校验；版本只增不改。 */
@RestController
@RequestMapping("/api/pf/nutrient-recipes")
public class NutrientRecipeController {

    private final NutrientRecipeService recipeService;
    private final NutrientDosingService dosingService;

    public NutrientRecipeController(NutrientRecipeService recipeService,
                                    NutrientDosingService dosingService) {
        this.recipeService = recipeService;
        this.dosingService = dosingService;
    }

    /** 创建草稿版本（创建时即做兼容校验，版本号自动递增）。 */
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

    @GetMapping("/{recipeId}/items")
    public ApiResponse<List<NutrientRecipeItem>> items(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.itemsOf(recipeId));
    }

    /** 发布：发布前再次兼容校验；同编码旧发布版归档。 */
    @PostMapping("/{recipeId}/publish")
    public ApiResponse<NutrientRecipe> publish(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.publish(recipeId));
    }

    /** 配方版本追溯：哪些补加单引用了该版本。 */
    @GetMapping("/{recipeId}/dosings")
    public ApiResponse<List<NutrientDosing>> dosings(@PathVariable Long recipeId) {
        return ApiResponse.ok(dosingService.listByRecipe(recipeId));
    }
}
