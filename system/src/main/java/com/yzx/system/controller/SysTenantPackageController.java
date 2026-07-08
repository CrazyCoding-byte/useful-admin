package com.yzx.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.AjaxResult;
import com.yzx.model.system.SysTenantPackage;
import com.yzx.system.service.ISysTenantPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户套餐管理 Controller
 */
@RestController
@RequestMapping("/system/tenantPackage")
public class SysTenantPackageController {

    @Autowired
    private ISysTenantPackageService tenantPackageService;

    /**
     * 获取套餐列表（支持分页和搜索）
     */
    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<SysTenantPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(packageName), SysTenantPackage::getPackageName, packageName)
               .eq(StringUtils.isNotBlank(status), SysTenantPackage::getStatus, status)
               .orderByDesc(SysTenantPackage::getCreateTime);

        Page<SysTenantPackage> page = tenantPackageService.page(new Page<>(pageNum, pageSize), wrapper);

        AjaxResult result = AjaxResult.success(page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    /**
     * 获取所有可用套餐（下拉选择用）
     */
    @GetMapping("/options")
    public AjaxResult options() {
        List<SysTenantPackage> list = tenantPackageService.selectAvailablePackages();
        return AjaxResult.success(list);
    }

    /**
     * 根据套餐ID获取详情
     */
    @GetMapping("/{packageId}")
    public AjaxResult getInfo(@PathVariable Long packageId) {
        SysTenantPackage pkg = tenantPackageService.getById(packageId);
        return AjaxResult.success(pkg);
    }

    /**
     * 新增套餐
     */
    @PostMapping
    public AjaxResult add(@RequestBody SysTenantPackage sysTenantPackage) {
        if (!tenantPackageService.checkPackageNameUnique(sysTenantPackage)) {
            return AjaxResult.error("套餐名称已存在");
        }
        tenantPackageService.save(sysTenantPackage);
        return AjaxResult.success("新增成功");
    }

    /**
     * 修改套餐
     */
    @PutMapping
    public AjaxResult edit(@RequestBody SysTenantPackage sysTenantPackage) {
        if (!tenantPackageService.checkPackageNameUnique(sysTenantPackage)) {
            return AjaxResult.error("套餐名称已存在");
        }
        tenantPackageService.updateById(sysTenantPackage);
        return AjaxResult.success("修改成功");
    }

    /**
     * 删除套餐
     */
    @DeleteMapping("/{packageId}")
    public AjaxResult remove(@PathVariable Long packageId) {
        tenantPackageService.removeById(packageId);
        return AjaxResult.success("删除成功");
    }
}
