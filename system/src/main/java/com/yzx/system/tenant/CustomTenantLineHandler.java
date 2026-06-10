package com.yzx.system.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.yzx.common.tenant.TenantContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义租户行级处理器
 *
 * @author ruoyi
 */
@Slf4j
@AllArgsConstructor
public class CustomTenantLineHandler implements TenantLineHandler {

    private final List<String> excludes;

    /**
     * 获取租户ID值
     */
    @Override
    public Expression getTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();

        if (StringUtils.isBlank(tenantId)) {
            // 租户ID为空，可能是超级管理员访问所有租户数据
            log.debug("租户ID为空，跳过租户过滤（可能是超级管理员）");
            return new NullValue();
        }

        return new StringValue(tenantId);
    }

    /**
     * 是否忽略租户过滤
     * 当租户ID为空时，忽略租户过滤（超级管理员场景）
     */
    @Override
    public boolean ignoreTenantLine() {
        String tenantId = TenantContext.getCurrentTenantId();
        // 租户ID为空时，忽略租户行过滤（超级管理员可以查看所有租户数据）
        return StringUtils.isBlank(tenantId);
    }

    /**
     * 判断哪些表不需要租户过滤
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 系统表、字典表等不需要租户过滤
        List<String> defaultExcludes = Arrays.asList(
                "sys_tenant",           // 租户表本身
                "sys_tenant_package",   // 租户套餐表
                "sys_dict_type",        // 字典类型
                "sys_dict_data",        // 字典数据
                "gen_table",            // 代码生成表
                "gen_table_column"      // 代码生成列
        );

        List<String> allExcludes = defaultExcludes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (excludes != null) {
            allExcludes.addAll(excludes.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));
        }

        boolean ignore = allExcludes.contains(tableName.toLowerCase());
        if (ignore) {
            log.debug("表 [{}] 已排除租户过滤", tableName);
        }

        return ignore;
    }
}
