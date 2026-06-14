package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.yzx.model.TreeSelect;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.SysMenu;
import com.yzx.model.system.SysTenant;
import com.yzx.model.system.SysUser;
import com.yzx.model.system.response.SysMenuDto;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.SecurityUtils;
import com.yzx.system.domain.bo.SysMenuBo;
import com.yzx.system.domain.convert.SysMenuConvert;
import com.yzx.system.domain.vo.SysMenuVo;
import com.yzx.system.mapper.SysMenuMapper;
import com.yzx.system.service.ISysMenuService;
import com.yzx.system.service.ISysTenantPackageService;
import com.yzx.system.service.ISysTenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单 业务层处理
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements ISysMenuService {

    @Autowired
    private SysMenuMapper baseMapper;

    @Autowired
    private SysMenuConvert menuConvert;

    @Autowired(required = false)
    private ISysTenantService tenantService;

    @Autowired(required = false)
    private ISysTenantPackageService tenantPackageService;

    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuList(Long userId) {
        return selectMenuList(new SysMenuBo(), userId);
    }

    /**
     * 根据用户查询系统菜单列表
     *
     * @param menu   菜单信息
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuVo> selectMenuList(SysMenuBo menu, Long userId) {
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
        List<SysMenu> menuList = baseMapper.selectList(queryWrapper);
        return menuConvert.entityListToVoList(menuList);
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
        // 这里简化处理，实际应该从角色菜单关联表中查询
        // 假设我们有一个方法可以查询角色对应的菜单ID
        // 暂时返回空集合
        return new HashSet<>();
    }

    /**
     * 根据用户ID查询菜单树信息
     * 超级管理员：返回所有菜单
     * 普通租户用户：套餐菜单 ∩ 用户角色菜单（取交集）
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenuDto> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        // 超级管理员看所有菜单
        if (SysUser.isAdmin(userId)) {
            menus = baseMapper.selectList(null);
        } else {
            // 1. 获取用户角色对应的菜单
            List<SysMenu> roleMenus = baseMapper.selectMenuTreeByUserId(userId);

            // 2. 获取租户套餐允许的菜单ID集合
            Set<Long> packageMenuIds = getPackageMenuIdsForUser(userId);

            // 3. 取交集：角色菜单 ⊆ 套餐菜单
            if (!packageMenuIds.isEmpty()) {
                menus = filterMenusByPackage(roleMenus, packageMenuIds);
            } else {
                menus = roleMenus;
            }
        }
        return buildMenuDtoTree(menus);
    }

    /**
     * 获取用户所属租户套餐包含的菜单ID集合
     */
    private Set<Long> getPackageMenuIdsForUser(Long userId) {
        if (tenantService == null || tenantPackageService == null) {
            return Collections.emptySet();
        }
        try {
            // 从 SecurityContext 获取当前登录用户信息
            BaseUserDetail loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getBaseUser() == null) {
                return Collections.emptySet();
            }
            String tenantId = loginUser.getBaseUser().getTenantId();
            if (StringUtils.isBlank(tenantId)) {
                return Collections.emptySet();
            }
            // 查租户信息获取套餐ID
            SysTenant tenant = tenantService.selectByTenantId(tenantId);
            if (tenant == null || tenant.getPackageId() == null) {
                return Collections.emptySet();
            }
            // 查套餐获取菜单ID列表
            List<Long> menuIds = tenantPackageService.getMenuIdsByPackageId(tenant.getPackageId());
            return new HashSet<>(menuIds);
        } catch (Exception e) {
            log.warn("获取租户套餐菜单失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 用套餐菜单ID过滤菜单列表（只保留套餐允许的菜单及其父菜单）
     */
    private List<SysMenu> filterMenusByPackage(List<SysMenu> roleMenus, Set<Long> packageMenuIds) {
        // 先找出所有在套餐中的菜单ID
        Set<Long> allowedIds = roleMenus.stream()
                .map(SysMenu::getMenuId)
                .filter(packageMenuIds::contains)
                .collect(Collectors.toSet());

        // 也需要包含这些菜单的父菜单（确保菜单树完整）
        Set<Long> parentIds = new HashSet<>();
        for (SysMenu menu : roleMenus) {
            if (allowedIds.contains(menu.getMenuId())) {
                // 追溯父菜单
                SysMenu parent = findInList(roleMenus, menu.getParentId());
                while (parent != null) {
                    parentIds.add(parent.getMenuId());
                    parent = findInList(roleMenus, parent.getParentId());
                }
            }
        }
        allowedIds.addAll(parentIds);

        return roleMenus.stream()
                .filter(m -> allowedIds.contains(m.getMenuId()))
                .collect(Collectors.toList());
    }

    private SysMenu findInList(List<SysMenu> list, Long menuId) {
        if (menuId == null || menuId == 0L) return null;
        for (SysMenu m : list) {
            if (m.getMenuId().equals(menuId)) return m;
        }
        return null;
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
    public List<SysMenuDto> buildMenus(List<SysMenuDto> menus) {
        // 这里直接返回传入的菜单列表，因为前端路由构建已经在selectMenuTreeByUserId中完成
        // 如果需要额外的路由处理，可以在这里添加
        return menus;
    }

    /**
     * 构建前端所需要树结构
     *
     * @param menus 菜单列表
     * @return 树结构列表
     */
    @Override
    public List<SysMenuVo> buildMenuTree(List<SysMenuVo> menus) {
        List<SysMenuVo> menuTree = new ArrayList<>();
        for (SysMenuVo menu : menus) {
            if (menu.getParentId() == null || menu.getParentId() == 0L) {
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
    public List<TreeSelect> buildMenuTreeSelect(List<SysMenuVo> menus) {
        List<SysMenuVo> menuTree = buildMenuTree(menus);
        return menuTree.stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    @Override
    public SysMenuVo selectMenuById(Long menuId) {
        SysMenu menu = baseMapper.selectById(menuId);
        return menuConvert.entityToVo(menu);
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
     * 新增保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    public int insertMenu(SysMenuBo bo) {
        // 校验菜单名称是否唯一
        if (!checkMenuNameUnique(bo)) {
            throw new ServiceException("新增菜单" + bo.getMenuName() + "失败，菜单名称已存在");
        }
        SysMenu menu = menuConvert.boToEntity(bo);
        return baseMapper.insert(menu) > 0 ? 1 : 0;
    }

    /**
     * 修改保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    public int updateMenu(SysMenuBo bo) {
        // 校验菜单名称是否唯一
        if (!checkMenuNameUnique(bo)) {
            throw new ServiceException("修改菜单" + bo.getMenuName() + "失败，菜单名称已存在");
        }
        SysMenu menu = menuConvert.boToEntity(bo);
        return baseMapper.updateById(menu) > 0 ? 1 : 0;
    }

    /**
     * 删除菜单管理信息
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
    public boolean checkMenuNameUnique(SysMenuBo menu) {
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
     * @param menus      菜单列表
     * @param parentMenu 父菜单
     * @return 菜单树
     */
    private SysMenuVo buildMenuTree(List<SysMenuVo> menus, SysMenuVo parentMenu) {
        List<SysMenuVo> children = new ArrayList<>();
        for (SysMenuVo menu : menus) {
            if (menu.getParentId().equals(parentMenu.getMenuId())) {
                children.add(buildMenuTree(menus, menu));
            }
        }
        parentMenu.setChildren(children);
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
     * @param menus      菜单列表
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
