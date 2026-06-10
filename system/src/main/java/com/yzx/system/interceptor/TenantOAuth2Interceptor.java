package com.yzx.system.interceptor;

import com.yzx.common.tenant.TenantContext;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 租户 OAuth2 拦截器
 * 从 SecurityContext 中提取当前用户的 tenantId 并设置到 TenantContext
 *
 * @author ruoyi
 */
@Slf4j
@Component
public class TenantOAuth2Interceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 尝试从 SecurityContext 获取当前用户信息
            BaseUserDetail userDetail = SecurityUtils.getBaseUserDetail();

            if (userDetail != null && userDetail.getBaseUser() != null) {
                Long userId = userDetail.getBaseUser().getUserId();
                // 检查是否为超级管理员（userId = 1）
                boolean isSuperAdmin = userId != null && userId == 1;

                if (isSuperAdmin) {
                    // 超级管理员可以跨租户访问
                    // 检查请求头中是否有指定的租户ID（用于切换租户视图）
                    String targetTenantId = request.getHeader("X-Tenant-Id");
                    if (targetTenantId != null && !targetTenantId.isEmpty()) {
                        TenantContext.setCurrentTenantId(targetTenantId);
                        log.info("超级管理员 [{}] 切换到租户ID: {}, URI: {}", userId, targetTenantId, request.getRequestURI());
                    } else {
                        // 不设置租户上下文，超级管理员可以看到所有租户数据
                        log.info("超级管理员 [{}] 访问所有租户数据，URI: {}", userId, request.getRequestURI());
                    }
                } else {
                    // 普通用户只能访问自己租户的数据
                    String tenantId = userDetail.getBaseUser().getTenantId();
                    if (tenantId != null && !tenantId.isEmpty()) {
                        TenantContext.setCurrentTenantId(tenantId);
                        log.debug("用户 [{}] 访问租户ID: {}, URI: {}", userId, tenantId, request.getRequestURI());
                    } else {
                        log.warn("用户 [{}] 的租户ID为空，URI: {}", userId, request.getRequestURI());
                    }
                }
            } else {
                log.debug("未检测到 OAuth2 用户信息，URI: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("设置租户上下文失败，URI: {}", request.getRequestURI(), e);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理租户上下文
        TenantContext.clear();
        log.debug("清理租户上下文，URI: {}", request.getRequestURI());
    }
}
