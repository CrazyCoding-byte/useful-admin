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
                String tenantId = userDetail.getBaseUser().getTenantId();

                if (tenantId != null && !tenantId.isEmpty()) {
                    // 设置租户上下文
                    TenantContext.setCurrentTenantId(tenantId);
                    log.debug("从 OAuth2 Token 中提取租户ID: {}, URI: {}", tenantId, request.getRequestURI());
                } else {
                    log.warn("用户 [{}] 的租户ID为空，URI: {}", userDetail.getUsername(), request.getRequestURI());
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
