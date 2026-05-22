package com.yzx.model.ucenter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @className: OauthClientDetails
 * @author: yzx
 * @date: 2025/12/26 14:47
 * @Version: 1.0
 * @description:
 */
@TableName("oauth_client_details")
@Data
public class OauthClientDetails {
    private static final long serialVersionUID = 1L;

    /**
     * 客户端ID（主键）
     */
    @TableId(value = "client_id") // 标识主键，映射字段名
    private String clientId;

    /**
     * 资源ID列表，多个资源用逗号分隔
     */
    @TableField(value = "resource_ids") // 映射数据库字段名（解决驼峰/下划线差异）
    private String resourceIds;

    /**
     * 客户端密钥（建议加密存储）
     */
    @TableField(value = "client_secret")
    private String clientSecret;

    /**
     * 客户端权限范围，多个范围用逗号分隔
     */
    @TableField(value = "scope")
    private String scope;

    /**
     * 授权类型，多个类型用逗号分隔（如password,authorization_code,refresh_token）
     */
    @TableField(value = "authorized_grant_types")
    private String authorizedGrantTypes;

    /**
     * 重定向URI
     */
    @TableField(value = "web_server_redirect_uri")
    private String webServerRedirectUri;

    /**
     * 客户端拥有的权限（角色），多个用逗号分隔
     */
    @TableField(value = "authorities")
    private String authorities;

    /**
     * access_token有效期（秒）
     */
    @TableField(value = "access_token_validity")
    private Integer accessTokenValidity;

    /**
     * refresh_token有效期（秒）
     */
    @TableField(value = "refresh_token_validity")
    private Integer refreshTokenValidity;

    /**
     * 附加信息（JSON格式）
     */
    @TableField(value = "additional_information")
    private String additionalInformation;

    /**
     * 是否自动批准（true/false，或范围列表）
     */
    @TableField(value = "autoapprove")
    private String autoapprove;

    /**
     * 租户编号（多租户支持）
     */
    @TableField(value = "tenant_id")
    private String tenantId;
}
