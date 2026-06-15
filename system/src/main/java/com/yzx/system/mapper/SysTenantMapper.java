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

    /**
     * 查询所有可用租户列表（供登录选择）
     */
    @Select("SELECT tenant_id, tenant_name, logo FROM sys_tenant WHERE status = '0' AND (expire_time IS NULL OR expire_time > NOW()) ORDER BY create_time DESC")
    List<SysTenant> selectAvailableTenantList();

    /**
     * 根据租户ID查询租户
     */
    @Select("SELECT * FROM sys_tenant WHERE tenant_id = #{tenantId}")
    SysTenant selectByTenantId(@Param("tenantId") String tenantId);

    /**
     * 检查租户是否存在且可用
     */
    @Select("SELECT COUNT(*) FROM sys_tenant WHERE tenant_id = #{tenantId} AND status = '0' AND (expire_time IS NULL OR expire_time > NOW())")
    int checkTenantAvailable(@Param("tenantId") String tenantId);
}
