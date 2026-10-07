package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.NutrientDosingRequest;
import com.evops.plant.dto.TankLedgerRequest;
import com.evops.plant.entity.NutrientDosing;
import com.evops.plant.entity.NutrientTank;
import com.evops.plant.entity.NutrientTankLedger;
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

/** 水箱与投加：投加记录水箱/批号/浓度/操作人；补加与排放分别记账。 */
@RestController
@RequestMapping("/api/pf/nutrient-tanks")
public class NutrientTankController {

    private final NutrientDosingService dosingService;

    public NutrientTankController(NutrientDosingService dosingService) {
        this.dosingService = dosingService;
    }

    @GetMapping
    public ApiResponse<List<NutrientTank>> list() {
        return ApiResponse.ok(dosingService.listTanks());
    }

    /** 投加登记：批号过期或不在库会被拒绝。 */
    @PostMapping("/{tankId}/dosings")
    public ApiResponse<NutrientDosing> dose(@PathVariable Long tankId,
                                            @Valid @RequestBody NutrientDosingRequest request) {
        return ApiResponse.ok(dosingService.dose(tankId, request));
    }

    @GetMapping("/{tankId}/dosings")
    public ApiResponse<List<NutrientDosing>> dosings(@PathVariable Long tankId) {
        return ApiResponse.ok(dosingService.dosingsOfTank(tankId));
    }

    /** 补加记账（与排放分别记账）。 */
    @PostMapping("/{tankId}/replenish")
    public ApiResponse<NutrientTankLedger> replenish(@PathVariable Long tankId,
                                                     @Valid @RequestBody TankLedgerRequest request) {
        return ApiResponse.ok(dosingService.replenish(tankId, request));
    }

    /** 排放记账（与补加分别记账）。 */
    @PostMapping("/{tankId}/discharge")
    public ApiResponse<NutrientTankLedger> discharge(@PathVariable Long tankId,
                                                     @Valid @RequestBody TankLedgerRequest request) {
        return ApiResponse.ok(dosingService.discharge(tankId, request));
    }

    /** 台账查询：type=REPLENISH/DISCHARGE 分别查账，空=全部。 */
    @GetMapping("/{tankId}/ledger")
    public ApiResponse<List<NutrientTankLedger>> ledger(@PathVariable Long tankId,
                                                        @RequestParam(required = false) String type) {
        return ApiResponse.ok(dosingService.ledgerOfTank(tankId, type));
    }
}
