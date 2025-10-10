package com.yzx.distribution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.distribution.mapper.CommissionConfigMapper;
import com.yzx.distribution.service.CommissionConfigService;
import com.yzx.model.distribution.CommissionConfig;
import org.springframework.stereotype.Service;

/**
 * @className: CommissionConfigService
 * @author: yzx
 * @date: 2025/10/10 17:21
 * @Version: 1.0
 * @description:
 */
@Service
public class CommissionConfigServiceimp extends ServiceImpl<CommissionConfigMapper, CommissionConfig>  implements CommissionConfigService {
    /**
     * 根据层级获取佣金配置
     */
    @Override
    public CommissionConfig getCommissionConfigByLevel(Integer level) {
        if (level == null || level <= 0) {
            return null;
        }

        return this.getOne(
                new QueryWrapper<CommissionConfig>()
                        .eq("level", level)
                        .eq("status", 1)
        );
    }
}
