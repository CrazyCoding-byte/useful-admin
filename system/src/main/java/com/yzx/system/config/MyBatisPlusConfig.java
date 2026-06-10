package com.yzx.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yzx.common.permission.aspect.DataPermissionPointcutAdvisor;
import com.yzx.system.interceptor.PlusDataPermissionInterceptor;
import com.yzx.system.tenant.CustomTenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.util.List;

/**
 * MyBatis-Plus配置类
 *
 * @author ruoyi
 */
@Configuration
@MapperScan({"com.yzx.system.mapper", "com.yzx.common.mapper"})
public class MyBatisPlusConfig {

    @Value("${tenant.enable:true}")
    private Boolean tenantEnable;

    @Value("${tenant.excludes:}")
    private List<String> tenantExcludes;

    /**
     * 配置MyBatis-Plus拦截器
     * 注意顺序：
     * 1. 多租户拦截器（修改JOIN条件）
     * 2. 数据权限拦截器（修改WHERE条件）
     * 3. 分页拦截器（最后执行）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        if (tenantEnable) {
            // 1. 先添加租户插件（先执行，修改JOIN的ON条件）
            CustomTenantLineHandler tenantLineHandler = new CustomTenantLineHandler(tenantExcludes);
            TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
            tenantInterceptor.setTenantLineHandler(tenantLineHandler);
            interceptor.addInnerInterceptor(tenantInterceptor);
        }

        // 2. 添加数据权限拦截器（后执行，修改WHERE条件）
        interceptor.addInnerInterceptor(dataPermissionInterceptor());

        // 3. 添加分页插件（最后执行）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        paginationInterceptor.setDbType(DbType.MYSQL);
        paginationInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

    @Bean
    public PlusDataPermissionInterceptor dataPermissionInterceptor() {
        PlusDataPermissionInterceptor interceptor = new PlusDataPermissionInterceptor();
        // Handler 直接用 @Autowired 注入
        return interceptor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DataPermissionPointcutAdvisor dataPermissionPointcutAdvisor() {
        return new DataPermissionPointcutAdvisor();
    }
}
