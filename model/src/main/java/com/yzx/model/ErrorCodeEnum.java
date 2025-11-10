package com.yzx.model;

import lombok.Getter;

/**
 * Description:
 *
 * @author: aoxiang
 * @create: 2024-12-21 16:13
 **/

@Getter
public enum ErrorCodeEnum {
    /**
     * 错误码枚举类
     * 错误码共有5位:
     * 第一位表示错误来源：
     * - 1：客户端错误
     * - 2：服务端错误
     * - 3：第三方错误
     * <p>
     * 第二、三位表示错误模块：
     * - 00：通用模块
     * - 01：用户模块
     * - 02：tiktok模块
     * - 03：办公室模块
     * - 04：系统资源模块
     * - 05：地区模块
     * - 06：whatsapp模块
     * - 07：文件系统模块
     * - 08：Messenger模块
     * - ...（按需扩展）
     * <p>
     * 第四、五位为递增数字，表示具体错误类型。
     *
     */
    // 通用模块
    SUCCESS("00000", "成功"),
    PARAM_MISSING("10001", "参数缺失错误"),
    PARAM_TYPE_ERROR("10002", "参数类型错误"),
    REQUEST_METHOD_ERROR("10003", "请求方式错误"),
    PARAM_VALIDATE_ERROR("10004", "参数校验失败"),
    UNAUTHORIZED("10005", "token缺失"),
    TOKEN_EXPIRED("10006", "token已过期"),
    USER_EMAIL_EXIST("10101", "用户邮箱已存在"),
    USER_NOT_EXISTS("10102", "用户不存在"),
    USER_PASSWORD_ERROR("10103", "用户账号或密码错误"),
    PERMISSION_DENIED("10104", "没有当前操作权限"),
    USER_ACCOUNT_EXISTS("10105", "账号已存在"),
    EMAIL_NOT_FOUND("10106", "用户邮箱未找到"),
    USER_EXPIRED("10107", "用户账号已过期"),
    OFFICE_NAME_EXISTS("10301", "办公室名称已存在"),
    OFFICE_NOT_FOUND("10301", "办公室不存在"),
    RESOURCE_ROUTE_EXIST("10401", "资源路由已存在"),
    BUTTON_PERMISSION_NAME_EXIST("10402", "按钮权限名称已存在"),
    RESOURCE_PARENT_NOT_FOUND("10403", "父资源不存在或被删除"),
    RESOURCE_CHILD_EXIST("10404", "存在子资源"),
    AREA_NAME_EXISTS("10501", "地区名称已存在"),
    AREA_HAS_DATA("10502", "地区已有关联数据"),
    MESSENGER_CLIENT_EXISTS("10801", "应用已存在"),
    MESSENGER_CLIENT_ADD_FAIL("10802", "添加应用失败"),
    SYSTEM_ERROR("20001", "系统错误"),
    AES_DECRYPT_ERROR("20002", "AES解密失败"),
    AVATAR_NOT_FOUND("20602", "未找到头像"),
    WHATSAPP_ACCOUNT_BOUND("20603", "whatsapp账号已分配给用户"),
    NOT_ALLOWED_SYSTEM("20701", "不被允许的所属系统"),
    FILE_NOT_EXISTS("20702", "文件不存在"),
    PAGE_NOT_EXIST("20801", "公共主页不存在"),
    FILE_UPLOAD_ERROR("20006", "文件上传失败"),
    REQUEST_NO_RESPONSE("30001", "请求无响应"),
    EXECUTION_TIMEOUT("30002", "执行超时"),
    EXECUTION_FAIL("30003", "请求失败"),
    CHUNK_HASH_ERROR("30004", "分片哈希校验失败");
    private final String code;
    private final String desc;

    ErrorCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
