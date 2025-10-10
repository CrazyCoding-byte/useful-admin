package com.yzx.distribution.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.distribution.CommissionRecord;
import com.yzx.model.distribution.DistributionRelation;
import com.yzx.model.distribution.UserTeamInfo;

import java.math.BigDecimal;
import java.util.List;

/**
 * @className: DistributionService
 * @author: yzx
 * @date: 2025/10/10 17:04
 * @Version: 1.0
 * @description:
 */
public interface DistributionService extends IService<DistributionRelation> {

    /**
     * 建立分销关系
     */
    boolean buildDistributionRelation(Long userId, String inviteQrCode);

    /**
     * 计算订单佣金
     */
    void calculateOrderCommission(String orderId, Long userId, BigDecimal orderAmount);

    /**
     * 获取用户团队信息
     */
    UserTeamInfo getUserTeamInfo(Long userId);

    /**
     * 获取用户的佣金记录
     */
    List<CommissionRecord> getUserCommissionRecords(Long userId, Integer status);

    /**
     * 结算佣金
     */
    boolean settleCommission(Long commissionRecordId);

    /**
     * 批量结算佣金
     */
    boolean batchSettleCommission(List<Long> commissionRecordIds);

    /**
     * 重新计算订单佣金
     */
    void recalculateOrderCommission(String orderId, Long userId, BigDecimal newOrderAmount);
}