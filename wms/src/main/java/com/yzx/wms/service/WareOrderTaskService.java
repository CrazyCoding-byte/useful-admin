package com.yzx.wms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.wms.WareOrderTaskEntity;

/**
 * @className: WareOrderTaskService
 * @author: yzx
 * @date: 2025/9/4 11:50
 * @Version: 1.0
 * @description:
 */
public interface WareOrderTaskService extends IService<WareOrderTaskEntity> {
    WareOrderTaskEntity getOrderTaskByOrderSn(String orderSn);
}
