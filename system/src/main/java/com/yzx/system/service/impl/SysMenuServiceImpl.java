package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.RouterVo;
import com.yzx.model.TreeSelect;
import com.yzx.model.constant.UserConstants;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.SysMenu;
import com.yzx.model.system.response.SysMenuDto;
import com.yzx.model.MetaVo;
import com.yzx.system.mapper.SysMenuMapper;
import com.yzx.system.service.ISysMenuService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单 业务层处理
 *
 * @author ruoyi
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {
    
    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenu> selectMenuList(Long userId) {
        return selectMenuList(new SysMenu(), userId);
    }

    /**
     * 根据用户查询系统菜单列表
     *
     * @param menu 菜单信息
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenu> selectMenuList(SysMenu menu, Long userId) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.isNotBlank(menu.getMenuName())) {
            queryWrapper.like(SysMenu::getMenuName, menu.getMenuName());
        }
        if (StringUtils.isNotBlank(menu.getStatus())) {
            queryWrapper.eq(SysMenu::getStatus, menu.getStatus());
        }
        if (menu.getParentId() != null) {
            queryWrapper.eq(SysMenu::getParentId, menu.getParentId());
        }
        
        queryWrapper.orderByAsc(SysMenu::getOrderNum);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        List<String> perms = baseMapper.selectMenuPermsByUserId(userId);
        return new HashSet<>(perms);
    }

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByRoleId(Long roleId) {
        // 先查询角色对应的菜单ID列表
        List<Long> menuIds = new ArrayList<>();
        // 这里简化处理，实际应该从角色菜单关联表中查询
        // 假设我们有一个方法可以查询角色对应的菜单ID
        // 暂时返回空集合
        return new HashSet<>();
    }

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuDto> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        // 如果是超级管理员，返回所有菜单
        if (userId != null && com.yzx.model.system.SysUser.isAdmin(userId)) {
            menus = baseMapper.selectList(null);
        } else {
            menus = baseMapper.selectMenuTreeByUserId(userId);
        }
        return buildMenuDtoTree(menus);
    }

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        // 这里简化处理，实际应该从角色菜单关联表中查询
        // 假设我们有一个方法可以查询角色对应的菜单ID
        // 暂时返回空集合
        return new ArrayList<>();
    }

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterVo> buildMenus(List<SysMenuDto> menus) {
        List<RouterVo> routers = new ArrayList<>();
        for (SysMenuDto menu : menus) {
            RouterVo router = new RouterVo();
            router.setName(menu.getMenuName());
            router.setPath(menu.getPath());
            router.setComponent(menu.getComponent());
            MetaVo meta = new MetaVo();
            meta.setTitle(menu.getMenuName());
            meta.setIcon(menu.getIcon());
            router.setMeta(meta);
            if (!menu.getChildren().isEmpty()) {
                router.setChildren(buildMenus(menu.getChildren()));
            }
            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建前端所需要树结构
     *
     * @param menus 菜单列表
     * @return 树结构列表
     */
    @Override
    public List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> menuTree = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (menu.getParentId() == 0L) {
                menuTree.add(buildMenuTree(menus, menu));
            }
        }
        return menuTree;
    }

    /**
     * 构建前端所需要下拉树结构
     *
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    @Override
    public List<TreeSelect> buildMenuTreeSelect(List<SysMenu> menus) {
        List<SysMenu> menuTree = buildMenuTree(menus);
        return menuTree.stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    @Override
    public SysMenu selectMenuById(Long menuId) {
        return baseMapper.selectById(menuId);
    }

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean hasChildByMenuId(Long menuId) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getParentId, menuId);
        return baseMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 查询菜单是否存在角色
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkMenuExistRole(Long menuId) {
        // 这里简化处理，实际应该从角色菜单关联表中查询
        // 假设我们有一个方法可以查询菜单对应的角色数量
        // 暂时返回 false
        return false;
    }

    /**
     * 新增菜单
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public int insertMenu(SysMenu menu) {
        // 校验菜单名称是否唯一
        if (!checkMenuNameUnique(menu)) {
            throw new ServiceException("新增菜单" + menu.getMenuName() + "失败，菜单名称已存在");
        }
        return baseMapper.insert(menu) > 0 ? 1 : 0;
    }

    /**
     * 修改菜单
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public int updateMenu(SysMenu menu) {
        // 校验菜单名称是否唯一
        if (!checkMenuNameUnique(menu)) {
            throw new ServiceException("修改菜单" + menu.getMenuName() + "失败，菜单名称已存在");
        }
        return baseMapper.updateById(menu) > 0 ? 1 : 0;
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public int deleteMenuById(Long menuId) {
        // 检查是否有子菜单
        if (hasChildByMenuId(menuId)) {
            throw new ServiceException("存在子菜单，无法删除");
        }
        return baseMapper.deleteById(menuId) > 0 ? 1 : 0;
    }

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public boolean checkMenuNameUnique(SysMenu menu) {
        Long menuId = menu.getMenuId() == null ? -1L : menu.getMenuId();
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getMenuName, menu.getMenuName());
        queryWrapper.eq(SysMenu::getParentId, menu.getParentId());
        queryWrapper.ne(SysMenu::getMenuId, menuId);
        return baseMapper.selectOne(queryWrapper) == null;
    }

    /**
     * 构建菜单树
     *
     * @param menus 菜单列表
     * @param parentMenu 父菜单
     * @return 菜单树
     */
    private SysMenu buildMenuTree(List<SysMenu> menus, SysMenu parentMenu) {
        List<SysMenu> children = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (menu.getParentId().equals(parentMenu.getMenuId())) {
                children.add(buildMenuTree(menus, menu));
            }
        }
        parentMenu.setChild(children);
        return parentMenu;
    }

    /**
     * 构建菜单DTO树
     *
     * @param menus 菜单列表
     * @return 菜单DTO树
     */
    private List<SysMenuDto> buildMenuDtoTree(List<SysMenu> menus) {
        List<SysMenuDto> menuDtoTree = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (menu.getParentId() == 0L) {
                menuDtoTree.add(buildMenuDtoTree(menus, menu));
            }
        }
        return menuDtoTree;
    }

    /**
     * 构建菜单DTO树
     *
     * @param menus 菜单列表
     * @param parentMenu 父菜单
     * @return 菜单DTO树
     */
    private SysMenuDto buildMenuDtoTree(List<SysMenu> menus, SysMenu parentMenu) {
        SysMenuDto menuDto = new SysMenuDto();
        // 复制属性
        menuDto.setMenuId(parentMenu.getMenuId());
        menuDto.setParentId(parentMenu.getParentId());
        menuDto.setMenuName(parentMenu.getMenuName());
        menuDto.setPath(parentMenu.getPath());
        menuDto.setComponent(parentMenu.getComponent());
        menuDto.setQuery(parentMenu.getQuery());
        menuDto.setIsFrame(parentMenu.getIsFrame());
        menuDto.setIsCache(parentMenu.getIsCache());
        menuDto.setMenuType(parentMenu.getMenuType());
        menuDto.setVisible(parentMenu.getVisible());
        menuDto.setStatus(parentMenu.getStatus());
        menuDto.setPerms(parentMenu.getPerms());
        menuDto.setIcon(parentMenu.getIcon());
        menuDto.setOrderNum(parentMenu.getOrderNum());
        menuDto.setCreateBy(parentMenu.getCreateBy());
        menuDto.setCreateTime(parentMenu.getCreateTime());
        menuDto.setUpdateBy(parentMenu.getUpdateBy());
        menuDto.setUpdateTime(parentMenu.getUpdateTime());
        menuDto.setRemark(parentMenu.getRemark());
        
        List<SysMenuDto> children = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (menu.getParentId().equals(parentMenu.getMenuId())) {
                children.add(buildMenuDtoTree(menus, menu));
            }
        }
        menuDto.setChildren(children);
        return menuDto;
    }
}
