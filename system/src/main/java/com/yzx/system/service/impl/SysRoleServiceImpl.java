package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.yzx.model.constant.UserConstants;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.SysRole;
import com.yzx.model.system.SysRoleDept;
import com.yzx.model.system.SysRoleMenu;
import com.yzx.model.system.SysUserRole;
import com.yzx.system.domain.bo.SysRoleBo;
import com.yzx.system.domain.convert.SysRoleConvert;
import com.yzx.system.domain.vo.SysRoleVo;
import com.yzx.system.mapper.SysRoleDeptMapper;
import com.yzx.system.mapper.SysRoleMapper;
import com.yzx.system.mapper.SysRoleMenuMapper;
import com.yzx.system.mapper.SysUserRoleMapper;
import com.yzx.system.service.ISysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色 业务层处理
 *
 * @author ruoyi
 */
@Service
public class SysRoleServiceImpl implements ISysRoleService {

    @Autowired
    private SysRoleMapper baseMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysRoleDeptMapper roleDeptMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleConvert roleConvert;

    /**
     * 根据条件分页查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    public List<SysRoleVo> selectRoleList(SysRoleBo role) {
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
        List<SysRole> list = baseMapper.selectList(queryWrapper);
        return roleConvert.entityListToVoList(list);
    }

    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesByUserId(Long userId) {
        List<SysRole> roles = baseMapper.selectRolesByUserId(userId);
        return roleConvert.entityListToVoList(roles);
    }

    /**
     * 根据用户ID查询角色列表(包含被授权状态)
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesAuthByUserId(Long userId) {
        List<SysRoleVo> userRoles = selectRolesByUserId(userId);
        List<SysRoleVo> roles = selectRoleAll();
        // 使用HashSet提高查找效率
        Set<Long> userRoleIds = userRoles.stream().map(SysRoleVo::getRoleId).collect(Collectors.toSet());
        for (SysRoleVo role : roles) {
            if (userRoleIds.contains(role.getRoleId())) {
                role.setFlag(true);
            }
        }
        return roles;
    }

    /**
     * 根据用户ID查询角色权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        return new HashSet<>(baseMapper.selectRolePermissionByUserId(userId));
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRoleAll() {
        return selectRoleList(new SysRoleBo());
    }

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        List<SysRoleVo> roles = selectRolesByUserId(userId);
        return roles.stream().map(SysRoleVo::getRoleId).collect(Collectors.toList());
    }

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRoleVo selectRoleById(Long roleId) {
        SysRole role = baseMapper.selectById(roleId);
        return roleConvert.entityToVo(role);
    }

    /**
     * 通过角色ID串查询角色
     *
     * @param roleIds 角色ID串
     * @return 角色列表信息
     */
    @Override
    public List<SysRoleVo> selectRoleByIds(List<Long> roleIds) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getStatus, UserConstants.NORMAL);
        queryWrapper.in(roleIds != null && !roleIds.isEmpty(), SysRole::getRoleId, roleIds);
        List<SysRole> list = baseMapper.selectList(queryWrapper);
        return roleConvert.entityListToVoList(list);
    }

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRoleBo role) {
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
    public boolean checkRoleKeyUnique(SysRoleBo role) {
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
    public void checkRoleAllowed(SysRoleBo role) {
        if (role.getRoleId() != null && role.isAdmin()) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleId 角色id
     */
    @Override
    public void checkRoleDataScope(Long roleId) {
        if (roleId == null) {
            return;
        }
        checkRoleDataScope(Collections.singletonList(roleId));
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleIds 角色ID列表（支持传单个ID）
     */
    @Override
    public void checkRoleDataScope(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // 这里简化处理，实际应该校验当前用户是否有权限操作这些角色
        // 暂时不做具体实现
    }

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public long countUserRoleByRoleId(Long roleId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        return userRoleMapper.selectCount(queryWrapper);
    }

    /**
     * 新增保存角色信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int insertRole(SysRoleBo bo) {
        // 校验角色名称是否唯一
        if (!checkRoleNameUnique(bo)) {
            throw new ServiceException("新增角色" + bo.getRoleName() + "失败，角色名称已存在");
        }
        // 校验角色权限是否唯一
        if (!checkRoleKeyUnique(bo)) {
            throw new ServiceException("新增角色" + bo.getRoleName() + "失败，角色权限已存在");
        }

        // Bo转Entity
        SysRole role = roleConvert.boToEntity(bo);

        // 新增角色
        int rows = baseMapper.insert(role) > 0 ? 1 : 0;

        // 新增角色与菜单关联
        if (rows > 0 && bo.getMenuIds() != null) {
            insertRoleMenu(role.getRoleId(), bo.getMenuIds());
        }

        // 新增角色与部门关联
        if (rows > 0 && bo.getDeptIds() != null) {
            insertRoleDept(role.getRoleId(), bo.getDeptIds());
        }

        return rows;
    }

    /**
     * 修改保存角色信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int updateRole(SysRoleBo bo) {
        // 校验角色名称是否唯一
        if (!checkRoleNameUnique(bo)) {
            throw new ServiceException("修改角色" + bo.getRoleName() + "失败，角色名称已存在");
        }
        // 校验角色权限是否唯一
        if (!checkRoleKeyUnique(bo)) {
            throw new ServiceException("修改角色" + bo.getRoleName() + "失败，角色权限已存在");
        }

        // Bo转Entity
        SysRole role = roleConvert.boToEntity(bo);

        // 修改角色
        int rows = baseMapper.updateById(role) > 0 ? 1 : 0;

        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleIds(new Long[]{bo.getRoleId()});

        // 新增角色与菜单关联
        if (rows > 0 && bo.getMenuIds() != null) {
            insertRoleMenu(bo.getRoleId(), bo.getMenuIds());
        }

        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(new Long[]{bo.getRoleId()});

        // 新增角色与部门关联
        if (rows > 0 && bo.getDeptIds() != null) {
            insertRoleDept(bo.getRoleId(), bo.getDeptIds());
        }

        return rows;
    }

    /**
     * 修改角色状态
     *
     * @param roleId 角色ID
     * @param status 角色状态
     * @return 结果
     */
    @Override
    public int updateRoleStatus(Long roleId, String status) {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setStatus(status);
        return baseMapper.updateById(role) > 0 ? 1 : 0;
    }

    /**
     * 修改数据权限信息
     *
     * @param bo 角色信息
     * @return 结果
     */
    @Transactional
    @Override
    public int authDataScope(SysRoleBo bo) {
        // Bo转Entity
        SysRole role = roleConvert.boToEntity(bo);

        // 修改角色信息
        int rows = baseMapper.updateById(role) > 0 ? 1 : 0;

        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(new Long[]{bo.getRoleId()});

        // 新增角色与部门关联
        if (rows > 0 && bo.getDeptIds() != null) {
            insertRoleDept(bo.getRoleId(), bo.getDeptIds());
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
    public int deleteRoleByIds(List<Long> roleIds) {
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleIds(roleIds.toArray(new Long[0]));

        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleIds(roleIds.toArray(new Long[0]));

        // 删除角色
        return baseMapper.deleteBatchIds(roleIds) > 0 ? 1 : 0;
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
     * @param roleId  角色ID
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
     * @param roleId  角色ID
     * @param userIds 需要授权的用户数据ID
     * @return 结果
     */
    @Override
    public int insertAuthUsers(Long roleId, Long[] userIds) {
        List<SysUserRole> list = new ArrayList<>();
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
     * @param roleId  角色ID
     * @param menuIds 菜单ID数组
     */
    private void insertRoleMenu(Long roleId, Long[] menuIds) {
        for (Long menuId : menuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    /**
     * 新增角色部门关联
     *
     * @param roleId  角色ID
     * @param deptIds 部门ID数组
     */
    private void insertRoleDept(Long roleId, Long[] deptIds) {
        for (Long deptId : deptIds) {
            SysRoleDept roleDept = new SysRoleDept();
            roleDept.setRoleId(roleId);
            roleDept.setDeptId(deptId);
            roleDeptMapper.insert(roleDept);
        }
    }
}
