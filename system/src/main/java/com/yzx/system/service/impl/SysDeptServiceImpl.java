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
        return Collections.emptyList();
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
        return Collections.emptyList();
    }

    @Override
    public SysDept selectDeptById(Long deptId) {
        return null;
    }

    @Override
    public int selectNormalChildrenDeptById(Long deptId) {
        return 0;
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        return false;
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        return false;
    }

    @Override
    public boolean checkDeptNameUnique(SysDept dept) {
        return false;
    }

    @Override
    public void checkDeptDataScope(Long deptId) {

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
        return 0;
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
