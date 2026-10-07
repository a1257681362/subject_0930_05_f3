package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.DischargeRequest;
import com.evops.plant.dto.RejectRequest;
import com.evops.plant.entity.NutrientDischarge;
import com.evops.plant.service.NutrientDischargeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 营养液排放（与补加分别记账）：提交停留 PENDING，批准后记负向水箱台账。 */
@RestController
@RequestMapping("/api/pf/nutrient-discharges")
public class NutrientDischargeController {

    private final NutrientDischargeService dischargeService;

    public NutrientDischargeController(NutrientDischargeService dischargeService) {
        this.dischargeService = dischargeService;
    }

    @PostMapping
    public ApiResponse<NutrientDischarge> submit(@Valid @RequestBody DischargeRequest request) {
        return ApiResponse.ok(dischargeService.submit(request));
    }

    @GetMapping
    public ApiResponse<List<NutrientDischarge>> list(@RequestParam(required = false) Long tankId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(dischargeService.list(tankId, batchId, status));
    }

    /** 待处理排放单。 */
    @GetMapping("/pending")
    public ApiResponse<List<NutrientDischarge>> pending() {
        return ApiResponse.ok(dischargeService.pending());
    }

    @GetMapping("/{dischargeId}")
    public ApiResponse<NutrientDischarge> detail(@PathVariable Long dischargeId) {
        return ApiResponse.ok(dischargeService.getDischarge(dischargeId));
    }

    /** 批准：校验液位充足后记负向台账。需非提交人调用。 */
    @PostMapping("/{dischargeId}/approve")
    public ApiResponse<NutrientDischarge> approve(@PathVariable Long dischargeId) {
        return ApiResponse.ok(dischargeService.approve(dischargeId));
    }

    @PostMapping("/{dischargeId}/reject")
    public ApiResponse<NutrientDischarge> reject(@PathVariable Long dischargeId,
                                                 @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(dischargeService.reject(dischargeId, request.getReason()));
    }
}
