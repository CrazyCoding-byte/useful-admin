package com.yzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 数据权限类型枚举
 * 
 * @author ruoyi
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {
    
    /**
     * 全部数据权限（不做任何过滤）
     */
    ALL("1", "全部数据", "", ""),
    
    /**
     * 自定义数据权限（通过角色配置的部门列表）
     */
    CUSTOM("2", "自定义数据", "#{#deptColumn} IN (#{@dataScopeService.getRoleCustomDeptIds(#user.roleId)})", "1 = 0"),
    
    /**
     * 本部门数据权限
     */
    DEPT("3", "本部门数据", "#{#deptColumn} = #{#user.deptId}", "1 = 0"),
    
    /**
     * 本部门及以下数据权限
     */
    DEPT_AND_CHILD("4", "本部门及以下", "#{#deptColumn} IN (#{@dataScopeService.getDeptAndChildIds(#user.deptId)})", "1 = 0"),
    
    /**
     * 仅本人数据权限
     */
    SELF("5", "仅本人数据", "#{#userColumn} = #{#user.userId}", "1 = 0"),
    
    /**
     * 本部门及以下或本人数据权限
     */
    DEPT_AND_CHILD_OR_SELF("6", "本部门及以下或本人", "#{#deptColumn} IN (#{@dataScopeService.getDeptAndChildIds(#user.deptId)}) OR #{#userColumn} = #{#user.userId}", "1 = 0");
    
    private final String code;
    private final String name;
    private final String sqlTemplate; // SpEL模板
    private final String elseSql; // 兜底SQL（当模板不适用时）
    
    public static DataScopeType getByCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }
}
