package com.yzx.system.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.AjaxResult;
import com.yzx.model.StringUtils;
import com.yzx.model.annotation.Log;
import com.yzx.model.enums.BusinessType;
import com.yzx.model.system.RegisterUserTo;
import com.yzx.model.system.SysRole;
import com.yzx.model.system.SysUser;
import com.yzx.model.system.TableDataInfo;
import com.yzx.model.utils.SecurityUtils;


import com.yzx.system.annotation.RequiresPermission;
import com.yzx.system.service.ISysRoleService;
import com.yzx.system.service.ISysUserService;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户信息
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user")
public class SysUserController {
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

//    @Autowired
//    private ISysDeptService deptService;
//
//    @Autowired
//    private ISysPostService postService;

    /**
     * 获取用户列表
     */
    @PostMapping("/list/{pageNum}/{pageSize}")
    @RequiresPermission("system:user:list")
    public AjaxResult list(@RequestBody Map<String, Object> params, @PathVariable("pageNum") Integer pageNum, @PathVariable("pageSize") Integer pageSize) {
        SysUser user = new SysUser();
        user.setUserName((String) params.get("userName"));
        user.setNickName((String) params.get("nickName"));
        user.setPhonenumber((String) params.get("phonenumber"));
        user.setStatus((String) params.get("status"));
        
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        Page<SysUser> result = userService.selectUserList(user, page, params);
        return AjaxResult.success(result);
    }

    @GetMapping("/list")
    public AjaxResult list() {
        // 直接用 MP 自带的 list，数据权限自动生效
        List<SysUser> list = userService.list();
        return AjaxResult.success(list);
    }


//    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
//    @PostMapping("/export")
//    public void export(HttpServletResponse response, SysUser user)
//    {
//        List<SysUser> list = userService.selectUserList(user);
//        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
//        util.exportExcel(response, list, "用户数据");
//    }
//
//    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
//    @PostMapping("/importData")
//    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
//    {
//        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
//        List<SysUser> userList = util.importExcel(file.getInputStream());
//        String operName = getUsername();
//        String message = userService.importUser(userList, updateSupport, operName);
//        return success(message);
//    }
//
//    @PostMapping("/importTemplate")
//    public void importTemplate(HttpServletResponse response)
//    {
//        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
//        util.importTemplateExcel(response, "用户数据");
//    }

    /**
     * 根据用户编号获取详细信息
     */
//    @PreAuthorize("@ss.hasPermi('system:user:query')")
//    @GetMapping(value = { "/", "/{userId}" })
//    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId)
//    {
//        AjaxResult ajax = AjaxResult.success();
//        if (StringUtils.isNotNull(userId))
//        {
//            userService.checkUserDataScope(userId);
//            SysUser sysUser = userService.selectUserById(userId);
//            ajax.put(AjaxResult.DATA_TAG, sysUser);
//            ajax.put("postIds", postService.selectPostListByUserId(userId));
//            ajax.put("roleIds", sysUser.getRoles().stream().map(SysRole::getRoleId).collect(Collectors.toList()));
//        }
//        List<SysRole> roles = roleService.selectRoleAll();
//        ajax.put("roles", SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
//        ajax.put("posts", postService.selectPostAll());
//        return ajax;
//    }
    @GetMapping("/getUserInfo/{userId}")
    public AjaxResult getUserInfo(@PathVariable String userId) {
        return AjaxResult.success(userService.selectUserById(Long.valueOf(userId)));
    }

    @GetMapping("/getUserInfoByQrCode/{code}")
    public AjaxResult getUserInfoByQrCode(@PathVariable String code) {
        return AjaxResult.success(userService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getQrCode, code)));
    }
//    /**
//     * 新增用户
//     */
//    @PreAuthorize("@ss.hasPermi('system:user:add')")
//    @Log(title = "用户管理", businessType = BusinessType.INSERT)
//    @PostMapping
//    public AjaxResult add(@Validated @RequestBody SysUser user)
//    {
//        deptService.checkDeptDataScope(user.getDeptId());
//        roleService.checkRoleDataScope(user.getRoleIds());
//        if (!userService.checkUserNameUnique(user))
//        {
//            return error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
//        }
//        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
//        {
//            return error("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
//        }
//        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
//        {
//            return error("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
//        }
//        user.setCreateBy(getUsername());
//        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
//        return toAjax(userService.insertUser(user));
//    }

