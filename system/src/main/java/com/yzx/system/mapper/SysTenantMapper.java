package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysTenant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户表 数据层
 */
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    @Select("SELECT tenant_id, company_name FROM sys_tenant WHERE status = '0' AND (expire_time IS NULL OR expire_time > NOW()) ORDER BY create_time DESC")
    List<SysTenant> selectAvailableTenantList();

    @Select("SELECT * FROM sys_tenant WHERE tenant_id = #{tenantId}")
    SysTenant selectByTenantId(@Param("tenantId") String tenantId);

    @Select("SELECT COUNT(*) FROM sys_tenant WHERE tenant_id = #{tenantId} AND status = '0' AND (expire_time IS NULL OR expire_time > NOW())")
    int checkTenantAvailable(@Param("tenantId") String tenantId);
}
