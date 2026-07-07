package com.yzx.apiclient.api;

import com.yzx.apiclient.api.vo.RemoteDictDataVo;
import com.yzx.apiclient.api.vo.RemoteDictTypeVo;
import com.yzx.model.AjaxResult;
import com.yzx.model.system.SysTenant;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
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

    /**
     * 获取用户信息
     * @param userId
     * @return
     */
    @GetMapping("/system/getUserInfo/{userId}")
    public AjaxResult getUserInfo(@PathVariable(name = "userId") String userId);

    /**
     * 根据QrCode获取用户信息
     * @param code
     * @return
     */
    @GetMapping("/system/getUserInfoByQrCode/{code}")
    public AjaxResult getUserInfoByQrCode(@PathVariable String code);

    /**
     * 获取可用租户列表（供登录选择）
     */
    @GetMapping("/system/tenant/availableList")
    List<SysTenant> getAvailableTenantList();

    /**
     * 检查租户是否可用
     */
    @GetMapping("/system/tenant/checkAvailable/{tenantId}")
    boolean checkTenantAvailable(@PathVariable("tenantId") String tenantId);

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    @GetMapping("/system/dict/type/getDictTypeByType/{dictType}")
    AjaxResult selectDictTypeByType(@PathVariable("dictType") String dictType);

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @GetMapping("/system/dict/type/getDictDataByType/{dictType}")
    AjaxResult selectDictDataByType(@PathVariable("dictType") String dictType);
}
