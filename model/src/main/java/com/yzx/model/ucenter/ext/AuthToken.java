package com.yzx.model.ucenter.ext;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @program: xz-framework-parent
 * @description: oauthToken
 * @author: Mr.Pan
 * @create: 2020-02-13 11:08
 */
@Data
@ToString
@NoArgsConstructor
public class AuthToken {
    /**
     * 访问token
     */
    String accessToken;
    /**
     * 刷新token
     */
    String refreshToken;
    /**
     * jwt令牌
     */
    String jwtToken;
    /**
     * token有效期
     */
    Long expiresIn;
}
