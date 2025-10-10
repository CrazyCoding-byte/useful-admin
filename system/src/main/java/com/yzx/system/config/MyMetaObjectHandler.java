package com.yzx.system.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @className: MybatisConfig
 * @author: yzx
 * @date: 2025/8/21 6:46
 * @Version: 1.0
 * @description:
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    // 插入时的填充策略
    @Override
    public void insertFill(MetaObject metaObject) {
        // 自动填充createtime和updatetime为当前时间
        this.strictInsertFill(metaObject, "createtime", Date.class, new Date());
        this.strictInsertFill(metaObject, "updatetime", Date.class, new Date());
    }

    // 更新时的填充策略
    @Override
    public void updateFill(MetaObject metaObject) {
        // 自动填充updatetime为当前时间
        this.strictUpdateFill(metaObject, "updatetime", Date.class, new Date());
    }
}
