package com.yzx.model.ucenter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @program: xz-framework-parent-reversion
 * @description: 用于登陆授权校验用户的实体封装
 * @author: wdw
 * @create: 2020-01-21 00:07
 **/
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_user")
public class BaseAuth {
    /**
     * 用户id（主键）
     */
    @TableField("user_id")
    private Long userId;
    /**
     * 用户账号
     */
    @TableField("user_name")
    private String userName;
    /**
     * 手机号码
     */
    @TableField("phonenumber")
    private String phoneNumber;
    /**
     * 用户邮箱
     */
    private String email;
    /**
     * 密码
     */
    private String password;
}
