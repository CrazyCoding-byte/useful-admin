package com.yzx.model.system;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 租户表
 * 管理系统中的租户信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    @TableId
    private String tenantId;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 租户套餐ID
     */
    private Long packageId;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 租户状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否系统默认（0否 1是）
     */
    private String isDefault;

    /**
     * 域名
     */
    private String domain;

    /**
     * 租户Logo
     */
    private String logo;

    /**
     * 租户地址
     */
    private String address;

    /**
     * 统一社会信用代码
     */
    private String creditCode;

    /**
     * 判断租户是否过期
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return false;
        }
        return expireTime.before(new Date());
    }

    /**
     * 判断是否为系统默认租户
     */
    public boolean isDefaultTenant() {
        return "1".equals(isDefault);
    }

    /**
     * 判断是否可用
     */
    public boolean isAvailable() {
        return "0".equals(status) && !isExpired();
    }
}
