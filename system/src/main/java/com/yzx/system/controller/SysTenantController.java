package com.yzx.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.common.tenant.TenantContext;
import com.yzx.model.AjaxResult;
import com.yzx.model.system.SysTenant;
import com.yzx.system.service.ISysTenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/system/tenant")
public class SysTenantController {

    @Autowired
    private ISysTenantService tenantService;

    @GetMapping("/availableList")
    public List<SysTenant> getAvailableTenantList() {
        log.info("收到获取可用租户列表请求");
        List<SysTenant> list = TenantContext.ignoreTenant(() -> tenantService.selectAvailableTenantList());
        log.info("查询到可用租户列表，数量: {}", list != null ? list.size() : 0);
        if (list != null && !list.isEmpty()) {
            list.forEach(tenant -> log.debug("租户: {} - {}", tenant.getTenantId(), tenant.getCompanyName()));
        }
        return list;
    }

    @GetMapping("/checkAvailable/{tenantId}")
    public boolean checkTenantAvailable(@PathVariable String tenantId) {
        return TenantContext.ignoreTenant(() -> tenantService.checkTenantAvailable(tenantId));
    }

    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String contactUserName,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(companyName), SysTenant::getCompanyName, companyName)
                .like(StringUtils.isNotBlank(contactUserName), SysTenant::getContactUserName, contactUserName)
                .eq(StringUtils.isNotBlank(status), SysTenant::getStatus, status)
                .orderByDesc(SysTenant::getCreateTime);

        Page<SysTenant> page = TenantContext.ignoreTenant(() ->
                tenantService.page(new Page<>(pageNum, pageSize), wrapper));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return AjaxResult.success(data);
    }

    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        SysTenant tenant = TenantContext.ignoreTenant(() -> tenantService.getById(id));
        return AjaxResult.success(tenant);
    }

    @PostMapping
    public AjaxResult add(@RequestBody SysTenant tenant) {
        log.info("收到新增租户请求: companyName={}, contactUserName={}, contactPhone={}, packageId={}, expireTime={}",
                tenant.getCompanyName(), tenant.getContactUserName(), tenant.getContactPhone(),
                tenant.getPackageId(), tenant.getExpireTime());
        boolean success = tenantService.insertTenant(tenant);

        return success ? AjaxResult.success("创建成功") : AjaxResult.error("创建失败");
    }

    @PutMapping
    public AjaxResult update(@RequestBody SysTenant tenant) {
        boolean success = tenantService.updateTenant(tenant);
        return success ? AjaxResult.success("修改成功") : AjaxResult.error("修改失败");
    }

    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        boolean success = tenantService.deleteTenantById(id);
        return success ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
    }
}
