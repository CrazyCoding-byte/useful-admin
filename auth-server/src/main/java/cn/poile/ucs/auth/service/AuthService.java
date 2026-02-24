package cn.poile.ucs.auth.service;

import cn.poile.ucs.auth.mapper.OauthClientDetailsMapper;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.api.R;
import com.yzx.model.constant.Constants;
import com.yzx.model.enums.AuthCode;
import com.yzx.model.exception.CustomException;
import com.yzx.model.exception.ExceptionCast;
import com.yzx.model.ucenter.OauthClientDetails;
import com.yzx.model.ucenter.ext.AuthToken;
import com.yzx.model.utils.UserAgentUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.bouncycastle.cms.RecipientId.password;

/**
 * @className: AuthService
 * @author: yzx
 * @date: 2025/9/6 7:23
 * @Version: 1.0
 * @description:
 */
@Service
@Slf4j
public class AuthService {
    @Autowired
    private OauthClientDetailsMapper oauthClientDetailsMapper;
    @Value("${auth.tokenValiditySecondsMobile}")
    int tokenValiditySecondsMobile;
    @Value("${auth.smsCodeTime}")
    private long smsCodeTime;
    @Value("${auth.tokenValiditySecondsComputer}")
    int tokenValiditySecondsComputer;
    @Autowired
    private AuthorizationServerTokenServices tokenServices;
    @Autowired
    private ClientDetailsService clientDetailsService;
    static final String DEVICE_TYPE_MOBILE = "MOBILE";

    static final String DEVICE_TYPE_COMPUTER = "COMPUTER";
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public AuthToken login(LinkedMultiValueMap<String, String> body, HttpServletRequest request) {
        // 打印请求参数（原有逻辑保留）
        for (Map.Entry<String, List<String>> entry : body.entrySet()) {
            String mapKey = entry.getKey();
            List<String> list = entry.getValue();
            String value = list.get(0);
            log.info("请求参数：{}：value：{}", mapKey, value);
        }

        AuthToken authToken = null;
        try {
            // 调用applyToken（此时异常会直接抛到这里）
            authToken = this.applyToken(body);
        } catch (CustomException e) {
            // 捕获所有自定义异常（账号/密码/刷新令牌等），直接向上抛
            throw e;
        } catch (Exception e) {
            // 捕获非自定义的“令牌生成失败”异常 → 抛23008
            log.error("申请令牌失败（非账号密码错误）", e);
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_APPLYTOKEN_FAIL); // 23008
        }

