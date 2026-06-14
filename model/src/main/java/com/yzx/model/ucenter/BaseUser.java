package com.yzx.model.ucenter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.system.SysRole;
import lombok.Data;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * @program: xz-framework-parent-reversion
 * @description: 用户的基础信息，作为其他用户实体类的基类
 * @author: wdw
 * @create: 2020-01-20 23:13
 **/

@Data
@ToString
@TableName("sys_user")
public class BaseUser {

    /*** 用户id */
    private Long userId; // 表中存在user_id，无需注解

    /**
     * 租户编号（多租户支持）
     */
    private String tenantId;

    /**
     * CID（表中无此字段）
     */
    @TableField(exist = false)
    private String clientId;

    /** 用户类型（表中存在user_type） */
    private String userType;

    /** 用户性别（表中存在sex，移除错误注解） */
    private String sex;

    /** 用户头像（表中存在avatar） */
    private String avatar;

    /** 用户昵称（表中存在nick_name） */
    private String nickName;

    /**
     * 用户账号（表中存在user_name）
     */
    private String userName;

    /**
     * 手机号码（表中存在phonenumber）
     */
    private String phonenumber;

    /**
     * 微信用户唯一id（表中无unionId字段）
     */
    @TableField(exist = false)
    private String unionId;

    /**
     * 用户邮箱（表中存在email）
     */
    private String email;

    /** 密码（表中存在password） */
    @TableField(exist = false)
    private String password;

    /** 角色组（表中无roleIds字段） */
    @TableField(exist = false)
    private Long[] roleIds;
    @TableField(exist = false)
    private List<SysRole> roles;
    /** 岗位组（表中无postIds字段） */
    @TableField(exist = false)
    private Long[] postIds;

    /** 部门ID（表中存在dept_id） */
    private Long deptId;

    /** 帐号状态（0正常 1停用）（表中存在status） */
    private String status;

    /** 删除标志（0代表存在 2代表删除）（表中存在del_flag） */
    private String delFlag;

    /** 最后登陆IP（表中存在login_ip） */
    private String loginIp;

    /** 最后登陆时间（表中存在login_date） */
    private Date loginDate;

    /**
     * 微信OPENID（表中无wxOpenId字段）
     */
    @TableField(exist = false)
    private String wxOpenId;

    /**
     * 是否服务者（表中无isServant字段）
     */
    @TableField(exist = false)
    private String isServant;


    /** 判断用户是不是超级管理员 */
    public boolean isAdmin() {
        return isAdmin(this.userId);
    }

    /** 判断用户是不是超级管理员 */
    public static boolean isAdmin(Long userId) {
        return userId != null && 1L == userId;
    }

}
