package com.yzx.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.wms.WareSkuEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @className: WareSkuDao
 * @author: yzx
 * @date: 2025/9/4 10:26
 * @Version: 1.0
 * @description:
 */
@Mapper
public interface WareSkuDao extends BaseMapper<WareSkuEntity> {
    List<Long> listWareIdHasSkuStock(Long skuId);

    Long lockSkuStock(Long skuid, Long wareId, Integer num);

    void unLockStock(Long skuId, Long wareId, Integer num);
}
