package com.yzx.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.yzx.system.tenant.CustomTenantLineHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis-Plus配置类
 * 
 * @author ruoyi
 */
@Configuration
public class MyBatisPlusConfig {
    
    @Value("${tenant.enable:true}")
    private Boolean tenantEnable;
    
    @Value("${tenant.excludes:}")
    private List<String> tenantExcludes;
    
    /**
     * 配置MyBatis-Plus拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        if (tenantEnable) {
            // 添加租户插件
            TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
            tenantInterceptor.setTenantLineHandler(new CustomTenantLineHandler(tenantExcludes));
            interceptor.addInnerInterceptor(tenantInterceptor);
        }
        
        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        paginationInterceptor.setDbType(DbType.MYSQL);
        paginationInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        return interceptor;
    }
}
