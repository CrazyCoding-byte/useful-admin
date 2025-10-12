package com.yzx.coupon.controller;

import com.yzx.coupon.service.ActivityInfoService;
import com.yzx.coupon.service.CouponInfoService;
import com.yzx.model.cart.vo.CartItemVo;
import com.yzx.model.cart.vo.CartVo;
import com.yzx.model.coupon.CartInfoVo;
import com.yzx.model.order.OrderConfirmVo;
import com.yzx.model.order.OrderItemVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @className: ActivityInfoController
 * @author: yzx
 * @date: 2025/10/12 16:26
 * @Version: 1.0
 * @description:
 */
@RestController
@Controller("coupon")
public class ActivityInfoController {

    @Autowired
    private ActivityInfoService activityInfoService;

    @Autowired
    private CouponInfoService couponInfoService;

    //获取购物车里面满足条件优惠卷和活动的信息
    @PostMapping("inner/findCartActivityAndCoupon/{userId}")
    public OrderConfirmVo findCartActivityAndCoupon(@RequestBody List<CartItemVo> cartInfoList,
                                                    @PathVariable("userId") Long userId) {
        return activityInfoService.findCartActivityAndCoupon(cartInfoList, userId);
    }
}
