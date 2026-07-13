package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.common.tenant.TenantContext;
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

    @Override
    public List<SysTenant> selectAvailableTenantList() {
        return tenantMapper.selectAvailableTenantList();
    }

    @Override
    public SysTenant selectByTenantId(String tenantId) {
        return tenantMapper.selectByTenantId(tenantId);
    }

    @Override
    public boolean checkTenantAvailable(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return false;
        }
        return tenantMapper.checkTenantAvailable(tenantId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertTenant(SysTenant tenant) {
        if (tenant.getTenantId() == null || tenant.getTenantId().isEmpty()) {
            tenant.setTenantId(UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase());
        }
        tenant.setCreateTime(new Date());
        tenant.setUpdateTime(new Date());
        if (tenant.getStatus() == null) {
            tenant.setStatus("0");
        }
        return TenantContext.ignoreTenant(() -> save(tenant));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTenant(SysTenant tenant) {
        // 如果 id 为 null 但 tenantId 有值，通过 tenantId 查出已有记录获取 id
        if (tenant.getId() == null && tenant.getTenantId() != null && !tenant.getTenantId().isEmpty()) {
            SysTenant existing = TenantContext.ignoreTenant(() -> selectByTenantId(tenant.getTenantId()));
            if (existing != null) {
                tenant.setId(existing.getId());
            }
        }
        tenant.setUpdateTime(new Date());
        return TenantContext.ignoreTenant(() -> updateById(tenant));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTenantById(Long id) {
        return TenantContext.ignoreTenant(() -> removeById(id));
    }
}
