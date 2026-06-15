package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.annotation.Log;
import com.yzx.model.enums.BusinessType;
import com.yzx.model.system.SysRole;
import com.yzx.model.system.SysUser;
import com.yzx.model.system.SysUserRole;
import com.yzx.system.domain.bo.SysRoleBo;
import com.yzx.system.domain.convert.SysRoleConvert;
import com.yzx.system.service.ISysRoleService;
import com.yzx.system.service.ISysUserService;
import com.yzx.system.service.impl.SysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.token.TokenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 角色信息
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleController {
    @Autowired
    private ISysRoleService roleService;

//    @Autowired
//    private TokenService tokenService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysRoleConvert roleConvert;

//    @Autowired
//    private ISysDeptService deptService;

    @GetMapping("/list")
    public AjaxResult list(SysRole role) {
        SysRoleBo bo = roleConvert.entityToBo(role);
        return AjaxResult.success(roleService.selectRoleList(bo));
    }

//    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
//    @PreAuthorize("@ss.hasPermi('system:role:export')")
//    @PostMapping("/export")
//    public void export(HttpServletResponse response, SysRole role)
//    {
//        List<SysRole> list = roleService.selectRoleList(role);
//        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
//        util.exportExcel(response, list, "角色数据");
//    }

    /**
     * 根据角色编号获取详细信息
     */
    @GetMapping(value = "/{roleId}")
    public AjaxResult getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return AjaxResult.success(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysRoleBo role) {
        if (!roleService.checkRoleNameUnique(role)) {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        // createBy、createTime 由 MyMetaObjectHandler 自动填充
        return AjaxResult.success(roleService.insertRole(role));

    }

//    /**
//     * 修改保存角色
//     */
//    @PreAuthorize("@ss.hasPermi('system:role:edit')")
//    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
//    @PutMapping
//    public AjaxResult edit(@Validated @RequestBody SysRole role)
//    {
//        roleService.checkRoleAllowed(role);
//        roleService.checkRoleDataScope(role.getRoleId());
//        if (!roleService.checkRoleNameUnique(role))
//        {
//            return error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
//        }
//        else if (!roleService.checkRoleKeyUnique(role))
//        {
//            return error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
//        }
//        role.setUpdateBy(getUsername());
//
//        if (roleService.updateRole(role) > 0)
//        {
//            // 更新缓存用户权限
//            LoginUser loginUser = getLoginUser();
//            if (StringUtils.isNotNull(loginUser.getUser()) && !loginUser.getUser().isAdmin())
//            {
//                loginUser.setUser(userService.selectUserByUserName(loginUser.getUser().getUserName()));
//                loginUser.setPermissions(permissionService.getMenuPermission(loginUser.getUser()));
//                tokenService.setLoginUser(loginUser);
//            }
//            return success();
//        }
//        return error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
//    }

    /**
     * 修改保存数据权限
     */
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dataScope")
    public AjaxResult dataScope(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return AjaxResult.success(roleService.authDataScope(role));
    }

    /**
     * 状态修改
     */
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        // updateBy、updateTime 由 MyMetaObjectHandler 自动填充
        return AjaxResult.success(roleService.updateRoleStatus(role.getRoleId(), role.getStatus()));
    }

    /**
     * 删除角色
     */
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public AjaxResult remove(@PathVariable Long[] roleIds) {
        return AjaxResult.success(roleService.deleteRoleByIds(Arrays.asList(roleIds)));
    }

    /**
     * 获取角色选择框列表
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        return AjaxResult.success(roleService.selectRoleAll());
    }

    /**
     * 查询已分配用户角色列表
     */
    @GetMapping("/authUser/allocatedList")
    public AjaxResult allocatedList(SysUser user) {
        List<SysUser> list = userService.selectAllocatedList(user);
        return AjaxResult.success(list);
    }

    /**
     * 查询未分配用户角色列表
     */
    @GetMapping("/authUser/unallocatedList")
    public AjaxResult unallocatedList(SysUser user) {
        List<SysUser> list = userService.selectUnallocatedList(user);
        return AjaxResult.success(list);
    }

    /**
     * 取消授权用户
     */
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancel")
    public AjaxResult cancelAuthUser(@RequestBody SysUserRole userRole) {
        return AjaxResult.success(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消授权用户
     */
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancelAll")
    public AjaxResult cancelAuthUserAll(Long roleId, Long[] userIds) {
        return AjaxResult.success(roleService.deleteAuthUsers(roleId, userIds));
    }

    /**
     * 批量选择用户授权
     */
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/selectAll")
    public AjaxResult selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        return AjaxResult.success(roleService.insertAuthUsers(roleId, userIds));
    }

//    /**
//     * 获取对应角色部门树列表
//     */
//    @PreAuthorize("@ss.hasPermi('system:role:query')")
//    @GetMapping(value = "/deptTree/{roleId}")
//    public AjaxResult deptTree(@PathVariable("roleId") Long roleId)
//    {
//        AjaxResult ajax = AjaxResult.success();
//        ajax.put("checkedKeys", deptService.selectDeptListByRoleId(roleId));
//        ajax.put("depts", deptService.selectDeptTreeList(new SysDept()));
//        return ajax;
//    }
}
