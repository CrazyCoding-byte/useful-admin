package com.yzx.system.config;

import com.alibaba.fastjson.JSON;
import com.yzx.model.AjaxResult;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 自定义：处理 Token 过期、无效、未登录 401 异常
 * 替换 OAuth2 默认异常返回格式
 */
@Component
public class CustomAuthExceptionEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 统一返回你的项目格式 AjaxResult
        AjaxResult result = AjaxResult.error(401, "令牌已过期或无效，请重新登录");
        response.getWriter().write(JSON.toJSONString(result));
    }
}