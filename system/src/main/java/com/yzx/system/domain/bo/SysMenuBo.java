package com.yzx.system.domain.bo;

import com.yzx.model.system.SysMenu;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 菜单权限业务对象 sys_menu
 *
 * @author yzx
 */
public class SysMenuBo extends SysMenu {

    private static final long serialVersionUID = 1L;

    // 继承父类字段，只添加校验注解
    // 在父类字段上使用校验注解需要在Controller层使用@Validated(SysMenuBo.class)分组校验
    // 或者使用自定义校验逻辑

}
