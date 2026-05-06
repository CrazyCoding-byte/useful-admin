package com.yzx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.product.PmsAttr;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @className: AttrMapper
 * @author: yzx
 * @date: 2025/9/18 16:00
 * @Version: 1.0
 * @description:
 */
public interface PmsAttrMapper extends BaseMapper<PmsAttr> {
    List<Long> selectByids(@Param("ids") List<Long> attrIds);
}
