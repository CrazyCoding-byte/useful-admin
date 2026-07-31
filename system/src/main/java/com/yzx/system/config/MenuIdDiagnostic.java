package com.yzx.system.config;

import com.yzx.model.system.SysMenu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 临时诊断 Bean：确认运行时加载的 SysMenu.class 来源及 @TableId 类型
 * TODO: 问题修复后删除此类
 */
@Slf4j
@Component
public class MenuIdDiagnostic {

    @PostConstruct
    public void diagnose() {
        try {
            java.net.URL location = SysMenu.class.getProtectionDomain().getCodeSource().getLocation();
            com.baomidou.mybatisplus.annotation.TableId tableId =
                    SysMenu.class.getDeclaredField("menuId").getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class);
            String annotationIdType = tableId != null && tableId.type() != null ? tableId.type().name() : "null";

            String tableInfoIdType = "unknown";
            try {
                com.baomidou.mybatisplus.core.metadata.TableInfo info =
                        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(SysMenu.class);
                if (info != null) {
                    tableInfoIdType = info.getIdType() != null ? info.getIdType().name() : "null";
                }
            } catch (Exception ex) {
                tableInfoIdType = "error:" + ex.getMessage();
            }

            log.info("========== MenuIdDiagnostic ==========");
            log.info("SysMenu.class loaded from: {}", location);
            log.info("SysMenu.menuId @TableId.type = {}", annotationIdType);
            log.info("SysMenu TableInfo.idType = {}", tableInfoIdType);
            log.info("======================================");
        } catch (Exception e) {
            log.error("MenuIdDiagnostic failed", e);
        }
    }
}
