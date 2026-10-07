package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.TankRequest;
import com.evops.plant.entity.TankLedger;
import com.evops.plant.entity.WaterTank;
import com.evops.plant.service.WaterTankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 营养液水箱：建档、当前液位、台账（补加/排放逐笔滚算）。 */
@RestController
@RequestMapping("/api/pf/water-tanks")
public class WaterTankController {

    private final WaterTankService tankService;

    public WaterTankController(WaterTankService tankService) {
        this.tankService = tankService;
    }

    @PostMapping
    public ApiResponse<WaterTank> create(@Valid @RequestBody TankRequest request) {
        return ApiResponse.ok(tankService.create(request));
    }

    @GetMapping
    public ApiResponse<List<WaterTank>> list() {
        return ApiResponse.ok(tankService.list());
    }

    @GetMapping("/{tankId}")
    public ApiResponse<WaterTank> detail(@PathVariable Long tankId) {
        return ApiResponse.ok(tankService.requireTank(tankId));
    }

    /** 当前液位（升）。 */
    @GetMapping("/{tankId}/balance")
    public ApiResponse<Map<String, Object>> balance(@PathVariable Long tankId) {
        WaterTank tank = tankService.requireTank(tankId);
        Map<String, Object> result = new HashMap<>();
        result.put("tankId", tank.getId());
        result.put("tankCode", tank.getTankCode());
        result.put("balanceVolumeLiters", tankService.currentBalance(tankId));
        result.put("capacityLiters", tank.getCapacityLiters());
        return ApiResponse.ok(result);
    }

    /** 水箱台账（补加正向、排放负向），补加与排放分别记账在此一目了然。 */
    @GetMapping("/{tankId}/ledger")
    public ApiResponse<List<TankLedger>> ledger(@PathVariable Long tankId) {
        tankService.requireTank(tankId);
        return ApiResponse.ok(tankService.ledger(tankId));
    }
}
