package cn.poile.ucs.auth.controller;

import cn.poile.ucs.auth.service.AuthService;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.model.AjaxResult;
import com.yzx.model.LoginRequest;
import com.yzx.model.constant.Constants;
import com.yzx.model.enums.AuthCode;
import com.yzx.model.exception.CustomException;
import com.yzx.model.exception.ExceptionCast;
import com.yzx.model.ucenter.ext.AuthToken;
import com.yzx.model.utils.Oauth2Util;
import com.yzx.model.utils.ServletUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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

    @PostMapping("/user/login")
    public AjaxResult login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        System.out.println(loginRequest);
        if (loginRequest == null) {
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_ERROR);
        }
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", loginRequest.getClientId());
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
        return result;
    }
    /**
     * token刷新
     * @param client_id
     * @param refresh_token
     * @param grant_type
     * @return
     */
    @PostMapping("refresh")
    public AjaxResult refreshToken(@RequestParam String client_id,@RequestParam String refresh_token,@RequestParam String grant_type){
        //1.校验刷新令牌
        if(org.springframework.util.StringUtils.isEmpty(refresh_token)){
            return AjaxResult.error("令牌不能为空");
        }
        try{
            LinkedMultiValueMap<String, String> param  = new LinkedMultiValueMap<>();
            param.add("client_id",client_id);
            param.add("grant_type",grant_type);
            param.add("refresh_token",refresh_token);
            AuthToken authToken=  authService.refreshToken(param);
            return AjaxResult.success("令牌刷新成功",authToken);
        }catch (CustomException e){
            log.error("令牌刷新失败",e);
            return AjaxResult.error("令牌刷新失败");
        }catch (Exception e){
            log.error("令牌刷新失败",e);
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
