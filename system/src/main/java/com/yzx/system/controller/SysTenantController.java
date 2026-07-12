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

import java.util.List;

/**
 * 租户管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/system/tenant")
public class SysTenantController {

    @Autowired
    private ISysTenantService tenantService;

    /**
     * 获取可用租户列表（供登录选择）
     * 此接口不需要租户过滤，因为是登录前调用
     */
    @GetMapping("/availableList")
    public List<SysTenant> getAvailableTenantList() {
        log.info("收到获取可用租户列表请求");
        List<SysTenant> list = TenantContext.ignoreTenant(() -> tenantService.selectAvailableTenantList());
        log.info("查询到可用租户列表，数量: {}", list != null ? list.size() : 0);
        if (list != null && !list.isEmpty()) {
            list.forEach(tenant -> log.debug("租户: {} - {}", tenant.getTenantId(), tenant.getTenantName()));
        }
        return list;
    }

    /**
     * 检查租户是否可用
     */
    @GetMapping("/checkAvailable/{tenantId}")
    public boolean checkTenantAvailable(@PathVariable String tenantId) {
        return TenantContext.ignoreTenant(() -> tenantService.checkTenantAvailable(tenantId));
    }

    /**
     * 获取租户列表（管理后台用，支持分页和搜索）
     */
    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(tenantName), SysTenant::getTenantName, tenantName)
               .like(StringUtils.isNotBlank(contactName), SysTenant::getContactName, contactName)
               .eq(StringUtils.isNotBlank(status), SysTenant::getStatus, status)
               .orderByDesc(SysTenant::getCreateTime);

        Page<SysTenant> page = TenantContext.ignoreTenant(() ->
                tenantService.page(new Page<>(pageNum, pageSize), wrapper));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return AjaxResult.success(data);
    }

    /**
     * 根据ID获取租户详情
     */
    @GetMapping("/{tenantId}")
    public AjaxResult getById(@PathVariable String tenantId) {
        SysTenant tenant = TenantContext.ignoreTenant(() -> tenantService.getById(tenantId));
        return AjaxResult.success(tenant);
    }

    /**
     * 新增租户
     */
    @PostMapping
    public AjaxResult add(@RequestBody SysTenant tenant) {
        log.info("收到新增租户请求: tenantName={}, contactName={}, contactPhone={}, packageId={}, expireTime={}",
                tenant.getTenantName(), tenant.getContactName(), tenant.getContactPhone(),
                tenant.getPackageId(), tenant.getExpireTime());
        boolean success = tenantService.insertTenant(tenant);
        return success ? AjaxResult.success("创建成功") : AjaxResult.error("创建失败");
    }

    /**
     * 修改租户
     */
    @PutMapping
    public AjaxResult update(@RequestBody SysTenant tenant) {
        boolean success = tenantService.updateTenant(tenant);
        return success ? AjaxResult.success("修改成功") : AjaxResult.error("修改失败");
    }

    /**
     * 删除租户
     */
    @DeleteMapping("/{tenantId}")
    public AjaxResult delete(@PathVariable String tenantId) {
        boolean success = tenantService.deleteTenantById(tenantId);
        return success ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
    }
}
