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
        response.put("userName", baseUserDetail.getBaseAuth().getUserName());
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
        response.put("isServant", baseUserDetail.getBaseUser().getIsServant());
        response.put("avatar", baseUserDetail.getBaseUser().getAvatar());
        try {
            response.put("ID", aesEncryptUtil.encrypt(JSON.toJSONString(baseUserDetail.getBaseUser().getIdNumber())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        return null;
    }
}

