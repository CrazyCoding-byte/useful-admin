package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysRoleDept;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 角色与部门关联表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept>
{
    /**
     * 批量删除角色与部门关联
     * 
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    public int deleteRoleDeptByRoleIds(Long[] roleIds);
    
    /**
     * 批量删除角色与部门关联
     * 
     * @param deptIds 需要删除的部门ID
     * @return 结果
     */
    public int deleteRoleDeptByDeptIds(Long[] deptIds);
}
