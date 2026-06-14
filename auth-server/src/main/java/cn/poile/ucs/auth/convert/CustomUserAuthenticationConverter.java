package cn.poile.ucs.auth.convert;

import cn.poile.ucs.auth.security.UserNameUserDetailService;
import com.alibaba.fastjson.JSON;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.AESEncryptUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.provider.token.DefaultUserAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author yzx
 * 自定义用户信息 oauth2默认是只有用户名 远程解系的时候可以直接拿来用 注意不要过度暴露数据
 */
@Component
public class CustomUserAuthenticationConverter extends DefaultUserAuthenticationConverter {

    protected final Log logger = LogFactory.getLog(this.getClass());

    @Autowired
    UserNameUserDetailService userDetailsService;
    @Autowired
    private AESEncryptUtil aesEncryptUtil;

    @Override
    public Map<String, ?> convertUserAuthentication(Authentication authentication) {
        logger.debug("***********    jwt converter   ********************");
        LinkedHashMap<String, Object> response = new LinkedHashMap<String, Object>();
        String name = authentication.getName();
        Object principal = authentication.getPrincipal();
        BaseUserDetail baseUserDetail = null;
        if (principal instanceof BaseUserDetail) {
            baseUserDetail = (BaseUserDetail) principal;
        } else {
            UserDetails user = userDetailsService.loadUserByUsername(name);
            baseUserDetail = (BaseUserDetail) user;
        }

        //TODO 此处根据用户的类别进行处理，让不同的用户携带不通的信息
        try {
            response.put("userName", aesEncryptUtil.encrypt(baseUserDetail.getBaseAuth().getUserName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        response.put("nickName", baseUserDetail.getBaseUser().getNickName());
        response.put("sex", baseUserDetail.getBaseUser().getSex());
        try {
            response.put("phone", aesEncryptUtil.encrypt(baseUserDetail.getBaseAuth().getPhoneNumber()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            response.put("id", aesEncryptUtil.encrypt(String.valueOf(baseUserDetail.getBaseUser().getUserId())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        response.put("avatar", baseUserDetail.getBaseUser().getAvatar());
        try {
            response.put("permissions", aesEncryptUtil.encrypt(JSON.toJSONString(baseUserDetail.getPermissions())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
            try {
                response.put("authorities", aesEncryptUtil.encrypt(JSON.toJSONString(authentication.getAuthorities())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return response;
    }

    @Override
    public Authentication extractAuthentication(Map<String, ?> map) {
        // 从 JWT 中解析用户信息，用于 refresh token 时重建 Authentication
        if (map == null || map.isEmpty()) {
            logger.error("JWT 中未找到用户信息");
            throw new RuntimeException("JWT 中未找到用户信息");
        }

        try {
            // 获取用户ID（从加密字段中解密）
            String encryptedId = (String) map.get("id");
            if (encryptedId == null) {
                logger.error("JWT 中未找到用户ID");
                throw new RuntimeException("JWT 中未找到用户ID");
            }

            String userId = aesEncryptUtil.decrypt(encryptedId);
            String encryptedUsername = (String) map.get("userName");
            String username = encryptedUsername != null ? aesEncryptUtil.decrypt(encryptedUsername) : null;

            logger.debug("从JWT解析用户: userId=" + userId + ", username=" + username);

            // 通过用户名加载用户详情
            if (username != null) {
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (user instanceof BaseUserDetail) {
                    BaseUserDetail baseUserDetail = (BaseUserDetail) user;
                    // 验证用户ID是否匹配
                    if (userId.equals(String.valueOf(baseUserDetail.getBaseUser().getUserId()))) {
                        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                baseUserDetail, null, user.getAuthorities());
                    } else {
                        logger.error("JWT中的用户ID [" + userId + "] 与加载的用户 [" + 
                            baseUserDetail.getBaseUser().getUserId() + "] 不匹配");
                        throw new RuntimeException("JWT中的用户ID与加载的用户不匹配");
                    }
                } else {
                    logger.error("加载的用户不是 BaseUserDetail 类型");
                    throw new RuntimeException("加载的用户类型不正确");
                }
            } else {
                logger.error("JWT 中未找到用户名");
                throw new RuntimeException("JWT 中未找到用户名");
            }
        } catch (Exception e) {
            logger.error("从JWT解析用户信息失败: " + e.getMessage(), e);
            throw new RuntimeException("从JWT解析用户信息失败: " + e.getMessage(), e);
        }
    }
}

