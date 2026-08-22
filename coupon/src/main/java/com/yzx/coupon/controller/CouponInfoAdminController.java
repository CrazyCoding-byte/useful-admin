package com.yzx.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.apiclient.api.ProductFeignService;
import com.yzx.coupon.mapper.CouponRangeMapper;
import com.yzx.coupon.service.CouponInfoService;
import com.yzx.coupon.service.CouponRangeService;
import com.yzx.model.AjaxResult;
import com.yzx.model.coupon.CouponInfo;
import com.yzx.model.coupon.CouponRange;
import com.yzx.model.order.enums.CouponRangeType;
import com.yzx.model.order.enums.CouponType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.TypeReference;

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
    @Autowired
    private CouponRangeService couponRangeService;

    /**
     * 分页列表
     */
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(defaultValue = "1") Long pageNum,
                           @RequestParam(defaultValue = "10") Long pageSize, @RequestParam(required = false) String couponName,
                           @RequestParam(required = false) String couponType, @RequestParam(required = false) String rangeType,
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

    @PostMapping("/publishCoupon/{couponId}")
    public AjaxResult publishCoupon(@PathVariable Long couponId) {
        boolean success = couponInfoService.publishCoupon(couponId);
        return success ? AjaxResult.success("发卷成功") : AjaxResult.error("发卷失败");
    }

    /**
     * 获取优惠卷已经配置的规则范围列表
     *
     * @param couponId
     * @return
     */
    @GetMapping("/{couponId}/range")
    public AjaxResult getRangeList(@PathVariable Long couponId) {
        List<CouponRange> couponRanges = couponRangeMapper
                .selectList(new LambdaQueryWrapper<CouponRange>().eq(CouponRange::getCouponId, couponId));
        return AjaxResult.success(couponRanges);
    }

    /**
     * 保存优惠卷规则范围
     *
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{couponId}/range")
    public AjaxResult saveRange(@PathVariable Long couponId, @RequestBody List<CouponRange> rangeList) {
        CouponInfo couponInfo = couponInfoService.getById(couponId);
        if (couponInfo == null)
            return AjaxResult.error("优惠卷不存在");
        CouponRangeType couponRangeType = couponInfo.getRangeType();
        if (couponRangeType == null)
            return AjaxResult.error("优惠卷适用的范围类型不能为空");
        if (couponRangeType == CouponRangeType.ALL) {
            if (rangeList != null && !rangeList.isEmpty()) {
                return AjaxResult.error("通用卷不能配置具体商品或分类");
            }
            couponRangeMapper.delete(new LambdaQueryWrapper<CouponRange>().eq(CouponRange::getCouponId, couponId));
            return AjaxResult.success("通用卷规则保存成功");
        }

        if (rangeList == null || rangeList.isEmpty())
            return AjaxResult.error("请至少选择一个适用范围");

        // 5. 校验前端提交的数据
        for (CouponRange range : rangeList) {
            if (range == null) {
                return AjaxResult.error("适用范围数据不能为空");
            }

            if (range.getRangeType() == null) {
                return AjaxResult.error("适用范围类型不能为空");
            }

            if (range.getRangeId() == null || range.getRangeId() <= 0) {
                return AjaxResult.error("适用范围ID不能为空");
            }

            if (range.getRangeType() != couponRangeType) {
                return AjaxResult.error("提交的适用范围类型与优惠券类型不一致");
            }
        }
        List<CouponRange> newRangeList = new ArrayList<>();

        if (couponRangeType == CouponRangeType.SKU) {
            Set<Long> skuIdSet = new HashSet();
            for (CouponRange range : rangeList) {
                Long skuId = range.getRangeId();
                if (skuIdSet.add(skuId)) {
                    CouponRange newRange = new CouponRange();
                    newRange.setCouponId(couponId);
                    newRange.setRangeType(CouponRangeType.SKU);
                    newRange.setRangeId(skuId);
                    newRangeList.add(newRange);
                }
            }
        }

        if (couponRangeType == CouponRangeType.CATEGORY) {
            List<Long> categoryIdList = rangeList.stream().map(CouponRange::getRangeId).distinct()
                    .collect(Collectors.toList());
            AjaxResult result = productFeignService.getCategoryDescendantIds(categoryIdList);
            Map<Long, List<Long>> descendantMap = result.getData("data", new TypeReference<Map<Long, List<Long>>>() {
            });
            if (descendantMap == null)
                return AjaxResult.error("分类信息查询失败,请重试");
            Set<Long> categoryISet = new HashSet<>();
            for (Long categoryId : categoryIdList) {
                List<Long> descendantIds = descendantMap.get(categoryId);
                if (descendantIds == null)
                    return AjaxResult.error("分类" + categoryId + "的子分类查询失败");
                for (Long descendantId : descendantIds) {
                    if (descendantId == null)
                        continue;
                    if (categoryISet.add(descendantId)) {
                        CouponRange newRange = new CouponRange();
                        newRange.setCouponId(couponId);
                        newRange.setRangeType(CouponRangeType.CATEGORY);
                        newRange.setRangeId(descendantId);
                        newRangeList.add(newRange);
                    }
                }
            }
        }
        couponRangeMapper.delete(new LambdaQueryWrapper<CouponRange>().eq(CouponRange::getCouponId, couponId));
        couponRangeService.saveBatch(newRangeList);
        return AjaxResult.success();
    }
}
