package com.yzx.model.system.response;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.system.SysMenu;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: xz-framework-parent-reversion
 * @description: 菜单管理树状结构数据封装
 * @author: wdw
 * @create: 2020-02-03 20:41
 **/

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TableName("sys_menu")
@ToString(callSuper = true)
public class SysMenuDto extends SysMenu {
    /**
     * 父菜单名称
     */
    private String parentName;

    /**
     * 子菜单
     */
    private List<SysMenuDto> children = new ArrayList<SysMenuDto>();

    /**
     * 路由参数（用于前端展示）
     */
    private String query;

    /**
     * 路由名称（前端使用）
     */
    private String name;

    /**
     * 路由重定向地址
     */
    private String redirect;

    /**
     * 是否隐藏路由（前端使用）
     */
    private Boolean hidden;

    /**
     * 路由元数据（前端使用）
     */
    private Meta meta;

    /**
     * Meta 元数据内部类
     */
    @Data
    @Accessors(chain = true)
    public static class Meta {
        /**
         * 菜单标题
         */
        private String title;

        /**
         * 菜单图标
         */
        private String icon;

        /**
         * 是否隐藏
         */
        private Boolean hidden;

        /**
         * 是否一直显示
         */
        private Boolean alwaysShow;
    }

}
