package com.yzx.usefulagent.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzx.apiclient.api.OrderFeignService;
import com.yzx.apiclient.api.ProductFeignService;
import com.yzx.model.AjaxResult;
import com.yzx.model.HttpStatus;
import com.yzx.model.cart.vo.SkuInfoVo;
import com.yzx.model.order.OrderEntity;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.summary.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.yzx.model.utils.ImageUtils.log;

/**
 * @className: EcommerceTools
 * @author: yzx
 * @date: 2026/2/22 3:49
 * @Version: 1.0
 * @description:
 */
@Component
@Slf4j
public class EcommerceTools {
    @Autowired
    private OrderFeignService orderFeignService;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final String REFUND_LIMIT_KEY = "agent:refund:limit:";
    private static final String TOOL_LOG_PREFIX = "[工具调用]";

    @Tool("查询用户的所有订单列表,入参是用户的id")
    public List<OrderEntity> listUserOrders(String userId) {
        log.info(TOOL_LOG_PREFIX + "listUserOrders，用户ID：{}", userId);
        // 基础参数校验
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        AjaxResult orderResult = orderFeignService.getUserAllOrders(userId);
        if (orderResult.get("code").equals(HttpStatus.SUCCESS)) {
            List<OrderEntity> orderList = orderResult.getData("data", new TypeReference<List<OrderEntity>>() {
            });
            return orderList != null ? orderList : new ArrayList<>();
        } else {
            throw new RuntimeException("查询用户订单失败: " + orderResult.get("msg"));
        }
    }

    @Tool("根据订单号查询订单详情,入参是订单号")
    public OrderEntity getOrderDetail(String orderNo) {
        log.info(TOOL_LOG_PREFIX + "getOrderByNo，订单号：{}", orderNo);
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        AjaxResult orderResult = orderFeignService.getOrderInfo(orderNo);
        if (orderResult.get("code").equals(HttpStatus.SUCCESS)) {
            OrderEntity data = orderResult.getData("data", new TypeReference<OrderEntity>() {
            });
            return data;
        } else {
            throw new RuntimeException("查询订单失败: " + orderResult.get("msg"));
        }
    }

    @Tool("为订单申请退款,入参是订单号")
    public String applyRefund(String orderNo, String userId) {
        String logPrefix = TOOL_LOG_PREFIX + "applyRefund，订单号：" + orderNo + "，用户ID：" + userId;
        log.info(logPrefix);

        // 1. 强制参数校验
        if (orderNo == null || orderNo.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("订单号和用户ID不能为空");
        }
        String limitKey = REFUND_LIMIT_KEY + orderNo;
        Boolean isLimit = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", 1, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isLimit)) {
            log.warn(logPrefix + "：退款申请频率超限");
            return "该订单1小时内已申请过退款，请耐心等待审核结果";
        }
        AjaxResult orderResult = orderFeignService.updateOrder(orderNo, "");
        if (!(orderResult.get("code").equals(HttpStatus.SUCCESS))) {
            return "数据错误请联系管理员";
        }
        OrderEntity data = orderResult.getData("data", new TypeReference<OrderEntity>() {
        });
        if (Objects.isNull(data)) {
            return "订单不存在";
        }
        if (!(data.getMemberId().equals(userId))) {
            return "用户ID和订单不匹配";
        }
        // 4. 状态校验：只有待发货的订单才能申请退款
        if (!"1".equals(data.getStatus())) {
            return "仅待发货订单可申请退款，该订单状态为：" + data.getStatus() + "，无法申请退款";
        }
        //todo: 待完善退款服务
        return "退款成功";
    }

    @Tool("查询商品详情")
    public SkuInfoVo getSkuInfo(Long skuId) {
        AjaxResult info = productFeignService.getInfo(skuId);
        if (info.get("code").equals(HttpStatus.SUCCESS)) {
            SkuInfoVo data = info.getData("data", new TypeReference<SkuInfoVo>() {
            });
            return data;
        } else {
            throw new RuntimeException("查询商品详情失败: " + info.get("msg"));
        }
    }
}
