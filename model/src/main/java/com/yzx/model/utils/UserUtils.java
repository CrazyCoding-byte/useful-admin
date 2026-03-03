package com.yzx.model.utils;

/**
 * @program: xz-framework-parent-reversion
 * @description: 用户相关的工具类，主要是判别用户角色，用户是不是超级管理员
 * @author: wdw
 * @create: 2020-02-13 14:33
 **/
public class UserUtils {

    /** 判断用户是不是超级管理员 */
    public static boolean isAdmin(Long userId) {
        return userId != null && 222L == userId;
    }

}
