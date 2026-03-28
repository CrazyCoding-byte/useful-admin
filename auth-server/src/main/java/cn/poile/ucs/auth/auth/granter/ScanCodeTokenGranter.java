package cn.poile.ucs.auth.auth.granter;

import cn.poile.ucs.auth.auth.Token.ScanCodeAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @className: ScanCodeTokenGranter
 * @author: yzx
 * @date: 2026/3/27 15:04
 * @Version: 1.0
 * @description:
 */

public class ScanCodeTokenGranter extends AbstractTokenGranter {
    private static final String GRANT_TYPE = "scan_code";
    private final AuthenticationManager authenticationManager;

    public ScanCodeTokenGranter(AuthenticationManager authenticationManager,
                                AuthorizationServerTokenServices tokenServices,
                                ClientDetailsService clientDetailsService,
                                OAuth2RequestFactory requestFactory) {
        super(tokenServices, clientDetailsService, requestFactory, GRANT_TYPE);
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected OAuth2Authentication getOAuth2Authentication(ClientDetails client, TokenRequest tokenRequest) {
        Map<String, String> parameters = new LinkedHashMap<>(tokenRequest.getRequestParameters());

        // 获取扫码参数
        String scene = parameters.get("scene");
        String uid = parameters.get("uid");

        // 构建未认证Token
        Authentication userAuth = new ScanCodeAuthenticationToken(scene, uid);
        ((AbstractAuthenticationToken) userAuth).setDetails(parameters);

        // 执行认证
        try {
            userAuth = authenticationManager.authenticate(userAuth);
        } catch (AccountStatusException | BadCredentialsException e) {
            throw new InvalidGrantException(e.getMessage());
        }

        if (userAuth == null || !userAuth.isAuthenticated()) {
            throw new InvalidGrantException("Could not authenticate scan code: " + scene);
        }

        OAuth2Request oAuth2Request = getRequestFactory().createOAuth2Request(client, tokenRequest);
        return new OAuth2Authentication(oAuth2Request, userAuth);
    }
}
