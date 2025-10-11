package com.yzx.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.wms.WareOrderTaskEntity;
import com.yzx.wms.mapper.WareOrderTaskDao;
import com.yzx.wms.service.WareOrderTaskService;
import org.springframework.stereotype.Service;

/**
 * @className: WareOrderTaskServiceImpl
 * @author: yzx
 * @date: 2025/9/4 11:51
 * @Version: 1.0
 * @description:
 */
@Service
public class WareOrderTaskServiceImpl extends ServiceImpl<WareOrderTaskDao, WareOrderTaskEntity> implements WareOrderTaskService {
    @Override
    public WareOrderTaskEntity getOrderTaskByOrderSn(String orderSn) {

        WareOrderTaskEntity orderTaskEntity = this.baseMapper.selectOne(
                new QueryWrapper<WareOrderTaskEntity>().eq("order_sn", orderSn));

        return orderTaskEntity;
    }
}
