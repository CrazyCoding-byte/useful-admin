package cn.poile.ucs.auth.utils;

public enum AuthCode {
    AUTH_LOGIN_APPLYTOKEN_FAIL(23008, "申请令牌失败！"),
    AUTH_LOGIN_USER_NOT_EXIST(23001, "账号不存在！"),
    AUTH_LOGIN_PASSWORD_ERROR(23002, "密码错误！"),
    AUTH_LOGIN_TOKEN_SAVEFAIL(23009, "令牌存储失败！"); // 原有错误码

    // 原有枚举字段和方法
    private int code;
    private String msg;
    // getter/constructor省略

    AuthCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}