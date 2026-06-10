package cn.poile.ucs.auth.controller;

import cn.poile.ucs.auth.service.AuthService;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.common.tenant.TenantContext;
import com.yzx.model.AjaxResult;
import com.yzx.model.LoginRequest;
import com.yzx.model.constant.Constants;
import com.yzx.model.enums.AuthCode;
import com.yzx.model.exception.CustomException;
import com.yzx.model.exception.ExceptionCast;
import com.yzx.model.system.SysTenant;
import com.yzx.model.ucenter.ext.AuthToken;
import com.yzx.model.utils.Oauth2Util;
import com.yzx.model.utils.ServletUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @className: BaseUserDetails
 * @author: yzx
 * @date: 2025/8/21 6:24
 * @Version: 1.0
 * @description:
 */
@RestController
@Log4j2
@CrossOrigin
@RequestMapping("auth")
public class AuthenticationController {

    @Autowired
    SystemApi systemApi;
    @Autowired
    private AuthService authService;
    @Autowired
    private Oauth2Util oauth2Util;
    @Autowired
    private cn.poile.ucs.auth.mapper.SysTenantMapper sysTenantMapper;

    /**
     * 获取可用租户列表（供登录选择）
     * 登录前接口，允许匿名访问
     */
    @GetMapping("/tenant/list")
    public AjaxResult getTenantList() {
        log.info("开始获取租户列表...");
        try {
            // 使用本地 Mapper 查询，避免 Feign 调用问题
            List<SysTenant> tenantList = sysTenantMapper.selectAvailableTenantList();
            log.info("从数据库查询到租户列表，数量: {}", tenantList != null ? tenantList.size() : 0);
            if (tenantList != null && !tenantList.isEmpty()) {
                return AjaxResult.success(tenantList);
            } else {
                log.warn("数据库中租户列表为空");
            }
        } catch (Exception e) {
            log.error("查询租户列表失败: {}", e.getMessage(), e);
        }

        // 如果查询失败或返回空，返回默认租户
        log.info("返回默认租户");
        return AjaxResult.success(java.util.Arrays.asList(createDefaultTenant()));
    }

    /**
     * 验证租户是否可用
     * 登录前接口，允许匿名访问
     */
    @GetMapping("/tenant/check/{tenantId}")
    public AjaxResult checkTenant(@PathVariable String tenantId) {
        try {
            // 使用本地 Mapper 查询
            int count = sysTenantMapper.checkTenantAvailable(tenantId);
            boolean available = count > 0;
            log.info("检查租户 {} 可用性: {}", tenantId, available);
            return AjaxResult.success(available);
        } catch (Exception e) {
            log.error("检查租户可用性失败: {}", e.getMessage());
            // 如果检查失败，只允许默认租户
            return AjaxResult.success("000000".equals(tenantId));
        }
    }

