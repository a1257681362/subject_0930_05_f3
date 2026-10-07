package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.MaterialRequest;
import com.evops.plant.dto.StockInRequest;
import com.evops.plant.entity.NutrientDosingItem;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientStock;
import com.evops.plant.service.NutrientMaterialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 营养液原料目录与批号库存（按批号入库，有效期管控；批号用途可追溯）。 */
@RestController
@RequestMapping("/api/pf/nutrient-materials")
public class NutrientMaterialController {

    private final NutrientMaterialService materialService;

    public NutrientMaterialController(NutrientMaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping
    public ApiResponse<NutrientMaterial> create(@Valid @RequestBody MaterialRequest request) {
        return ApiResponse.ok(materialService.createMaterial(request));
    }

    @GetMapping
    public ApiResponse<List<NutrientMaterial>> list() {
        return ApiResponse.ok(materialService.listMaterials());
    }

    @GetMapping("/{materialId}")
    public ApiResponse<NutrientMaterial> detail(@PathVariable Long materialId) {
        return ApiResponse.ok(materialService.requireMaterial(materialId));
    }

    /** 原料按批号入库（批号 + 有效期；已过期批号拒绝入库）。 */
    @PostMapping("/{materialId}/stocks")
    public ApiResponse<NutrientStock> stockIn(@PathVariable Long materialId,
                                              @Valid @RequestBody StockInRequest request) {
        request.setMaterialId(materialId);
        return ApiResponse.ok(materialService.stockIn(request));
    }

    /** 批号库存列表（可按原料/状态过滤；返回带 expired 只读标记）。 */
    @GetMapping("/stocks")
    public ApiResponse<List<NutrientStock>> stocks(@RequestParam(required = false) Long materialId,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(materialService.listStocks(materialId, status));
    }

    @GetMapping("/stocks/{stockId}")
    public ApiResponse<NutrientStock> stock(@PathVariable Long stockId) {
        return ApiResponse.ok(materialService.getStock(stockId));
    }

    /** 批号追溯：该批号用在了哪些补加单行。 */
    @GetMapping("/stocks/{stockId}/usage")
    public ApiResponse<List<NutrientDosingItem>> stockUsage(@PathVariable Long stockId) {
        return ApiResponse.ok(materialService.usageOfStock(stockId));
    }
}
