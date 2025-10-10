package com.yzx.distribution.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.distribution.CommissionRecord;

import java.math.BigDecimal;

/**
 * @className: CommissionRecord
 * @author: yzx
 * @date: 2025/10/10 17:18
 * @Version: 1.0
 * @description:
 */
public interface CommissionRecordService extends IService<CommissionRecord> {
    /**
     * 获取用户总佣金
     */
    BigDecimal getTotalCommissionByUserId(String userId);

    /**
     * 获取用户待结算佣金
     */
    BigDecimal getPendingCommissionByUserId(String userId);

    /**
     * 获取用户已结算佣金
     */
    BigDecimal getSettledCommissionByUserId(String userId);

    /**
     * 检查记录是否存在
     */
}
