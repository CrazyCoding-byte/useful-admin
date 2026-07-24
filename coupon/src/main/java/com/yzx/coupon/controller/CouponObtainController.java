package com.yzx.coupon.controller;

import com.yzx.coupon.mapper.CouponObtainRecordMapper;
import com.yzx.coupon.service.impl.CouponObtainServiceImpl;
import com.yzx.model.AjaxResult;
import com.yzx.model.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @className: CouponObtainController
 * @author: yzx
 * @date: 2026/7/23 17:48
 * @Version: 1.0
 * @description:
 * /**
 *  * 用户端领券 API。
 *  *
 *  * 为什么和 CouponInfoAdminController 分开？
 *  * - AdminController 的路径是 /coupon/couponInfo，那是运营后台接口
 *  * - 这个 Controller 是普通用户用的，路径 /coupon/obtain
 *  * - 分开后可以给 C 端接口单独加限流（Sentinel）、单独监控

 */
@RestController
@RequestMapping("/coupon/obtain")
public class CouponObtainController {

    @Autowired
    private CouponObtainServiceImpl obtainService;

    /**
     * 领券
     * POST /coupon/obtain/{couponId}
     */
    @PostMapping("/{couponId}")
    public AjaxResult obtain(@PathVariable Long couponId) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return AjaxResult.error("请先登录");
        }

        boolean ok = obtainService.obtainCoupon(userId, couponId);
        return ok ? AjaxResult.success("领取成功")
                : AjaxResult.error("领取失败，库存不足或已领过");
    }
}
