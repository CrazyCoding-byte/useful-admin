package com.yzx.model.enums;


import com.google.common.collect.ImmutableMap;
import com.yzx.model.ResultCode;

/**
 * Created by admin on 2018/3/5.
 */
public enum AuthCode implements ResultCode {

    /**
     * 异常状态
     */
    AUTH_USERNAME_NONE(23001, "请输入账号！"),
    AUTH_MOBILE_NONE(23002, "请输入手机号！"),
    AUTH_PASSWORD_NONE(23003, "请输入密码！"),
    AUTH_VERIFYCODE_NONE(23004, "请输入验证码！"),
    AUTH_VERIFYCODE_ERROR(24004, "验证码错误！"),
    AUTH_ACCOUNT_NOTEXISTS(23005, "账号不存在！"),
    AUTH_CREDENTIAL_ERROR(23006, "账号或密码错误！"),
    AUTH_LOGIN_ERROR(23007, "登陆过程出现异常请尝试重新操作！"),
    AUTH_LOGIN_APPLYTOKEN_FAIL(23008, "申请令牌失败！"),
    AUTH_LOGIN_TOKEN_SAVEFAIL(23009, "存储令牌失败！"),
    AUTH_METHOD_NOT_SUPPORT(10008, "登陆请求方法错误，只支持post提交！"),
    AUTH_REFRESH_TOKEN_NOT_EXIST(23011, "刷新令牌不存在！"),
    AUTH_REFRESH_TOKEN_ERROR(23010, "刷新令牌失败！"),
    AUTH_ACCOUNT_FORBIDDEN(23012, "账号已被禁用！"),
    AUTH_GRANT_TYPE_ERROR(23013, "授权类型错误！"),
    Auth_MOBILE_NOT_REGISTER(23014, "手机号未注册！"),
    AUTH_TENANT_NOT_AVAILABLE(23015, "租户不存在或已过期！"),
    AUTH_TENANT_REQUIRED(23016, "请选择租户！"),
    AUTH_TENANT_NO_ALLOWD_OPERATION(23017, "不允许操作");
    /**
     * 操作代码
     */
    int code;
    /**
     * 提示信息
     */
    String message;

    private AuthCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private static final ImmutableMap<Integer, AuthCode> CACHE;

    static {
        final ImmutableMap.Builder<Integer, AuthCode> builder = ImmutableMap.builder();
        for (AuthCode commonCode : values()) {
            builder.put(commonCode.code(), commonCode);
        }
        CACHE = builder.build();
    }

    @Override
    public int code() {
        return code;
    }

    /**
     * 提示信息
     */
    @Override
    public String msg() {
        return message;
    }
}
