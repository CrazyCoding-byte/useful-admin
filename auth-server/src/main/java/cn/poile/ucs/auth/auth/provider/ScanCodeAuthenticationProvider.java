package cn.poile.ucs.auth.auth.provider;

import cn.poile.ucs.auth.auth.Token.ScanCodeAuthenticationToken;
import com.yzx.model.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * @className: ScanCodeAuthenticationProvider
 * @author: yzx
 * @date: 2026/3/27 13:51
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class ScanCodeAuthenticationProvider implements AuthenticationProvider, MessageSourceAware {
    private StringRedisTemplate stringRedisTemplate;
    private UserDetailsService userDetailsService;
    private MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();
    private boolean hideUserNotFoundExceptions = true;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messages = new MessageSourceAccessor(messageSource);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        //1.获取参数:scene
        String scene = (String) authentication.getPrincipal();
        String uid = (String) authentication.getCredentials();
        //2.非空校验
        if (scene == null) {
            throw new BadCredentialsException("Missing scene");
        }
        if (uid == null) {
            log.error("Missing");
            throw new BadCredentialsException("Missing uid");
        }
        //3.校验Redis中扫码状态(scene必须已被小程序扫码确认)
        String redisuId = stringRedisTemplate.opsForValue().get(Constants.SYS_CODE + scene);
        if (redisuId == null) {
            log.error("Invalid scene");
            throw new BadCredentialsException("Invalid scene");
        }
        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(uid);
        } catch (UsernameNotFoundException e) {
            log.info("小程序用户uid未注册:{}", uid);
            if (this.hideUserNotFoundExceptions) {
                throw new BadCredentialsException("Bad credentials");
            }
            throw e;
        }
        // 5. 账号状态校验
        check(user);

        // 6. 生成已认证Token
        ScanCodeAuthenticationToken authToken = new ScanCodeAuthenticationToken(user, uid, user.getAuthorities());
        authToken.setDetails(user);
        return authToken;
    }

    // 仅支持ScanCodeAuthenticationToken
    @Override
    public boolean supports(Class<?> aClass) {
        return ScanCodeAuthenticationToken.class.isAssignableFrom(aClass);
    }

    // 账号校验
    private void check(UserDetails user) {
        if (!user.isAccountNonLocked()) throw new LockedException("User account is locked");
        if (!user.isEnabled()) throw new DisabledException("User is disabled");
        if (!user.isAccountNonExpired()) throw new AccountExpiredException("User account has expired");
    }

    // setter
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void setHideUserNotFoundExceptions(boolean hideUserNotFoundExceptions) {
        this.hideUserNotFoundExceptions = hideUserNotFoundExceptions;
    }

    public void setUserDetailsService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }
}
