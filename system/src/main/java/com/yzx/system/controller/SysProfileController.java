package com.yzx.system.controller;


import com.yzx.model.AjaxResult;
import com.yzx.model.ucenter.BaseUser;
import com.yzx.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.token.TokenService;
import org.springframework.web.bind.annotation.*;
import com.yzx.model.system.SysUser;

/**
 * 个人信息 业务处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController {
    @Autowired
    private ISysUserService userService;
//
//    @Autowired
//    private TokenService tokenService;

    /**
     * 个人信息
     */
    @GetMapping
    public AjaxResult profile()
    {
        // 这里暂时返回空，实际应该从 SecurityContext 中获取当前用户
        BaseUser loginUser = new BaseUser();
        loginUser.setUserName("admin");
        loginUser.setUserId(1L); // 临时设置用户ID
        AjaxResult ajax = AjaxResult.success(loginUser);
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUserId()));
        return ajax;
    }

//    /**
//     * 修改用户
//     */
//    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
//    @PutMapping
//    public AjaxResult updateProfile(@RequestBody SysUser user)
//    {
//        LoginUser loginUser = getLoginUser();
//        SysUser currentUser = loginUser.getUser();
//        currentUser.setNickName(user.getNickName());
//        currentUser.setEmail(user.getEmail());
//        currentUser.setPhonenumber(user.getPhonenumber());
//        currentUser.setSex(user.getSex());
//        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
//        {
//            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
//        }
//        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(currentUser))
//        {
//            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
//        }
//        if (userService.updateUserProfile(currentUser) > 0)
//        {
//            // 更新缓存用户信息
//            tokenService.setLoginUser(loginUser);
//            return success();
//        }
//        return error("修改个人信息异常，请联系管理员");
//    }

//    /**
//     * 重置密码
//     */
//    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
//    @PutMapping("/updatePwd")
//    public AjaxResult updatePwd(@RequestBody Map<String, String> params)
//    {
//        String oldPassword = params.get("oldPassword");
//        String newPassword = params.get("newPassword");
//        LoginUser loginUser = getLoginUser();
//        String userName = loginUser.getUsername();
//        String password = loginUser.getPassword();
//        if (!SecurityUtils.matchesPassword(oldPassword, password))
//        {
//            return error("修改密码失败，旧密码错误");
//        }
//        if (SecurityUtils.matchesPassword(newPassword, password))
//        {
//            return error("新密码不能与旧密码相同");
//        }
//        newPassword = SecurityUtils.encryptPassword(newPassword);
//        if (userService.resetUserPwd(userName, newPassword) > 0)
//        {
//            // 更新缓存用户密码
//            loginUser.getUser().setPassword(newPassword);
//            tokenService.setLoginUser(loginUser);
//            return success();
//        }
//        return error("修改密码异常，请联系管理员");
//    }

//    /**
//     * 头像上传
//     */
//    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
//    @PostMapping("/avatar")
//    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file) throws Exception
//    {
//        if (!file.isEmpty())
//        {
//            LoginUser loginUser = getLoginUser();
//            String avatar = FileUploadUtils.upload(RuoYiConfig.getAvatarPath(), file, MimeTypeUtils.IMAGE_EXTENSION);
//            if (userService.updateUserAvatar(loginUser.getUsername(), avatar))
//            {
//                AjaxResult ajax = AjaxResult.success();
//                ajax.put("imgUrl", avatar);
//                // 更新缓存用户头像
//                loginUser.getUser().setAvatar(avatar);
//                tokenService.setLoginUser(loginUser);
//                return ajax;
//            }
//        }
//        return error("上传图片异常，请联系管理员");
//    }
}