//    /**
//     * 修改用户
//     */
//    @PreAuthorize("@ss.hasPermi('system:user:edit')")
//    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
//    @PutMapping
//    public AjaxResult edit(@Validated @RequestBody SysUser user)
//    {
//        userService.checkUserAllowed(user);
//        userService.checkUserDataScope(user.getUserId());
//        deptService.checkDeptDataScope(user.getDeptId());
//        roleService.checkRoleDataScope(user.getRoleIds());
//        if (!userService.checkUserNameUnique(user))
//        {
//            return error("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
//        }
//        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
//        {
//            return error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
//        }
//        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
//        {
//            return error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
//        }
//        user.setUpdateBy(getUsername());
//        return toAjax(userService.updateUser(user));
//    }

    /**
     * 删除用户
     */
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds) {
        if (CollectionUtils.isEmpty(Arrays.asList(userIds))) {
            return AjaxResult.error("请选择要删除的用户");
        }
        if (ArrayUtils.contains(userIds, SecurityUtils.getUserId())) {
            return AjaxResult.error("当前用户不能删除");
        }
        return AjaxResult.success(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user) {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        // updateBy、updateTime 由 MyMetaObjectHandler 自动填充
        return AjaxResult.success(userService.resetPwd(user));
    }

    /**
     * 状态修改
     */
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user) {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        // updateBy、updateTime 由 MyMetaObjectHandler 自动填充
        return AjaxResult.success(userService.updateUserStatus(user));
    }

    /**
     * 根据用户编号获取授权角色
     */
    @GetMapping("/authRole/{userId}")
    public AjaxResult authRole(@PathVariable("userId") Long userId) {
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        ajax.put("user", user);
        ajax.put("roles", SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
        return ajax;
    }

    /**
     * 用户授权角色
     */
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/authRole")
    public AjaxResult insertAuthRole(Long userId, Long[] roleIds) {
        userService.checkUserDataScope(userId);
        roleService.checkRoleDataScope(roleIds);
        userService.insertUserAuth(userId, roleIds);
        return AjaxResult.success();
    }

    @PostMapping("/register")
    public AjaxResult register(@Valid @RequestBody RegisterUserTo user) {

        return AjaxResult.success(userService.register(user));
    }

    /**
     * 新增或修改用户
     */
    @PostMapping
    @RequiresPermission("system:user:add")
    public AjaxResult save(@Validated @RequestBody SysUser user) {
        // 检查用户名、手机号码和邮箱的唯一性
        if (!userService.checkUserNameUnique(user)) {
            return AjaxResult.error("用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            return AjaxResult.error("用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return AjaxResult.error("用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }

        if (user.getUserId() == null) {
            // 新增用户
            // createBy、createTime 由 MyMetaObjectHandler 自动填充
            user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
            return AjaxResult.success(userService.insertUser(user));
        } else {
            // 修改用户
            userService.checkUserAllowed(user);
            userService.checkUserDataScope(user.getUserId());
            // updateBy、updateTime 由 MyMetaObjectHandler 自动填充
            // 如果密码不为空，才加密并设置密码
            if (!StringUtils.isEmpty(user.getPassword())) {
                user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
            }
            return AjaxResult.success(userService.updateUser(user));
        }
    }

    /**
     * 分销注册
     * @param user
     * @param code
     * @return
     */
    @PostMapping("/registerByH5")
    public AjaxResult register(@Valid @RequestBody RegisterUserTo user, String code) {
        return AjaxResult.success(userService.registerByH5(user, code));
    }
//
//    /**
//     * 获取部门树列表
//     */
//    @PreAuthorize("@ss.hasPermi('system:user:list')")
//    @GetMapping("/deptTree")
//    public AjaxResult deptTree(SysDept dept)
//    {
//        return success(deptService.selectDeptTreeList(dept));
//    }
}
