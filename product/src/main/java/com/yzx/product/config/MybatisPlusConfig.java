package com.yzx.product.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * MyBatis-Plus 配置类
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 添加分页插件
     * 注意：如果项目中同时使用了 PageHelper，可能会有冲突
     * 解决方案：1. 移除 PageHelper 依赖；2. 或配置 PageHelper 不拦截 MyBatis-Plus 的查询
     */
    @Bean
    @Order(0)  // 确保优先级最高
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置请求的页面大于最大页后操作，true 调回到首页，false 继续请求，默认 false
        paginationInterceptor.setOverflow(false);
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.setMaxLimit(500L);
        // 开启 count 的 join 优化，只针对部分 left join
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
