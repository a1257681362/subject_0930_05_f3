package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.DosingRequest;
import com.evops.plant.dto.RejectRequest;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientDosingItem;
import com.evops.plant.service.NutrientDosingService;
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
 * 营养液补加（投加记账）：提交停留 PENDING（不扣库存不记账），审批为独立第二步。
 * 审批端点由另一账号（approver）调用；本人审批本人会被拒绝。
 */
@RestController
@RequestMapping("/api/pf/nutrient-dosings")
public class NutrientDosingController {

    private final NutrientDosingService dosingService;

    public NutrientDosingController(NutrientDosingService dosingService) {
        this.dosingService = dosingService;
    }

    /** 提交补加（待处理；记录水箱、水量、实测 EC/pH 浓度、操作人与批号行）。 */
    @PostMapping
    public ApiResponse<NutrientDosing> submit(@Valid @RequestBody DosingRequest request) {
        return ApiResponse.ok(dosingService.submit(request));
    }

    @GetMapping
    public ApiResponse<List<NutrientDosing>> list(@RequestParam(required = false) Long tankId,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(dosingService.list(tankId, batchId, status));
    }

    /** 待处理补加单（审批只完成一半时系统停留在这里）。 */
    @GetMapping("/pending")
    public ApiResponse<List<NutrientDosing>> pending() {
        return ApiResponse.ok(dosingService.pending());
    }

    @GetMapping("/{dosingId}")
    public ApiResponse<NutrientDosing> detail(@PathVariable Long dosingId) {
        return ApiResponse.ok(dosingService.getDosing(dosingId));
    }

    @GetMapping("/{dosingId}/items")
    public ApiResponse<List<NutrientDosingItem>> items(@PathVariable Long dosingId) {
        return ApiResponse.ok(dosingService.itemsOf(dosingId));
    }

    /** 批准：二次校验批号效期 → 原子扣库存 → 写水箱台账。需非提交人调用。 */
    @PostMapping("/{dosingId}/approve")
    public ApiResponse<NutrientDosing> approve(@PathVariable Long dosingId) {
        return ApiResponse.ok(dosingService.approve(dosingId));
    }

    /** 驳回（原因必填）。需非提交人调用。 */
    @PostMapping("/{dosingId}/reject")
    public ApiResponse<NutrientDosing> reject(@PathVariable Long dosingId,
                                              @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(dosingService.reject(dosingId, request.getReason()));
    }
}
