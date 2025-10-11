package com.yzx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.order.OrderConfirmVo;
import com.yzx.model.order.OrderEntity;
import com.yzx.model.order.OrderSubmitVo;
import com.yzx.model.order.SubmitOrderResponseVo;

import java.util.concurrent.ExecutionException;

/**
 * @className: OrderService
 * @author: yzx
 * @date: 2025/8/30 5:48
 * @Version: 1.0
 * @description:
 */
public interface OrderService extends IService<OrderEntity> {
    OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException;

    SubmitOrderResponseVo submitOrder(OrderSubmitVo vo);

    void closeOrder(OrderEntity orderEntity);
}