    /**
     * 创建默认租户信息
     */
    private SysTenant createDefaultTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("000000");
        tenant.setTenantName("默认租户");
        tenant.setStatus("0");
        tenant.setIsDefault("1");
        return tenant;
    }

    @PostMapping("/user/login")
    public AjaxResult login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        System.out.println(loginRequest);
        if (loginRequest == null) {
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_ERROR);
        }

        // 先进行认证，获取用户信息以判断是否为超级管理员
        String tenantId = loginRequest.getTenantId();

        // 验证租户（如果不是超级管理员）
        if (StringUtils.isNotEmpty(tenantId)) {
            // 检查租户是否存在且可用（使用本地 Mapper）
            log.info("检查租户可用性，tenantId: {}", tenantId);
            int count = sysTenantMapper.checkTenantAvailable(tenantId);
            boolean tenantAvailable = count > 0;
            log.info("租户 {} 可用性: {} (count: {})", tenantId, tenantAvailable, count);
            if (!tenantAvailable) {
                ExceptionCast.cast(AuthCode.AUTH_TENANT_NOT_AVAILABLE);
            }
        }

        try {
            LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", loginRequest.getClientId());
            // 传递租户ID到认证服务（如果选择了租户）
            if (StringUtils.isNotEmpty(tenantId)) {
                body.add("tenant_id", tenantId);
            }

            switch (loginRequest.getGrantType()) {
                case Constants.LOGIN_TYPE_PWD:
                    if (StringUtils.isEmpty(loginRequest.getMobile())) {
                        ExceptionCast.cast(AuthCode.AUTH_MOBILE_NONE);
                    }
                    if (StringUtils.isEmpty(loginRequest.getPassword())) {
                        ExceptionCast.cast(AuthCode.AUTH_PASSWORD_NONE);
                    }
                    body.add("mobile", loginRequest.getMobile());
                    body.add("password", loginRequest.getPassword());
                    break;
                case Constants.LOGIN_TYPE_PASSWORD:
                    if (StringUtils.isEmpty(loginRequest.getUsername())) {
                        ExceptionCast.cast(AuthCode.AUTH_USERNAME_NONE);
                    }
                    if (StringUtils.isEmpty(loginRequest.getPassword())) {
                        ExceptionCast.cast(AuthCode.AUTH_PASSWORD_NONE);
                    }
                    body.add("username", loginRequest.getUsername());
                    body.add("password", loginRequest.getPassword());
                    break;
                case Constants.LOGIN_TYPE_SMS:
                    if (StringUtils.isEmpty(loginRequest.getMobile())) {
                        ExceptionCast.cast(AuthCode.AUTH_MOBILE_NONE);
                    }
                    if (StringUtils.isEmpty(loginRequest.getVerifyCode())) {
                        ExceptionCast.cast(AuthCode.AUTH_VERIFYCODE_NONE);
                    }
                    body.add("mobile", loginRequest.getMobile());
                    body.add("verifyCode", loginRequest.getVerifyCode());
                    break;
                default:
                    ExceptionCast.cast(AuthCode.AUTH_LOGIN_ERROR);
            }
            //body还可以加上需要携带的数据过去，比如菜单列表，menu列表，用于页面权限校验，或者也可以拿着身份令牌从别的接口中获取菜单列表，按钮列表等
            body.add("grant_type", loginRequest.getGrantType());
            //申请令牌
            AuthToken authToken = authService.login(body, request);
            AjaxResult result = new AjaxResult();
            result.put("code", 200);
            result.put("message", "操作成功");
            result.put("token", authToken);
            result.put("tenantId", tenantId);
            return result;
        } finally {
            // 清理租户上下文
            TenantContext.clear();
        }
    }

    /**
     * token刷新
     * @param client_id
     * @param refresh_token
     * @param grant_type
     * @return
     */
    @PostMapping("refresh")
    public AjaxResult refreshToken(@RequestParam String client_id, @RequestParam String refresh_token, @RequestParam String grant_type) {
        //1.校验刷新令牌
        if (org.springframework.util.StringUtils.isEmpty(refresh_token)) {
            return AjaxResult.error("令牌不能为空");
        }
        try {
            LinkedMultiValueMap<String, String> param = new LinkedMultiValueMap<>();
            param.add("client_id", client_id);
            param.add("grant_type", grant_type);
            param.add("refresh_token", refresh_token);
            AuthToken authToken = authService.refreshToken(param);
            return AjaxResult.success("令牌刷新成功", authToken);
        } catch (CustomException e) {
            log.error("令牌刷新失败", e);
            return AjaxResult.error("令牌刷新失败");
        } catch (Exception e) {
            log.error("令牌刷新失败", e);
            return AjaxResult.error("令牌刷新失败");
        }
    }

    /**
     *
     * @param request
     * @return
     */
    @GetMapping("/user/getInfo")
    public AjaxResult getInfo(HttpServletRequest request) {
        String bear = request.getHeader("Authorization");
        System.out.println(bear);
        Oauth2Util.UserJwt userJwt = oauth2Util.getUserJwtFromHeader(request);

        if (Objects.isNull(userJwt)) {
            return AjaxResult.error("用户未登录");
        }
        // 角色集合
        AjaxResult roles = systemApi.getRolePermissionByUserId(userJwt.getId());
        // 权限集合
        AjaxResult permissions = systemApi.getMenuPermissionByUserId(userJwt.getId());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", userJwt);
        ajax.put("roles", roles.get("data"));
        ajax.put("permissions", permissions.get("data"));
        return ajax;
    }


    @GetMapping("/user/getRouters")
    public AjaxResult getRouters(HttpServletRequest request) {
        Oauth2Util.UserJwt userJwt = oauth2Util.getUserJwtFromHeader(request);
        Long id = userJwt.getId();
        return systemApi.getMenusTreeByUserId(id);
    }

}
