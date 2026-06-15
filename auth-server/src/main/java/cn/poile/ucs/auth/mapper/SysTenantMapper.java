package cn.poile.ucs.auth.mapper;

import com.yzx.model.system.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户表 Mapper（auth-server 本地查询）
 * 避免通过 Feign 调用 system-server
 */
@Mapper
public interface SysTenantMapper {

    /**
     * 查询所有可用租户列表（供登录选择）
     */
    @Select("SELECT tenant_id, tenant_name, logo FROM sys_tenant WHERE status = '0' AND (expire_time IS NULL OR expire_time > NOW()) ORDER BY create_time DESC")
    List<SysTenant> selectAvailableTenantList();

    /**
     * 检查租户是否可用
     */
    @Select("SELECT COUNT(*) FROM sys_tenant WHERE tenant_id = #{tenantId} AND status = '0' AND (expire_time IS NULL OR expire_time > NOW())")
    int checkTenantAvailable(@Param("tenantId") String tenantId);
}
