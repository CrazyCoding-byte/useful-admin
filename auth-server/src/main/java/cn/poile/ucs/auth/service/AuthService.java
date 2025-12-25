package cn.poile.ucs.auth.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.api.R;
import com.yzx.model.constant.Constants;
import com.yzx.model.enums.AuthCode;
import com.yzx.model.exception.ExceptionCast;
import com.yzx.model.ucenter.ext.AuthToken;
import com.yzx.model.utils.UserAgentUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private RestTemplate restTemplate;
    @Autowired
    LoadBalancerClient loadBalancerClient;
    @Value("${auth.clientId}")
    String clientId;

    @Value("${auth.clientSecret}")
    String clientSecret;

    @Value("${auth.tokenValiditySecondsMobile}")
    int tokenValiditySecondsMobile;

    @Value("${auth.smsCodeTime}")
    private long smsCodeTime;

    @Value("${auth.tokenValiditySecondsComputer}")
    int tokenValiditySecondsComputer;

    static final String DEVICE_TYPE_MOBILE = "MOBILE";

    static final String DEVICE_TYPE_COMPUTER = "COMPUTER";

    @Autowired
    private StringRedisTemplate redisTemplate;

    public AuthToken login(LinkedMultiValueMap<String, String> body, HttpServletRequest request) {
        for (Map.Entry<String, List<String>> entry : body.entrySet()) {
            String mapKey = entry.getKey();
            List<String> list = entry.getValue();
            String value = list.get(0);
            log.info("请求参数：{}：value：{}", mapKey, value);
        }
        //请求spring security申请令牌
        AuthToken authToken = this.applyToken(body);
        if (authToken == null) {
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_APPLYTOKEN_FAIL);
        }
        //存储到redis中的内容
        String jsonString = JSON.toJSONString(authToken);
        //将令牌存储到redis
        boolean result = false;
        //是否为移动端用户
        if (DEVICE_TYPE_MOBILE.equals(UserAgentUtils.getDevicetype(request))) {
            result = this.saveToken(authToken.getAccessToken(), jsonString, tokenValiditySecondsMobile);
        } else {
            result = this.saveToken(authToken.getAccessToken(), jsonString, tokenValiditySecondsComputer);
        }
        if (!result) {
            ExceptionCast.cast(AuthCode.AUTH_LOGIN_TOKEN_SAVEFAIL);
        }
        return authToken;
    }

    /**
     * 申请令牌
     * @param body 请求体
     * @return AuthToken
     */
    private AuthToken applyToken(LinkedMultiValueMap<String, String> body) {
        //从eureka中获取认证服务的地址（因为spring security在认证服务中）
        //从eureka中获取认证服务的一个实例的地址
        ServiceInstance serviceInstance = loadBalancerClient.choose("auth-server");
        //此处是模拟请求
        //此地址就是http://ip:port
//        URI uri = serviceInstance.getUri();
        //令牌申请的地址 http://localhost:40400/auth/oauth/token
        String authUrl = "http://localhost:8001/oauth/token";
        System.out.println(authUrl);
        //定义header
        LinkedMultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        List<String> client_id = body.get("client_id");
        String httpBasic = getHttpBasic();
        header.add("Authorization", httpBasic);

        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(body, header);
        //String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables

        //设置restTemplate远程调用时候，对400和401不让报错，正确返回数据
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                if (response.getRawStatusCode() != 400 && response.getRawStatusCode() != 401) {
                    super.handleError(response);
                }
            }
        });
        ResponseEntity<Map> exchange = restTemplate.exchange(authUrl, HttpMethod.POST, httpEntity, Map.class);
        log.info("exchange:{}", exchange.getBody());
        //申请令牌信息
        Map bodyMap = exchange.getBody();
        if (bodyMap == null ||
                bodyMap.get("access_token") == null ||
                bodyMap.get("refresh_token") == null ||
                bodyMap.get("jti") == null) {
            return null;
        }
        AuthToken authToken = new AuthToken();
        //用户身份令牌
        authToken.setAccessToken((String) bodyMap.get("access_token"));
        //刷新令牌
        authToken.setRefreshToken((String) bodyMap.get("refresh_token"));
        //jwt令牌
        authToken.setJwtToken((String) bodyMap.get("jti"));
        return authToken;
    }

    private String getHttpBasic() {
        String string = clientId + ":" + clientSecret;
        //将串进行base64编码
        byte[] encode = Base64Utils.encode(string.getBytes());
        return "Basic " + new String(encode);
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
