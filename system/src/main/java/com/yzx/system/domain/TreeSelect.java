package com.yzx.system.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yzx.model.StringUtils;
import com.yzx.model.constant.UserConstants;
import com.yzx.model.system.SysMenu;
import com.yzx.model.system.SysMenuTree;
import com.yzx.model.system.SysUserProp;
import com.yzx.system.domain.vo.SysDeptVo;
import com.yzx.system.domain.vo.SysMenuVo;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Treeselect树结构实体类
 *
 * @author ruoyi
 */
public class TreeSelect implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 节点名称 */
    private String label;

    /** 节点禁用 */
    private boolean disabled = false;

    /** 子节点 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TreeSelect> children;

    public TreeSelect() {

    }

    public TreeSelect(SysUserProp.SysDept dept) {
        this.id = dept.getDeptId();
        this.label = dept.getDeptName();
        this.disabled = StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus());
        this.children = dept.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public TreeSelect(SysDeptVo dept) {
        this.id = dept.getDeptId();
        this.label = dept.getDeptName();
        this.disabled = StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus());
        if (dept.getChildren() != null) {
            this.children = dept.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
        }
    }

    public TreeSelect(SysMenuTree menu) {
        this.id = menu.getMenuId();
        this.label = menu.getMenuName();
        this.children = menu.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public TreeSelect(SysMenu sysMenu) {
        this.id = sysMenu.getMenuId();
        this.label = sysMenu.getMenuName();
    }

    public TreeSelect(SysMenuVo menu) {
        this.id = menu.getMenuId();
        this.label = menu.getMenuName();
        if (menu.getChildren() != null) {
            this.children = menu.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public List<TreeSelect> getChildren() {
        return children;
    }

    public void setChildren(List<TreeSelect> children) {
        this.children = children;
    }
}
