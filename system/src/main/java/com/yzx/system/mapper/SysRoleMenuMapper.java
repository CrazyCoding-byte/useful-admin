package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色与菜单关联表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu>
{
    /**
     * 批量删除角色与菜单关联
     * 
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    public int deleteRoleMenuByRoleIds(Long[] roleIds);
}
