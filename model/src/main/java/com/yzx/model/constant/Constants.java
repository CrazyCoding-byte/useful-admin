package com.yzx.model.constant;


import java.util.Locale;

/**
 * 通用常量信息
 *
 * @author ruoyi
 */
public class Constants {
    /**
     * Zuul请求头TOKEN名称（不要有空格）
     */
    public static final String ZUUL_TOKEN_HEADER = "ZuulToken";
    /**
     * Zuul请求头TOKEN值
     */
    public static final String ZUUL_TOKEN_VALUE = "xz:zuul:123456";

    /**
     * UTF-8 字符集
     */
    public static final String UTF8 = "UTF-8";

    /**
     * 通用成功标识
     */
    public static final String SUCCESS = "0";

    /**
     * 通用失败标识
     */
    public static final String FAIL = "1";

    /**
     * 登录成功
     */
    public static final String LOGIN_SUCCESS = "Success";

    /**
     * 注销
     */
    public static final String LOGOUT = "Logout";

    /**
     * 登录失败
     */
    public static final String LOGIN_FAIL = "Error";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码有效期（分钟）
     */
    public static final Integer CAPTCHA_EXPIRATION = 2;

    /**
     * 令牌
     */
    public static final String TOKEN = "token";

    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 令牌前缀
     */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /**
     * 用户ID
     */
    public static final String JWT_USERID = "userid";


    /**
     * 用户头像
     */
    public static final String JWT_AVATAR = "avatar";

    /**
     * 创建时间
     */
    public static final String JWT_CREATED = "created";

    /**
     * 用户权限
     */
    public static final String JWT_AUTHORITIES = "authorities";

    /**
     * 资源映射路径 前缀
     */
    public static final String RESOURCE_PREFIX = "/profile";

    /**
     * 手机号+密码登陆
     */
    public static final String LOGIN_TYPE_PWD = "pwd";
    /**
     * 账户名+密码登陆
     */
    public static final String LOGIN_TYPE_PASSWORD = "password";

    /**
     * 短信登陆
     */
    public static final String LOGIN_TYPE_SMS = "sms_code";

    /**
     * redis短信验证码key值
     */
    public static final String SMS_CODE = "sms_code";

    public static final String SYSTEM_VERSION = "system_version";

    /**
     * redis用户token信息key值
     */
    public static final String USER_TOKEN = "user_token:";
    /**
     * www主域
     */
    public static final String WWW = "www.";
    /**
     * http请求
     */
    public static final String HTTP = "http://";
    /**
     * https请求
     */
    public static final String HTTPS = "https://";
    /**
     * 系统默认头像
     **/
    public static final String DEFAULT_AVATAR = "group1/M00/00/07/wKgMbV6_sfOAMXwpAALh3rMBV7o285.jpg";


    public static final String DEVICE_STATUS_ONLINE = "ONLINE";


    public static final String WMS_QUEUE = "wms_queue";

    public static final String QUEUE_EXCHANGE = "wms_exchange";

    public static final String WMS_ORDER_KEY = "wms_order";

    /**
     * 二维码
     */
    public static final String SYS_CODE = "qrCode:";
    /**
     * 管理员角色权限标识
     */
    public static final String SUPER_ADMIN = "admin";
    /**
     * 所有权限标识
     */
    public static final String ALL_PERMISSION = "*:*:*";
}
