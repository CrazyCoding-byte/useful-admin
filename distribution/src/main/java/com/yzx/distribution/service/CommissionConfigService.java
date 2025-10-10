package com.yzx.distribution.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.distribution.CommissionConfig;

/**
 * @className: CommissionConfigService
 * @author: yzx
 * @date: 2025/10/10 17:17
 * @Version: 1.0
 * @description:
 */
public interface CommissionConfigService extends IService<CommissionConfig> {
    /**
     * 根据层级获取佣金配置
     */
    CommissionConfig getCommissionConfigByLevel(Integer level);
}
