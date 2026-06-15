package com.yzx.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户信息视图对象 sys_user
 *
 * @author yzx
 */
@Data
public class SysUserVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 用户账号
     */
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 是否是服务者（1：是，0：不是）
     */
    private String isServant;

    /**
     * 用户类型（00系统用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String phonenumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String sex;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 推广码
     */
    private String qrCode;

    /**
     * 密码
     */
    @JsonIgnore
    @JsonProperty
    private String password;

    /**
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    /**
     * 最后登陆IP
     */
    private String loginIp;

    /**
     * 最后登陆时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date loginDate;

    /**
     * 身份证号码
     */
    private String idNumber;

    /**
     * 生日
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 登陆时保存的用户手机appid
     */
    private String clientId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 更新者
     */
    private String updateBy;

    /**
     * 部门对象
     */
    private SysDeptVo dept;

    /**
     * 角色对象
     */
    private List<SysRoleVo> roles;

    /**
     * 角色组
     */
    private Long[] roleIds;

    /**
     * 岗位组
     */
    private Long[] postIds;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 是否为超级管理员
     */
    private boolean admin;

    public boolean isAdmin() {
        return isAdmin(this.userId);
    }

    public static boolean isAdmin(Long userId) {
        return userId != null && (1L == userId || 222L == userId);
    }

}
