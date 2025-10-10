package com.yzx.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.wms.WareSkuEntity;

/**
 * @className: WareMapper
 * @author: yzx
 * @date: 2025/9/2 7:22
 * @Version: 1.0
 * @description:
 */
public interface WareMapper extends BaseMapper<WareSkuEntity> {
    Long getSkuStock(Long skuid);
}
