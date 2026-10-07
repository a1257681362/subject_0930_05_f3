package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.TankRequest;
import com.evops.plant.entity.Greenhouse;
import com.evops.plant.entity.TankLedger;
import com.evops.plant.entity.WaterTank;
import com.evops.plant.mapper.GreenhouseMapper;
import com.evops.plant.mapper.TankLedgerMapper;
import com.evops.plant.mapper.WaterTankMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 营养液水箱建档与台账查询；台账只追加，余额由最新一笔台账给出（无台账即 0）。 */
@Service
public class WaterTankService {

    private final WaterTankMapper tankMapper;
    private final GreenhouseMapper greenhouseMapper;
    private final TankLedgerMapper ledgerMapper;

    public WaterTankService(WaterTankMapper tankMapper, GreenhouseMapper greenhouseMapper,
                            TankLedgerMapper ledgerMapper) {
        this.tankMapper = tankMapper;
        this.greenhouseMapper = greenhouseMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Transactional
    public WaterTank create(TankRequest request) {
        if (request.getGreenhouseId() != null) {
            Greenhouse gh = greenhouseMapper.selectById(request.getGreenhouseId());
            if (gh == null) {
                throw new IllegalArgumentException("温室不存在: " + request.getGreenhouseId());
            }
        }
        WaterTank tank = new WaterTank();
        tank.setTankCode(request.getTankCode());
        tank.setTankName(request.getTankName());
        tank.setGreenhouseId(request.getGreenhouseId());
        tank.setCapacityLiters(request.getCapacityLiters());
        tank.setStatus(PfStatus.ACTIVE);
        try {
            tankMapper.insert(tank);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("水箱编码已存在: " + request.getTankCode());
        }
        return tank;
    }

    public List<WaterTank> list() {
        return tankMapper.selectList(new QueryWrapper<WaterTank>().orderByAsc("tank_code"));
    }

    public WaterTank requireTank(Long tankId) {
        WaterTank tank = tankMapper.selectById(tankId);
        if (tank == null) {
            throw new IllegalArgumentException("水箱不存在: " + tankId);
        }
        return tank;
    }

    /** 水箱当前液位（升）：取最新一笔台账余额，无台账为 0。 */
    public BigDecimal currentBalance(Long tankId) {
        TankLedger latest = ledgerMapper.selectOne(new QueryWrapper<TankLedger>()
                .eq("tank_id", tankId)
                .orderByDesc("occurred_at")
                .orderByDesc("id")
                .last("LIMIT 1"));
        return latest == null ? BigDecimal.ZERO : latest.getBalanceVolumeLiters();
    }

    public List<TankLedger> ledger(Long tankId) {
        return ledgerMapper.selectList(new QueryWrapper<TankLedger>()
                .eq("tank_id", tankId)
                .orderByDesc("occurred_at")
                .orderByDesc("id"));
    }
}
