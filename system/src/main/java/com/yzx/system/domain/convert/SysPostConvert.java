package com.yzx.system.domain.convert;

import com.yzx.model.system.SysPost;
import com.yzx.system.domain.bo.SysPostBo;
import com.yzx.system.domain.vo.SysPostVo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 岗位信息转换器
 *
 * @author yzx
 */
@Mapper(componentModel = "spring")
public interface SysPostConvert {

    SysPostConvert INSTANCE = Mappers.getMapper(SysPostConvert.class);

    /**
     * Bo 转 Entity
     */
    SysPost boToEntity(SysPostBo bo);

    /**
     * Entity 转 Vo
     */
    SysPostVo entityToVo(SysPost entity);

    /**
     * Entity 转 Bo
     */
    SysPostBo entityToBo(SysPost entity);

    /**
     * Vo 转 Entity
     */
    SysPost voToEntity(SysPostVo vo);

    /**
     * Bo 转 Vo
     */
    SysPostVo boToVo(SysPostBo bo);

    /**
     * Entity列表 转 Vo列表
     */
    List<SysPostVo> entityListToVoList(List<SysPost> entityList);

    /**
     * Bo列表 转 Entity列表
     */
    List<SysPost> boListToEntityList(List<SysPostBo> boList);

}
