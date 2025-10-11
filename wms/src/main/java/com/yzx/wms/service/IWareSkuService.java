package com.yzx.wms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.order.to.OrderTo;
import com.yzx.model.wms.WareSkuEntity;
import com.yzx.model.wms.vo.SkuHasStockVo;
import com.yzx.wms.mq.StockLockedTo;

import java.util.List;

/**
 * @className: WareSkuService
 * @author: yzx
 * @date: 2025/9/2 7:18
 * @Version: 1.0
 * @description:
 */
public interface IWareSkuService extends IService<WareSkuEntity> {
    boolean orderLockStock(WareSkuLockVo vo);

    List<SkuHasStockVo> getSkusHasStock(List<Long> skuIds);

    void unlockStock(StockLockedTo to);

    void unlockStock(OrderTo to);
}
