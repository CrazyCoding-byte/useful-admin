package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.system.SysDept;
import com.yzx.system.mapper.SysDeptMapper;
import com.yzx.system.service.ISysDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @className: SysDeptServiceImpl
 * @author: yzx
 * @date: 2026/5/21 14:27
 * @Version: 1.0
 * @description:
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<SysDept> selectDeptList(SysDept dept) {
        List<SysDept> depts = list();
        return buildDeptTree(depts);
    }

    @Override
    public List<SysDept> selectDeptTreeList(SysDept dept) {
        // 查询部门列表并构建树形结构
        List<SysDept> depts = baseMapper.selectDeptList(dept);
        return buildDeptTree(depts);
    }

    @Override
    public List<SysDept> buildDeptTree(List<SysDept> depts) {
        List<SysDept> tree = new ArrayList<>();
        //遍历所有部门,找到根节点
        for (SysDept dept : depts) {
            if (dept.getParentId() == 0) {
                //递归查找子节点
                tree.add(findChildren(dept, depts));
            }
        }
        return tree;
    }

    /**
     * 递归查找子节点
     */
    private SysDept findChildren(SysDept parent, List<SysDept> allDepts) {
        List<SysDept> children = new ArrayList<>();
        for (SysDept dept : allDepts) {
            //如果当前部门的parentId等于父部门的deptID
            if (dept.getParentId().equals(parent.getDeptId())) {
                //递归查找这个部门的子节点
                children.add(findChildren(dept, allDepts));
            }
        }
        //设置子节点
        parent.setChildren(children);
        return parent;
    }

    @Override
    public List<Long> selectDeptListByRoleId(Long roleId) {
        // 根据角色ID查询部门列表
        SysDept dept = new SysDept();
        return baseMapper.selectDeptListByRoleId(roleId, false);
    }

    @Override
    public SysDept selectDeptById(Long deptId) {
        // 根据部门ID查询部门信息
        return baseMapper.selectById(deptId);
    }

    @Override
    public int selectNormalChildrenDeptById(Long deptId) {
        // 统计正常状态的子部门数量
        List<SysDept> children = baseMapper.selectChildrenDeptById(deptId);
        return children != null ? (int) children.stream()
                .filter(dept -> "0".equals(dept.getStatus()))
                .count() : 0;
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        // 检查部门是否有子部门
        List<SysDept> children = baseMapper.selectChildrenDeptById(deptId);
        return children != null && !children.isEmpty();
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        // 检查部门是否存在用户
        int count = baseMapper.countUserByDeptId(deptId);
        return count > 0;
    }

    @Override
    public boolean checkDeptNameUnique(SysDept dept) {
        // 检查部门名称是否唯一
        Long deptId = dept.getDeptId() == null ? -1L : dept.getDeptId();
        SysDept info = baseMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (info != null && info.getDeptId().longValue() != deptId.longValue()) {
            return false; // 不唯一
        }
        return true; // 唯一
    }

    @Override
    public void checkDeptDataScope(Long deptId) {
        // 检查数据权限（如果有数据权限注解，这里可以添加额外校验）
        if (deptId != null) {
            SysDept dept = baseMapper.selectById(deptId);
            if (dept == null) {
                throw new RuntimeException("部门不存在");
            }
        }
    }

    @Override

    public int insertDept(SysDept dept) {
        String ancestors = generateAncestors(dept.getParentId());
        dept.setAncestors(ancestors);
        //2.插入部门
        int rows = baseMapper.insert(dept);
        return rows;
    }

    @Override
    public int updateDept(SysDept dept) {
        SysDept oldDept = baseMapper.selectById(dept.getDeptId());

        //如果修改了父部门,需要更新所有子部门的ancestors
        if (!oldDept.getParentId().equals(dept.getParentId())) {
            //1.查询所有子部门
            List<SysDept> children = baseMapper.selectChildrenDeptById(dept.getDeptId());
            //2.生成新的ancestors
            String newAncestors = generateAncestors(dept.getParentId());
            //3.更新每个子部门ancestors
            for (SysDept child : children) {
                //替换新的祖先前缀为新的
                String oldPrefix = oldDept.getAncestors() + "," + oldDept.getDeptId();
                String newPrefix = newAncestors + "," + dept.getDeptId();
                child.setAncestors(child.getAncestors().replaceFirst(oldPrefix, newPrefix));
            }
            //4.批量更新子部门
            if (!children.isEmpty()) {
                baseMapper.updateDeptChildren(children);
            }

        }
        return baseMapper.updateById(dept);
    }

    @Override
    public int deleteDeptById(Long deptId) {
        // 1. 检查是否有子部门
        if (hasChildByDeptId(deptId)) {
            throw new RuntimeException("存在下级部门,不允许删除");
        }
        
        // 2. 检查部门是否存在用户
        if (checkDeptExistUser(deptId)) {
            throw new RuntimeException("部门存在用户,不允许删除");
        }
        
        // 3. 删除部门
        return baseMapper.deleteById(deptId);
    }

    @Override
    public String generateAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        //查询父部门
        SysDept parent = baseMapper.selectById(parentId);
        //父部门的ancestors+','+父部门ID
        return parent.getAncestors() + "," + parentId;
    }
}
