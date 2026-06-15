package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.common.tenant.TenantContext;
import com.yzx.model.ResultCode;
import com.yzx.model.enums.AuthCode;
import com.yzx.model.exception.CustomException;
import com.yzx.model.system.SysTenant;
import com.yzx.system.mapper.SysTenantMapper;
import com.yzx.system.service.ISysTenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 租户Service业务层处理
 */
@Slf4j
@Service
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements ISysTenantService {

    @Autowired
    private SysTenantMapper tenantMapper;

    /**
     * 查询所有可用租户列表（供登录选择）
     */
    @Override
    public List<SysTenant> selectAvailableTenantList() {
        return tenantMapper.selectAvailableTenantList();
    }

    /**
     * 根据租户ID查询租户
     */
    @Override
    public SysTenant selectByTenantId(String tenantId) {
        return tenantMapper.selectByTenantId(tenantId);
    }

    /**
     * 检查租户是否存在且可用
     */
    @Override
    public boolean checkTenantAvailable(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return false;
        }
        return tenantMapper.checkTenantAvailable(tenantId) > 0;
    }

    /**
     * 校验租户是否允许操作（系统内置租户不允许删除）
     */
    @Override
    public void checkTenantAllowed(String tenantId) {
        SysTenant tenant = selectByTenantId(tenantId);
        if (tenant != null && tenant.isDefaultTenant()) {
            throw new CustomException(AuthCode.AUTH_TENANT_NO_ALLOWD_OPERATION);
        }
    }

    /**
     * 新增租户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertTenant(SysTenant tenant) {
        // 生成租户ID（使用UUID前8位）
        if (tenant.getTenantId() == null || tenant.getTenantId().isEmpty()) {
            tenant.setTenantId(UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase());
        }
        tenant.setCreateTime(new Date());
        tenant.setUpdateTime(new Date());
        // 默认状态为正常
        if (tenant.getStatus() == null) {
            tenant.setStatus("0");
        }
        // 默认非系统租户
        if (tenant.getIsDefault() == null) {
            tenant.setIsDefault("0");
        }

        // 在忽略租户上下文的情况下保存（系统级操作）
        return TenantContext.ignoreTenant(() -> save(tenant));
    }

    /**
     * 修改租户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTenant(SysTenant tenant) {
        // 系统内置租户不允许修改
        checkTenantAllowed(tenant.getTenantId());
        tenant.setUpdateTime(new Date());
        return TenantContext.ignoreTenant(() -> updateById(tenant));
    }

    /**
     * 删除租户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTenantById(String tenantId) {
        // 系统内置租户不允许删除
        checkTenantAllowed(tenantId);
        return TenantContext.ignoreTenant(() -> removeById(tenantId));
    }
}
