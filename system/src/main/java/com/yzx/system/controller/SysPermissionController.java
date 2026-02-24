package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.system.service.ISysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * @program: xz-framework-parent-reversion
 * @description: 用户权限操作类
 * @author: wdw
 * @create: 2020-02-13 14:46
 **/
@RestController
@RequestMapping("/system/permission")
public class SysPermissionController {

    @Autowired
    ISysPermissionService sysPermissionService;

    /**
     * 获取角色数据权限
     *
     * @param userId 用户ID
     * @return 角色权限信息
     */
    @GetMapping("/getRolePermissionByUserId/{userId}")
    public AjaxResult getRolePermissionByUserId(@PathVariable Long userId) {
        return AjaxResult.success(sysPermissionService.getRolePermission(userId));
    }

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户ID
     * @return 菜单权限信息
     */
    @GetMapping("/getMenuPermissionByUserId/{userId}")
    public AjaxResult getMenuPermissionByUserId(@PathVariable Long userId) {
        return AjaxResult.success(sysPermissionService.getMenuPermission(userId));
    }

}
