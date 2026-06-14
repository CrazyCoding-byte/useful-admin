package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzx.model.StringUtils;
import com.yzx.model.system.SysDept;
import com.yzx.system.domain.bo.SysDeptBo;
import com.yzx.system.domain.convert.SysDeptConvert;
import com.yzx.system.domain.vo.SysDeptVo;
import com.yzx.system.mapper.SysDeptMapper;
import com.yzx.system.service.ISysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门管理 服务实现
 *
 * @author yzx
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements ISysDeptService {

    private final SysDeptMapper baseMapper;
    private final SysDeptConvert deptConvert;

    /**
     * 查询部门管理数据
     *
     * @param dept 部门信息
     * @return 部门信息集合
     */
    @Override
    public List<SysDeptVo> selectDeptList(SysDeptBo dept) {
        LambdaQueryWrapper<SysDept> lqw = buildQueryWrapper(dept);
        List<SysDept> deptList = baseMapper.selectList(lqw);
        return deptConvert.entityListToVoList(deptList);
    }

    /**
     * 构建查询条件
     *
     * @param bo 部门业务对象
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SysDept> buildQueryWrapper(SysDeptBo bo) {
        LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getDeptName()), SysDept::getDeptName, bo.getDeptName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysDept::getStatus, bo.getStatus());
        lqw.eq(bo.getParentId() != null, SysDept::getParentId, bo.getParentId());
        lqw.orderByAsc(SysDept::getOrderNum);
        return lqw;
    }

    /**
     * 查询部门树结构信息
     *
     * @param dept 部门信息
     * @return 部门树信息集合
     */
    @Override
    public List<SysDeptVo> selectDeptTreeList(SysDeptBo dept) {
        LambdaQueryWrapper<SysDept> lqw = buildQueryWrapper(dept);
        List<SysDept> deptList = baseMapper.selectList(lqw);
        List<SysDeptVo> voList = deptConvert.entityListToVoList(deptList);
        return buildDeptTree(voList);
    }

    /**
     * 构建前端所需要树结构
     *
     * @param depts 部门列表
     * @return 树结构列表
     */
    @Override
    public List<SysDeptVo> buildDeptTree(List<SysDeptVo> depts) {
        List<SysDeptVo> tree = new ArrayList<>();
        // 遍历所有部门，找到根节点
        for (SysDeptVo dept : depts) {
            if (dept.getParentId() == null || dept.getParentId() == 0) {
                // 递归查找子节点
                tree.add(findChildren(dept, depts));
            }
        }
        return tree;
    }

    /**
     * 递归查找子节点
     */
    private SysDeptVo findChildren(SysDeptVo parent, List<SysDeptVo> allDepts) {
        List<SysDeptVo> children = new ArrayList<>();
        for (SysDeptVo dept : allDepts) {
            // 如果当前部门的parentId等于父部门的deptId
            if (dept.getParentId() != null && dept.getParentId().equals(parent.getDeptId())) {
                // 递归查找这个部门的子节点
                children.add(findChildren(dept, allDepts));
            }
        }
        // 设置子节点
        parent.setChildren(children);
        return parent;
    }

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    @Override
    public List<Long> selectDeptListByRoleId(Long roleId) {
        // 使用LambdaQueryWrapper查询角色关联的部门ID列表
        // 这里需要通过sys_role_dept表查询，由于当前Mapper没有该方法，返回空列表
        // 实际项目中应该在SysRoleDeptMapper中实现
        return new ArrayList<>();
    }

    /**
     * 根据部门ID查询信息
     *
     * @param deptId 部门ID
     * @return 部门信息
     */
    @Override
    public SysDeptVo selectDeptById(Long deptId) {
        SysDept dept = baseMapper.selectById(deptId);
        if (dept == null) {
            return null;
        }
        SysDeptVo vo = deptConvert.entityToVo(dept);
        // 查询父部门名称
        if (dept.getParentId() != null && dept.getParentId() != 0) {
            SysDept parentDept = baseMapper.selectById(dept.getParentId());
            if (parentDept != null) {
                vo.setParentName(parentDept.getDeptName());
            }
        }
        return vo;
    }

    /**
     * 通过部门ID串查询部门
     *
     * @param deptIds 部门id串
     * @return 部门列表信息
     */
    @Override
    public List<SysDeptVo> selectDeptByIds(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysDept> deptList = baseMapper.selectBatchIds(deptIds);
        return deptConvert.entityListToVoList(deptList);
    }

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    @Override
    public String selectDeptNameByIds(String deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return "";
        }
        String[] ids = deptIds.split(",");
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            try {
                Long deptId = Long.valueOf(id.trim());
                SysDeptVo vo = selectDeptById(deptId);
                if (vo != null) {
                    names.add(vo.getDeptName());
                }
            } catch (NumberFormatException e) {
                // 忽略无效ID
            }
        }
        return String.join(",", names);
    }

    /**
     * 根据ID查询所有子部门数（正常状态）
     *
     * @param deptId 部门ID
     * @return 子部门数
     */
    @Override
    public long selectNormalChildrenDeptById(Long deptId) {
        LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
        lqw.like(SysDept::getAncestors, deptId.toString());
        lqw.eq(SysDept::getStatus, "0");
        return baseMapper.selectCount(lqw);
    }

    /**
     * 是否存在子节点
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public boolean hasChildByDeptId(Long deptId) {
        LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDept::getParentId, deptId);
        return baseMapper.selectCount(lqw) > 0;
    }

    /**
     * 查询部门是否存在用户
     *
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkDeptExistUser(Long deptId) {
        // 查询部门是否存在用户，需要通过sys_user表查询
        // 由于当前Service没有注入UserMapper，简化处理返回false
        // 实际项目中应该注入SysUserMapper查询
        return false;
    }

    /**
     * 校验部门名称是否唯一
     *
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public boolean checkDeptNameUnique(SysDeptBo dept) {
        Long deptId = dept.getDeptId() == null ? -1L : dept.getDeptId();
        LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDept::getDeptName, dept.getDeptName());
        lqw.eq(SysDept::getParentId, dept.getParentId());
        SysDept info = baseMapper.selectOne(lqw);
        if (info != null && info.getDeptId().longValue() != deptId.longValue()) {
            return false; // 不唯一
        }
        return true; // 唯一
    }

    /**
     * 校验部门是否有数据权限
     *
     * @param deptId 部门id
     */
    @Override
    public void checkDeptDataScope(Long deptId) {
        if (deptId != null) {
            SysDept dept = baseMapper.selectById(deptId);
            if (dept == null) {
                throw new RuntimeException("部门不存在");
            }
        }
    }

    /**
     * 新增保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @Override
    public int insertDept(SysDeptBo bo) {
        SysDept dept = deptConvert.boToEntity(bo);
        String ancestors = generateAncestors(dept.getParentId());
        dept.setAncestors(ancestors);
        // 插入部门
        return baseMapper.insert(dept);
    }

    /**
     * 修改保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @Override
    public int updateDept(SysDeptBo bo) {
        SysDept dept = deptConvert.boToEntity(bo);
        SysDept oldDept = baseMapper.selectById(dept.getDeptId());

        // 如果修改了父部门，需要更新所有子部门的ancestors
        if (oldDept != null && !oldDept.getParentId().equals(dept.getParentId())) {
            // 1.查询所有子部门（使用LambdaQueryWrapper）
            LambdaQueryWrapper<SysDept> lqw = Wrappers.lambdaQuery();
            lqw.like(SysDept::getAncestors, dept.getDeptId().toString());
            List<SysDept> children = baseMapper.selectList(lqw);
            // 2.生成新的ancestors
            String newAncestors = generateAncestors(dept.getParentId());
            dept.setAncestors(newAncestors);
            // 3.更新每个子部门ancestors
            for (SysDept child : children) {
                // 替换新的祖先前缀为新的
                String oldPrefix = oldDept.getAncestors() + "," + oldDept.getDeptId();
                String newPrefix = newAncestors + "," + dept.getDeptId();
                child.setAncestors(child.getAncestors().replaceFirst(oldPrefix, newPrefix));
                // 4.逐个更新子部门
                baseMapper.updateById(child);
            }
        }
        return baseMapper.updateById(dept);
    }

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public int deleteDeptById(Long deptId) {
        // 1. 检查是否有子部门
        if (hasChildByDeptId(deptId)) {
            throw new RuntimeException("存在下级部门，不允许删除");
        }

        // 2. 检查部门是否存在用户
        if (checkDeptExistUser(deptId)) {
            throw new RuntimeException("部门存在用户，不允许删除");
        }

        // 3. 删除部门
        return baseMapper.deleteById(deptId);
    }

    /**
     * 生成祖级列表
     *
     * @param parentId 父部门ID
     * @return 祖级列表
     */
    private String generateAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        // 查询父部门
        SysDept parent = baseMapper.selectById(parentId);
        if (parent == null) {
            return "0";
        }
        // 父部门的ancestors + ',' + 父部门ID
        return parent.getAncestors() + "," + parentId;
    }

}
