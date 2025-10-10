package com.yzx.distribution.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.distribution.mapper.CommissionRecordMapper;
import com.yzx.distribution.service.CommissionRecordService;
import com.yzx.model.distribution.CommissionRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @className: CommissionRecordServiceimp
 * @author: yzx
 * @date: 2025/10/10 17:24
 * @Version: 1.0
 * @description:
 */
@Service
public class CommissionRecordServiceimp extends ServiceImpl<CommissionRecordMapper, CommissionRecord> implements CommissionRecordService {
    /**
     * 获取用户总佣金
     */
    @Override
    public BigDecimal getTotalCommissionByUserId(String userId) {
        return this.getBaseMapper().selectTotalCommissionByUserId(userId);
    }

    /**
     * 获取用户待结算佣金
     */
    @Override
    public BigDecimal getPendingCommissionByUserId(String userId) {
        return this.getBaseMapper().selectPendingCommissionByUserId(userId);
    }

    /**
     * 获取用户已结算佣金
     */
    @Override
    public BigDecimal getSettledCommissionByUserId(String userId) {
        return this.getBaseMapper().selectSettledCommissionByUserId(userId);
    }
}
