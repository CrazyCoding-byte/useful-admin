package com.yzx.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yzx.common.config.JwtHelp;
import com.yzx.model.AjaxResult;
import com.yzx.model.order.OrderEntity;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * @className: OrderController
 * @author: yzx
 * @date: 2025/10/11 17:38
 * @Version: 1.0
 * @description:
 */
@RestController
@Controller("order")
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private JwtHelp jwtHelp;

    /**
     * 获取订单信息
     * @param orderSn
     * @return
     */
    @GetMapping("/getOrderInfo/{orderSn}")
    public AjaxResult getOrderInfo(@PathVariable String orderSn) {
        OrderEntity one = orderService.getOne(new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getOrderSn, orderSn));
        if (Objects.isNull(one)) return AjaxResult.error("订单不存在");
        BaseUserDetail currentUser = jwtHelp.getCurrentUser();
        Long userId = currentUser.getBaseUser().getUserId();
        if (!userId.equals(one.getMemberId())) return AjaxResult.error("无权操作此订单");
        return AjaxResult.success(one);
    }

    @PostMapping("/order/updateOrder/{orderSn}")
    public AjaxResult updateOrder(@PathVariable String orderSn, @RequestBody String codeUrl) {
        OrderEntity one = orderService.getOne(new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getOrderSn, orderSn));
        if (Objects.isNull(one)) return AjaxResult.error("订单不存在");
        BaseUserDetail currentUser = jwtHelp.getCurrentUser();
        Long userId = currentUser.getBaseUser().getUserId();
        if (!userId.equals(one.getMemberId())) return AjaxResult.error("无权操作此订单");
        if (one.getStatus() == 4) return AjaxResult.error("订单已关闭");
        one.setCodeUrl(codeUrl);
        orderService.updateById(one);
        return AjaxResult.success();
    }

    @GetMapping("/oder/getOrderStatus")
    public AjaxResult getOrderStatus(@RequestParam String orderSn) {
        OrderEntity one = orderService.getOne(new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getOrderSn, orderSn));
        if (Objects.isNull(one)) return AjaxResult.error("订单不存在");
        BaseUserDetail currentUser = jwtHelp.getCurrentUser();
        Long userId = currentUser.getBaseUser().getUserId();
        if (!userId.equals(one.getMemberId())) return AjaxResult.error("无权操作此订单");
        return AjaxResult.success(one.getStatus());
    }


}
