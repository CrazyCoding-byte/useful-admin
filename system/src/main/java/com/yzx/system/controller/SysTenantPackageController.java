package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.system.SysTenantPackage;
import com.yzx.system.annotation.RequiresPermission;
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
     * 获取套餐列表
     */
    @GetMapping("/list")
    public AjaxResult list() {
        List<SysTenantPackage> list = tenantPackageService.selectAvailablePackages();
        return AjaxResult.success(list);
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
