package com.yzx.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.coupon.service.ActivityInfoService;
import com.yzx.model.AjaxResult;
import com.yzx.model.coupon.ActivityInfo;
import com.yzx.model.coupon.eunms.ActivityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 营销活动管理 Controller
 */
@RestController
@RequestMapping("/coupon/activity")
public class ActivityInfoAdminController {

    @Autowired
    private ActivityInfoService activityInfoService;

    /**
     * 分页列表
     */
    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(required = false) String activityName,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startTimeBegin,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startTimeEnd) {

        ActivityType typeEnum = StringUtils.isNotBlank(activityType) ? ActivityType.valueOf(activityType) : null;

        LambdaQueryWrapper<ActivityInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(activityName), ActivityInfo::getActivityName, activityName)
                .eq(typeEnum != null, ActivityInfo::getActivityType, typeEnum)
                .ge(startTimeBegin != null, ActivityInfo::getStartTime, startTimeBegin)
                .le(startTimeEnd != null, ActivityInfo::getEndTime, startTimeEnd)
                .orderByDesc(ActivityInfo::getCreateTime);

        Page<ActivityInfo> page = activityInfoService.page(new Page<>(pageNum, pageSize), wrapper);

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
        ActivityInfo info = activityInfoService.getById(id);
        return AjaxResult.success(info);
    }

    /**
     * 新增
     */
    @PostMapping
    public AjaxResult add(@RequestBody ActivityInfo activityInfo) {
        boolean success = activityInfoService.save(activityInfo);
        return success ? AjaxResult.success("创建成功") : AjaxResult.error("创建失败");
    }

    /**
     * 修改
     */
    @PutMapping
    public AjaxResult update(@RequestBody ActivityInfo activityInfo) {
        boolean success = activityInfoService.updateById(activityInfo);
        return success ? AjaxResult.success("修改成功") : AjaxResult.error("修改失败");
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        boolean success = activityInfoService.removeById(id);
        return success ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
    }
}
