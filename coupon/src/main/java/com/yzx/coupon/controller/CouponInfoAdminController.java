package com.yzx.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.coupon.service.CouponInfoService;
import com.yzx.model.AjaxResult;
import com.yzx.model.coupon.CouponInfo;
import com.yzx.model.order.enums.CouponRangeType;
import com.yzx.model.order.enums.CouponType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 优惠券管理 Controller
 */
@RestController
@RequestMapping("/coupon/couponInfo")
public class CouponInfoAdminController {

    @Autowired
    private CouponInfoService couponInfoService;

    /**
     * 分页列表
     */
    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(required = false) String couponName,
            @RequestParam(required = false) String couponType,
            @RequestParam(required = false) String rangeType,
            @RequestParam(required = false) Boolean publishStatus) {

        CouponType couponTypeEnum = StringUtils.isNotBlank(couponType) ? CouponType.valueOf(couponType) : null;
        CouponRangeType rangeTypeEnum = StringUtils.isNotBlank(rangeType) ? CouponRangeType.valueOf(rangeType) : null;

        LambdaQueryWrapper<CouponInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(couponName), CouponInfo::getCouponName, couponName)
                .eq(couponTypeEnum != null, CouponInfo::getCouponType, couponTypeEnum)
                .eq(rangeTypeEnum != null, CouponInfo::getRangeType, rangeTypeEnum)
                .eq(publishStatus != null, CouponInfo::getPublishStatus, publishStatus)
                .orderByDesc(CouponInfo::getCreateTime);

        Page<CouponInfo> page = couponInfoService.page(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return AjaxResult.success(data);
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        CouponInfo info = couponInfoService.getById(id);
        return AjaxResult.success(info);
    }

    /**
     * 新增
     */
    @PostMapping
    public AjaxResult add(@RequestBody CouponInfo couponInfo) {
        if (couponInfo.getRangeType() == null) {
            couponInfo.setRangeType(CouponRangeType.ALL);
        }
        if (couponInfo.getPublishStatus() == null) {
            couponInfo.setPublishStatus(false);
        }
        boolean success = couponInfoService.save(couponInfo);
        return success ? AjaxResult.success("创建成功") : AjaxResult.error("创建失败");
    }

    /**
     * 修改
     */
    @PutMapping
    public AjaxResult update(@RequestBody CouponInfo couponInfo) {
        boolean success = couponInfoService.updateById(couponInfo);
        return success ? AjaxResult.success("修改成功") : AjaxResult.error("修改失败");
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        boolean success = couponInfoService.removeById(id);
        return success ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
    }
}
