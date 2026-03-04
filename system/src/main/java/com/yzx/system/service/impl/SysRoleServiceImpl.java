package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.constant.UserConstants;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.SysRole;
import com.yzx.model.system.SysRoleDept;
import com.yzx.model.system.SysRoleMenu;
import com.yzx.model.system.SysUserRole;
import com.yzx.system.mapper.SysRoleDeptMapper;
import com.yzx.system.mapper.SysRoleMapper;
import com.yzx.system.mapper.SysRoleMenuMapper;
import com.yzx.system.mapper.SysUserRoleMapper;
import com.yzx.system.service.ISysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 角色 业务层处理
 *
 * @author ruoyi
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {
    
    @Autowired
    private SysRoleMenuMapper roleMenuMapper;
    
    @Autowired
    private SysRoleDeptMapper roleDeptMapper;
    
    @Autowired
    private SysUserRoleMapper userRoleMapper;

    /**
     * 根据条件分页查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    public List<SysRole> selectRoleList(SysRole role) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.isNotBlank(role.getRoleName())) {
            queryWrapper.like(SysRole::getRoleName, role.getRoleName());
        }
        if (StringUtils.isNotBlank(role.getRoleKey())) {
            queryWrapper.like(SysRole::getRoleKey, role.getRoleKey());
        }
        if (StringUtils.isNotBlank(role.getStatus())) {
            queryWrapper.eq(SysRole::getStatus, role.getStatus());
        }
        
        queryWrapper.orderByAsc(SysRole::getRoleSort);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRolesByUserId(Long userId) {
        return baseMapper.selectRolesByUserId(userId);
    }

    /**
     * 根据用户ID查询角色权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        return new java.util.HashSet<>(baseMapper.selectRolePermissionByUserId(userId));
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRoleAll() {
        return baseMapper.selectList(null);
    }

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        List<SysRole> roles = baseMapper.selectRolesByUserId(userId);
        return roles.stream().map(SysRole::getRoleId).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRole selectRoleById(Long roleId) {
        return baseMapper.selectById(roleId);
    }

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRole role) {
        Long roleId = role.getRoleId() == null ? -1L : role.getRoleId();
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getRoleName, role.getRoleName());
        queryWrapper.ne(SysRole::getRoleId, roleId);
        return baseMapper.selectOne(queryWrapper) == null;
    }

    /**
     * 校验角色权限是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRole role) {
        Long roleId = role.getRoleId() == null ? -1L : role.getRoleId();
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getRoleKey, role.getRoleKey());
        queryWrapper.ne(SysRole::getRoleId, roleId);
        return baseMapper.selectOne(queryWrapper) == null;
    }

    /**
     * 校验角色是否允许操作
     *
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRole role) {
        if (role.getRoleId() != null && role.isAdmin()) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleIds 角色id
     */
    @Override
    public void checkRoleDataScope(Long... roleIds) {
        // 这里简化处理，实际应该校验当前用户是否有权限操作这些角色
        // 假设我们有一个权限校验机制
        // 暂时不做具体实现
    }

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public int countUserRoleByRoleId(Long roleId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        return userRoleMapper.selectCount(queryWrapper);
    }

    /**
     * 新增保存角色信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int insertRole(SysRole role) {
        // 校验角色名称是否唯一
        if (!checkRoleNameUnique(role)) {
            throw new ServiceException("新增角色" + role.getRoleName() + "失败，角色名称已存在");
        }
        // 校验角色权限是否唯一
        if (!checkRoleKeyUnique(role)) {
            throw new ServiceException("新增角色" + role.getRoleName() + "失败，角色权限已存在");
        }
        
        // 新增角色
        int rows = baseMapper.insert(role) > 0 ? 1 : 0;
        
        // 新增角色与菜单关联
        if (rows > 0 && role.getMenuIds() != null) {
            insertRoleMenu(role);
        }
        
        // 新增角色与部门关联
        if (rows > 0 && role.getDeptIds() != null) {
            insertRoleDept(role);
        }
        
        return rows;
    }

    /**
     * 修改保存角色信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int updateRole(SysRole role) {
        // 校验角色名称是否唯一
        if (!checkRoleNameUnique(role)) {
            throw new ServiceException("修改角色" + role.getRoleName() + "失败，角色名称已存在");
        }
        // 校验角色权限是否唯一
        if (!checkRoleKeyUnique(role)) {
            throw new ServiceException("修改角色" + role.getRoleName() + "失败，角色权限已存在");
        }
        
        // 修改角色
        int rows = baseMapper.updateById(role) > 0 ? 1 : 0;
        
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleIds(new Long[]{role.getRoleId()});
        
        // 新增角色与菜单关联
        if (rows > 0 && role.getMenuIds() != null) {
            insertRoleMenu(role);
        }
        
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(new Long[]{role.getRoleId()});
        
        // 新增角色与部门关联
        if (rows > 0 && role.getDeptIds() != null) {
            insertRoleDept(role);
        }
        
        return rows;
    }

    /**
     * 修改角色状态
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public int updateRoleStatus(SysRole role) {
        return baseMapper.updateById(role) > 0 ? 1 : 0;
    }

    /**
     * 修改数据权限信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int authDataScope(SysRole role) {
        // 修改角色信息
        int rows = baseMapper.updateById(role) > 0 ? 1 : 0;
        
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(new Long[]{role.getRoleId()});
        
        // 新增角色与部门关联
        if (rows > 0 && role.getDeptIds() != null) {
            insertRoleDept(role);
        }
        
        return rows;
    }

    /**
     * 通过角色ID删除角色
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteRoleById(Long roleId) {
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleIds(new Long[]{roleId});
        
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(new Long[]{roleId});
        
        // 删除角色
        return baseMapper.deleteById(roleId) > 0 ? 1 : 0;
    }

    /**
     * 批量删除角色信息
     *
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteRoleByIds(Long[] roleIds) {
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleIds(roleIds);
        
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(roleIds);
        
        // 删除角色
        return baseMapper.deleteBatchIds(java.util.Arrays.asList(roleIds)) > 0 ? 1 : 0;
    }

    /**
     * 取消授权用户角色
     *
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    @Override
    public int deleteAuthUser(SysUserRole userRole) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userRole.getUserId());
        queryWrapper.eq(SysUserRole::getRoleId, userRole.getRoleId());
        return userRoleMapper.delete(queryWrapper);
    }

    /**
     * 批量取消授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要取消授权的用户数据ID
     * @return 结果
     */
    @Override
    public int deleteAuthUsers(Long roleId, Long[] userIds) {
        return userRoleMapper.deleteUserRoleByUserIds(userIds);
    }

    /**
     * 批量选择授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要删除的用户数据ID
     * @return 结果
     */
    @Override
    public int insertAuthUsers(Long roleId, Long[] userIds) {
        List<SysUserRole> list = new java.util.ArrayList<>();
        for (Long userId : userIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            list.add(userRole);
        }
        return userRoleMapper.batchUserRole(list);
    }

    /**
     * 新增角色菜单关联
     *
     * @param role 角色信息
     */
    private void insertRoleMenu(SysRole role) {
        for (Long menuId : role.getMenuIds()) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(role.getRoleId());
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    /**
     * 新增角色部门关联
     *
     * @param role 角色信息
     */
    private void insertRoleDept(SysRole role) {
        for (Long deptId : role.getDeptIds()) {
            SysRoleDept roleDept = new SysRoleDept();
            roleDept.setRoleId(role.getRoleId());
            roleDept.setDeptId(deptId);
            roleDeptMapper.insert(roleDept);
        }
    }
}
