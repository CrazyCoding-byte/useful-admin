package com.yzx.system.domain.convert;

import com.yzx.model.system.SysUser;
import com.yzx.system.domain.bo.SysUserBo;
import com.yzx.system.domain.vo.SysUserVo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 用户信息转换器
 *
 * @author yzx
 */
@Mapper(componentModel = "spring")
public interface SysUserConvert {

    SysUserConvert INSTANCE = Mappers.getMapper(SysUserConvert.class);

    /**
     * SysUserBo 转 SysUser
     *
     * @param bo 业务对象
     * @return 实体对象
     */
    SysUser boToEntity(SysUserBo bo);

    /**
     * SysUser 转 SysUserBo
     *
     * @param entity 实体对象
     * @return 业务对象
     */
    SysUserBo entityToBo(SysUser entity);

    /**
     * SysUser 转 SysUserVo
     *
     * @param entity 实体对象
     * @return 视图对象
     */
    SysUserVo entityToVo(SysUser entity);

    /**
     * SysUser列表 转 SysUserVo列表
     *
     * @param list 实体对象列表
     * @return 视图对象列表
     */
    List<SysUserVo> entityListToVoList(List<SysUser> list);

}
