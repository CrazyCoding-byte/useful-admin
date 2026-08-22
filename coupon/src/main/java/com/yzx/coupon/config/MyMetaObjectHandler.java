package com.yzx.coupon.config;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yzx.model.utils.SecurityUtils;

@Component
@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {

   /**
     * 新增时自动填充字段
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();

        // 创建时间
        this.strictInsertFill(
                metaObject,
                "createTime",
                Date.class,
                now
        );

        // 更新时间
        this.strictInsertFill(
                metaObject,
                "updateTime",
                Date.class,
                now
        );

        // 当前用户信息
        String userName = getCurrentUserName();
        Long deptId = getCurrentDeptId();

        // 创建者
        if (userName != null) {
            this.strictInsertFill(
                    metaObject,
                    "createBy",
                    String.class,
                    userName
            );

            // 更新者
            this.strictInsertFill(
                    metaObject,
                    "updateBy",
                    String.class,
                    userName
            );
        }

        // 创建部门
        if (deptId != null) {
            this.strictInsertFill(
                    metaObject,
                    "createDept",
                    Long.class,
                    deptId
            );
        }

        log.debug(
                "新增自动填充完成：createTime={}, updateTime={}, createBy={}, createDept={}",
                now,
                now,
                userName,
                deptId
        );
    }

    /**
     * 更新时自动填充字段
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();

        // 更新时间
        this.strictUpdateFill(
                metaObject,
                "updateTime",
                Date.class,
                now
        );

        // 更新者
        String userName = getCurrentUserName();

        if (userName != null) {
            this.strictUpdateFill(
                    metaObject,
                    "updateBy",
                    String.class,
                    userName
            );
        }

        log.debug(
                "更新自动填充完成：updateTime={}, updateBy={}",
                now,
                userName
        );
    }

    /**
     * 获取当前登录用户名称
     */
    private String getCurrentUserName() {
        try {
            if (SecurityUtils.getBaseUserDetail() == null) {
                return null;
            }

            if (SecurityUtils.getBaseUserDetail().getBaseUser() == null) {
                return null;
            }

            return SecurityUtils.getBaseUserDetail()
                    .getBaseUser()
                    .getUserName();

        } catch (Exception e) {
            log.debug("获取当前用户名称失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前登录用户部门ID
     */
    private Long getCurrentDeptId() {
        try {
            if (SecurityUtils.getBaseUserDetail() == null) {
                return null;
            }

            if (SecurityUtils.getBaseUserDetail().getBaseUser() == null) {
                return null;
            }

            return SecurityUtils.getBaseUserDetail()
                    .getBaseUser()
                    .getDeptId();

        } catch (Exception e) {
            log.debug("获取当前用户部门ID失败：{}", e.getMessage());
            return null;
        }
    }
}
