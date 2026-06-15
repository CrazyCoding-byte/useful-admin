package com.yzx.system.domain.convert;

import com.yzx.model.system.SysDictType;
import com.yzx.system.domain.bo.SysDictTypeBo;
import com.yzx.system.domain.vo.SysDictTypeVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 字典类型对象转换器
 *
 * @author yzx
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysDictTypeConvert {

    /**
     * SysDictTypeBo 转 SysDictType
     *
     * @param bo 业务对象
     * @return 实体对象
     */
    SysDictType boToEntity(SysDictTypeBo bo);

    /**
     * SysDictType 转 SysDictTypeBo
     *
     * @param entity 实体对象
     * @return 业务对象
     */
    SysDictTypeBo entityToBo(SysDictType entity);

    /**
     * SysDictType 转 SysDictTypeVo
     *
     * @param entity 实体对象
     * @return 视图对象
     */
    SysDictTypeVo entityToVo(SysDictType entity);

    /**
     * SysDictTypeVo 转 SysDictType
     *
     * @param vo 视图对象
     * @return 实体对象
     */
    SysDictType voToEntity(SysDictTypeVo vo);

    /**
     * SysDictTypeBo 转 SysDictTypeVo
     *
     * @param bo 业务对象
     * @return 视图对象
     */
    SysDictTypeVo boToVo(SysDictTypeBo bo);

    /**
     * 实体列表转视图对象列表
     *
     * @param list 实体列表
     * @return 视图对象列表
     */
    List<SysDictTypeVo> entityListToVoList(List<SysDictType> list);

}
