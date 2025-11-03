package com.yzx.order.api;

import com.yzx.model.AjaxResult;
import com.yzx.model.order.OrderConfirmVo;
import com.yzx.model.order.OrderSubmitVo;
import com.yzx.model.order.SubmitOrderResponseVo;
import com.yzx.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutionException;

/**
 * @className: OrderApiController
 * @author: yzx
 * @date: 2025/8/29 10:55
 * @Version: 1.0
 * @description:
 */
@RestController
@RequestMapping("order")
public class OrderApiController {
    @Autowired
    private OrderService orderService;

    /**
     * 去结算确认页 查询出订单的信息
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @GetMapping(value = "/toTrade")
    public AjaxResult toTrade() throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = orderService.confirmOrder();
        //展示订单确认的数据
        return AjaxResult.success(confirmVo);
    }

    /**
     * 订单提交
     */
    @PostMapping(value = "/submitOrder")
    public AjaxResult submitOrder(OrderSubmitVo vo, Model model) {

        SubmitOrderResponseVo responseVo = orderService.submitOrder(vo);
        //下单成功来到支付选择页
        //下单失败回到订单确认页重新确定订单信息
        if (responseVo.getCode() == 0) {
            //成功
            return AjaxResult.success(responseVo);
        } else {
            String msg = "下单失败";
            switch (responseVo.getCode()) {
                case 1:
                    msg += "令牌订单信息过期，请刷新再次提交";
                    break;
                case 2:
                    msg += "订单商品价格发生变化，请确认后再次提交";
                    break;
                case 3:
                    msg += "库存锁定失败，商品库存不足";
                    break;
            }
            return AjaxResult.error(msg);
        }
    }
}
