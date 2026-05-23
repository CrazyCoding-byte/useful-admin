package com.yzx.system.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yzx.model.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis-Plus 自动填充处理器
 * 自动填充 createBy、updateBy、createTime、updateTime 字段
 *
 * @author yzx
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时的填充策略
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");

        // 获取当前用户ID
        String userId = getCurrentUserId();
        Date now = new Date();

        // 自动填充 createTime
        this.strictInsertFill(metaObject, "createTime", Date.class, now);

        // 自动填充 updateTime
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);

        // 自动填充 createBy
        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", String.class, userId);
        }

        // 自动填充 updateBy
        if (userId != null) {
            this.strictInsertFill(metaObject, "updateBy", String.class, userId);
        }

        // 自动填充 createDept（如果有）
        Long deptId = getCurrentDeptId();
        if (deptId != null) {
            this.strictInsertFill(metaObject, "createDept", Long.class, deptId);
        }

        log.debug("插入填充完成: userId={}, deptId={}", userId, deptId);
    }

    /**
     * 更新时的填充策略
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");

        // 获取当前用户ID
        String userId = getCurrentUserId();
        Date now = new Date();

        // 自动填充 updateTime
        this.strictUpdateFill(metaObject, "updateTime", Date.class, now);

        // 自动填充 updateBy
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, userId);
        }

        log.debug("更新填充完成: userId={}", userId);
    }

    /**
     * 获取当前用户名称
     */
    private String getCurrentUserId() {
        try {
            return SecurityUtils.getBaseUserDetail().getBaseUser().getUserName();
        } catch (Exception e) {
            log.debug("无法获取当前用户ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前部门ID
     */
    private Long getCurrentDeptId() {
        try {
            return SecurityUtils.getBaseUserDetail().getBaseUser().getDeptId();
        } catch (Exception e) {
            log.debug("无法获取当前部门ID: {}", e.getMessage());
            return null;
        }
    }
}
