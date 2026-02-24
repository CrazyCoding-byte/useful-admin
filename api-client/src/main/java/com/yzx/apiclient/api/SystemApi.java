package com.yzx.apiclient.api;

import com.yzx.model.AjaxResult;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;

/**
 * @className: SystemApi
 * @author: yzx
 * @date: 2025/8/21 6:21
 * @Version: 1.0
 * @description:
 */
@FeignClient("system-server")
public interface SystemApi {
    @GetMapping("/system/menu/getPermsByUserId/{userId}")
    AjaxResult selectPermsByUserId(@PathVariable(name = "userId") Long userId);

    /**
     * 获取角色数据权限
     *
     * @param userId 用户ID
     * @return 角色权限信息
     */
    @GetMapping("/system/permission/getRolePermissionByUserId/{userId}")
    AjaxResult getRolePermissionByUserId(@PathVariable(name = "userId") Long userId);

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户ID
     * @return 菜单权限信息
     */
    @GetMapping("/system/permission/getMenuPermissionByUserId/{userId}")
    AjaxResult getMenuPermissionByUserId(@PathVariable(name = "userId") Long userId);

    /**
     * 获取菜单树
     * @param userId 用户ID
     * @return 菜单树
     */
    @GetMapping("/system/menu/getMenusTreeByUserId/{userId}")
    public AjaxResult getMenusTreeByUserId(@PathVariable(name = "userId") Long userId);

    @GetMapping("/system/getUserInfo/{userId}")
    public AjaxResult getUserInfo(@PathVariable(name = "userId") String userId);

    @GetMapping("/system/getUserInfoByQrCode/{code}")
    public AjaxResult getUserInfoByQrCode(@PathVariable String code);
}