        // 存储到redis（原有逻辑保留）
        String jsonString = JSON.toJSONString(authToken);
        boolean result = false;
        if (DEVICE_TYPE_MOBILE.equals(UserAgentUtils.getDevicetype(request))) {
            result = this.saveToken(authToken.getAccessToken(), jsonString, tokenValiditySecondsMobile);
        } else {
            result = this.saveToken(authToken.getAccessToken(), jsonString, tokenValiditySecondsComputer);
        }
        if (!result) {
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_TOKEN_SAVEFAIL); // 23009
        }
        return authToken;
    }

    /**
     * 申请令牌
     * @param body 请求体
     * @return AuthToken
     */
    private AuthToken applyToken(LinkedMultiValueMap<String, String> body) {
        // 移除外层大try-catch，让异常直接向上抛（仅保留必要的异常分类处理）
        // 解析请求参数
        String clientId = body.getFirst("client_id");
        String grantType = body.getFirst("grant_type");
        String password = body.getFirst("password");
        String username = body.getFirst("username");
        String refreshToken = body.getFirst("refresh_token");

        // 校验核心参数
        if (StringUtils.isEmpty(clientId) || StringUtils.isEmpty(grantType)) {
            log.error("缺少核心参数：client_id={} 或 grant_type={}", clientId, grantType);
            return null;
        }

        // 加载客户端信息
        ClientDetails clientDetails = clientDetailsService.loadClientByClientId(clientId);
        if (clientDetails == null) {
            log.error("客户端不存在：{}", clientId);
            return null;
        }

        // 构建令牌请求
        TokenRequest tokenRequest = new TokenRequest(Collections.emptyMap(), clientId, clientDetails.getScope(), grantType);
        OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(clientDetails);
        Authentication userAuthentication = null;
        OAuth2AccessToken accessToken = null;

        if ("password".equals(grantType)) {
            // 密码模式：校验账号密码（移除嵌套try-catch，异常直接向外抛）
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    username, password, Collections.emptyList()
            );
            try {
                // 调用认证管理器校验用户（触发UserDetailsService逻辑）
                userAuthentication = authenticationManager.authenticate(authToken);
            } catch (UsernameNotFoundException e) {
                // 捕获“账号不存在”→ 直接抛自定义异常（无外层catch拦截，会到login方法）
                log.error("账号不存在：{}", username, e);
                ExceptionCast.cast(AuthCode.AUTH_ACCOUNT_NOTEXISTS); // 23001
            } catch (BadCredentialsException e) {
                // 捕获“密码错误”→ 直接抛自定义异常
                log.error("密码错误：账号={}", username, e);
                ExceptionCast.cast(AuthCode.AUTH_CREDENTIAL_ERROR); // 23002
            } catch (Exception e) {
                // 其他认证相关异常（如用户锁定、过期等）
                log.error("用户认证失败：账号={}", username, e);
                ExceptionCast.cast(AuthCode.AUTH_LOGIN_ERROR); // 可新增23003：认证失败
            }

            // 账号密码校验通过，生成令牌
            OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(oAuth2Request, userAuthentication);
            accessToken = tokenServices.createAccessToken(oAuth2Authentication);
        } else if ("refresh_token".equals(grantType)) {
            // refresh_token模式：原有逻辑保留，异常直接抛
            if (refreshToken == null) {
                log.error("刷新令牌模式缺少参数：refresh_token");
                ExceptionCast.cast(AuthCode.AUTH_REFRESH_TOKEN_NOT_EXIST); // 可新增23004
            }
            try {
                accessToken = tokenServices.refreshAccessToken(refreshToken, tokenRequest);
            } catch (Exception e) {
                log.error("刷新令牌失败：token={}", refreshToken, e);
                ExceptionCast.cast(AuthCode.AUTH_REFRESH_TOKEN_ERROR); // 可新增23005
            }
        } else {
            log.error("不支持的授权模式：{}", grantType);
            ExceptionCast.cast(AuthCode.AUTH_GRANT_TYPE_ERROR); // 可新增23006
        }

        // 封装返回AuthToken
        AuthToken authToken1 = new AuthToken();
        authToken1.setAccessToken(accessToken.getValue());
        authToken1.setRefreshToken(accessToken.getRefreshToken().getValue());
        authToken1.setJwtToken(UUID.randomUUID().toString());
        log.info("内部生成令牌成功：{}", authToken1);
        return authToken1;
    }


    /**
     *存储到令牌到redis
     * @param accessToken 用户身份令牌
     * @param content  内容就是AuthToken对象的内容
     * @param ttl 过期时间
     * @return boolean
     */
    private boolean saveToken(String accessToken, String content, long ttl) {
        String key = Constants.USER_TOKEN + accessToken;
        redisTemplate.boundValueOps(key).set(content, ttl, TimeUnit.SECONDS);
        redisTemplate.boundValueOps(key);
        return redisTemplate.getExpire(key, TimeUnit.SECONDS) > 0;
    }

    /**删除token*/
    public boolean delToken(String accessToken) {
        String key = Constants.USER_TOKEN + accessToken;
        redisTemplate.delete(key);
        return true;
    }

    /**从redis查询令牌*/
    public AuthToken getUserToken(String token) {
        String key = Constants.USER_TOKEN + token;
        //从redis中取到令牌信息
        String value = redisTemplate.opsForValue().get(key);
        //转成对象
        try {
            return JSON.parseObject(value, AuthToken.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取短信验证码
     * @param phone 手机号
     * @return Boolean
     */
//    public boolean getSmsCode(String phone) {
//        int code =  (int)((Math.random()*9+1)*100000);
//       redisTemplate.opsForValue().set(Constants.SMS_CODE +phone,code+"",smsCodeTime,TimeUnit.MINUTES);
//        CommonResponse response = new AliYunSmsUtils().sendSms(phone, code + "");
//        String data = response.getData();
//        Map<String,String> map = JSON.parseObject(data, Map.class);
//        System.out.println(map);
//        return "OK".equals(map.get("Code"));
//    }
}
