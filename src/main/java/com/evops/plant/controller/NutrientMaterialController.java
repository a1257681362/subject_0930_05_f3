package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.NutrientLotRequest;
import com.evops.plant.dto.NutrientMaterialRequest;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientMaterial;
import com.evops.plant.entity.NutrientMaterialLot;
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

/** 营养液原料：档案建档、按批号入库（过期批号拒绝入库）、批号投加追溯。 */
@RestController
@RequestMapping("/api/pf/nutrient-materials")
public class NutrientMaterialController {

    private final NutrientMaterialService materialService;

    public NutrientMaterialController(NutrientMaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping
    public ApiResponse<NutrientMaterial> create(@Valid @RequestBody NutrientMaterialRequest request) {
        return ApiResponse.ok(materialService.createMaterial(request));
    }

    @GetMapping
    public ApiResponse<List<NutrientMaterial>> list() {
        return ApiResponse.ok(materialService.listMaterials());
    }

    /** 批号入库：批号全局唯一，已过期批号拒绝入库。 */
    @PostMapping("/{materialId}/lots")
    public ApiResponse<NutrientMaterialLot> addLot(@PathVariable Long materialId,
                                                   @Valid @RequestBody NutrientLotRequest request) {
        return ApiResponse.ok(materialService.addLot(materialId, request));
    }

    @GetMapping("/lots")
    public ApiResponse<List<NutrientMaterialLot>> lots(@RequestParam(required = false) Long materialId) {
        return ApiResponse.ok(materialService.listLots(materialId));
    }

    /** 批号追溯：该批号的全部投加记录（水箱/浓度/操作人快照）。 */
    @GetMapping("/lots/{lotId}/dosings")
    public ApiResponse<List<NutrientDosing>> lotDosings(@PathVariable Long lotId) {
        return ApiResponse.ok(materialService.dosingsOfLot(lotId));
    }
}
