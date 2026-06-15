package com.yzx.system.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.yzx.common.tenant.TenantContext;
import com.yzx.system.tenant.CustomTenantLineHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

/**
 * 支持超级管理员的租户拦截器
 * 当租户ID为空时（超级管理员），跳过租户过滤
 */
@Slf4j
public class SuperAdminTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    private final CustomTenantLineHandler tenantLineHandler;

    public SuperAdminTenantLineInnerInterceptor(CustomTenantLineHandler tenantLineHandler) {
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 检查是否需要跳过租户过滤
        String tenantId = TenantContext.getCurrentTenantId();
        if (StringUtils.isBlank(tenantId)) {
            // 租户ID为空，说明是超级管理员，跳过租户过滤
            log.debug("超级管理员模式，跳过租户拦截");
            return;
        }
        // 调用父类方法执行租户过滤
        super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // 检查是否需要跳过租户过滤
        String tenantId = TenantContext.getCurrentTenantId();
        if (StringUtils.isBlank(tenantId)) {
            // 租户ID为空，说明是超级管理员，跳过租户过滤
            log.debug("超级管理员模式，跳过SELECT租户处理");
            return;
        }
        // 调用父类方法处理SELECT
        super.processSelect(select, index, sql, obj);
    }
}
