package com.yzx.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.apiclient.api.ProductFeignService;
import com.yzx.coupon.mapper.CouponRangeMapper;
import com.yzx.coupon.service.CouponInfoService;
import com.yzx.model.AjaxResult;
import com.yzx.model.coupon.CouponInfo;
import com.yzx.model.coupon.CouponRange;
import com.yzx.model.order.enums.CouponRangeType;
import com.yzx.model.order.enums.CouponType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 优惠券管理 Controller
 */
@RestController
@RequestMapping("/coupon/couponInfo")
public class CouponInfoAdminController {

    @Autowired
    private CouponInfoService couponInfoService;
    @Autowired
    private CouponRangeMapper couponRangeMapper;
    @Autowired
    private ProductFeignService productFeignService;

    /**
     * 分页列表
     */
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize, @RequestParam(required = false) String couponName, @RequestParam(required = false) String couponType, @RequestParam(required = false) String rangeType, @RequestParam(required = false) Boolean publishStatus) {

        CouponType couponTypeEnum = StringUtils.isNotBlank(couponType) ? CouponType.valueOf(couponType) : null;
        CouponRangeType rangeTypeEnum = StringUtils.isNotBlank(rangeType) ? CouponRangeType.valueOf(rangeType) : null;

        LambdaQueryWrapper<CouponInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(couponName), CouponInfo::getCouponName, couponName).eq(couponTypeEnum != null, CouponInfo::getCouponType, couponTypeEnum).eq(rangeTypeEnum != null, CouponInfo::getRangeType, rangeTypeEnum).eq(publishStatus != null, CouponInfo::getPublishStatus, publishStatus).orderByDesc(CouponInfo::getCreateTime);

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

    @PostMapping("/publishCoupon/{couponId}")
    public AjaxResult publishCoupon(@PathVariable Long couponId) {
        boolean success = couponInfoService.publishCoupon(couponId);
        return success ? AjaxResult.success("发卷成功") : AjaxResult.error("发卷失败");
    }

    /**
     * 获取优惠卷已经配置的规则范围列表
     * @param couponId
     * @return
     */
    @GetMapping("/{couponId}/range")
    public AjaxResult getRangeList(@PathVariable Long couponId) {
        List<CouponRange> couponRanges = couponRangeMapper.selectList(new LambdaQueryWrapper<CouponRange>().eq(CouponRange::getCouponId, couponId));
        return AjaxResult.success(couponRanges);
    }

    /**
     * 保存优惠卷规则范围
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{couponId}/range")
    public AjaxResult saveRange(@PathVariable Long couponId, @RequestBody List<CouponRange> rangeList) {
        //先删除旧有的规则
        couponRangeMapper.delete(new LambdaQueryWrapper<CouponRange>().eq(CouponRange::getCouponId, couponId));
        if (rangeList == null || rangeList.isEmpty()) return AjaxResult.success();
        //收集所偶分类锚点,远程展开 自己+所有子孙
        List<Long> categoryAnchorIds = rangeList.stream().filter(r -> r.getRangeType() == CouponRangeType.CATEGORY)
                .map(CouponRange::getRangeId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, List<Long>> descendantMap;
        if (!categoryAnchorIds.isEmpty()) {
            AjaxResult res = productFeignService.getCategoryDescendantIds(categoryAnchorIds);
            Object data = res.get("data");
            if (data instanceof Map) {
                descendantMap = new HashMap<>();
                ((Map<?, ?>) data).forEach((k, v) -> descendantMap.put(Long.valueOf(k.toString()), (List<Long>) v));
            } else {
                descendantMap = Collections.emptyMap();
                //展开失败不能静默降级，否则分类规则会存成只有锚点
                return AjaxResult.error("分类展开失败，请重试");
            }
        } else {
            descendantMap = Collections.emptyMap();
        }
        //3.按照类型分别处理
        Set<Long> inserted = new HashSet<>();
        for (CouponRange range : rangeList) {
            CouponRangeType type = range.getRangeType();
            if (type == null) continue;
            switch (type) {
                //通用卷
                case ALL:
                    break;
                //指定商品
                case SKU:
                    if (inserted.add(range.getRangeId())) {
                        range.setCouponId(couponId);
                        couponRangeMapper.insert(range);
                    }
                    //指定分类
                    break;
                case CATEGORY:
                    List<Long> expanded = descendantMap.
                            getOrDefault(range.getRangeId(), Collections.singletonList(range.getRangeId()));
                    for (Long cid : expanded) {
                        if (inserted.add(cid)) {
                            CouponRange r = new CouponRange();
                            r.setCouponId(couponId);
                            r.setRangeType(CouponRangeType.CATEGORY);
                            r.setRangeId(cid);
                            couponRangeMapper.insert(r);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return AjaxResult.success();
    }
}
