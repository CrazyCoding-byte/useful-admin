package com.yzx.system.domain.convert;

import com.yzx.model.system.SysRole;
import com.yzx.system.domain.bo.SysRoleBo;
import com.yzx.system.domain.vo.SysRoleVo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 角色信息转换器
 *
 * @author yzx
 */
@Mapper(componentModel = "spring")
public interface SysRoleConvert {

    SysRoleConvert INSTANCE = Mappers.getMapper(SysRoleConvert.class);

    /**
     * SysRoleBo 转 SysRole
     *
     * @param bo 业务对象
     * @return 实体对象
     */
    SysRole boToEntity(SysRoleBo bo);

    /**
     * SysRole 转 SysRoleBo
     *
     * @param entity 实体对象
     * @return 业务对象
     */
    SysRoleBo entityToBo(SysRole entity);

    /**
     * SysRole 转 SysRoleVo
     *
     * @param entity 实体对象
     * @return 视图对象
     */
    SysRoleVo entityToVo(SysRole entity);

    /**
     * SysRole列表 转 SysRoleVo列表
     *
     * @param list 实体对象列表
     * @return 视图对象列表
     */
    List<SysRoleVo> entityListToVoList(List<SysRole> list);

}
