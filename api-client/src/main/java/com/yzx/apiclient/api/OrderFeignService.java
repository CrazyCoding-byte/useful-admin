package com.yzx.apiclient.api;

import com.yzx.model.AjaxResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * @className: OrderFeignService
 * @author: yzx
 * @date: 2025/10/11 17:40
 * @Version: 1.0
 * @description:
 */
@FeignClient("service-order")
public interface OrderFeignService {
    @GetMapping("/order/getOrderInfo/{orderSn}")
    public AjaxResult getOrderInfo(@PathVariable String orderSn);

    @PostMapping("/order/updateOrder/{orderSn}")
    public AjaxResult updateOrder(@PathVariable String orderSn, @RequestBody String codeUrl);

    @GetMapping("/order/getOrderStatus")
    AjaxResult getOrderStatus(String orderSn);
}
