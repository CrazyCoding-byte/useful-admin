package com.yzx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.system.SysTenant;

import java.util.List;

/**
 * 租户Service接口
 */
public interface ISysTenantService extends IService<SysTenant> {

    /**
     * 查询所有可用租户列表（供登录选择）
     */
    List<SysTenant> selectAvailableTenantList();

    /**
     * 根据租户ID查询租户
     */
    SysTenant selectByTenantId(String tenantId);

    /**
     * 检查租户是否存在且可用
     */
    boolean checkTenantAvailable(String tenantId);

    /**
     * 新增租户
     */
    boolean insertTenant(SysTenant tenant);

    /**
     * 修改租户
     */
    boolean updateTenant(SysTenant tenant);

    /**
     * 删除租户
     */
    boolean deleteTenantById(Long id);
}
