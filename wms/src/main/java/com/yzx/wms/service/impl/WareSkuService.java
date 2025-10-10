package com.yzx.wms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.OrderItemVo;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.wms.WareOrderTaskDetailEntity;
import com.yzx.model.wms.WareOrderTaskEntity;
import com.yzx.model.wms.WareSkuEntity;
import com.yzx.model.wms.vo.SkuHasStockVo;
import com.yzx.wms.mapper.WareMapper;
import com.yzx.wms.mapper.WareSkuDao;
import com.yzx.wms.mq.StockDetailTo;
import com.yzx.wms.mq.StockLockedTo;
import com.yzx.wms.service.IWareSkuService;
import com.yzx.wms.service.WareOrderTaskDetailService;
import com.yzx.wms.service.WareOrderTaskService;
import lombok.Data;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @className: WareSkuService
 * @author: yzx
 * @date: 2025/9/2 7:21
 * @Version: 1.0
 * @description:
 */
@Service
public class WareSkuService extends ServiceImpl<WareMapper, WareSkuEntity> implements IWareSkuService {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private WareOrderTaskService wareOrderTaskService;
    @Autowired
    private WareSkuDao wareSkuDao;
    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Override
    public boolean orderLockStock(WareSkuLockVo vo) {
        /**
         * 保存库存工作单详情信息
         * 追溯
         */
        WareOrderTaskEntity wareOrderTaskEntity = new WareOrderTaskEntity();
        wareOrderTaskEntity.setOrderSn(vo.getOrderSn());
        wareOrderTaskEntity.setCreateTime(new Date());
        wareOrderTaskService.save(wareOrderTaskEntity);

        List<OrderItemVo> locks = vo.getLocks();
        List<SkuWareHasStock> skuWareHasStocks = locks.stream().map(item -> {
            SkuWareHasStock stock = new SkuWareHasStock();
            Long skuId = item.getSkuId();
            stock.setNum(item.getCount());
            //查询这个商品在那个仓库有库存
            List<Long> wareIdList = wareSkuDao.listWareIdHasSkuStock(skuId);
            stock.setWareId(wareIdList);
            return stock;
        }).collect(Collectors.toList());

        //2. 检查每个商品是否都有库存，有就锁定库存
        for (SkuWareHasStock hasStock : skuWareHasStocks) {
            boolean skuStocked = false;
            Long skuid = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();
            if (CollectionUtils.isEmpty(wareIds)) {
                throw new NoStockException(skuid);
            }
            for (Long wareId : wareIds) {
                Long count = wareSkuDao.lockSkuStock(skuid, wareId, hasStock.getNum());
                if (count == 1) {
                    skuStocked = true;
                    WareOrderTaskDetailEntity wareOrderTaskDetailEntity = WareOrderTaskDetailEntity.builder()
                            .skuId(skuid).
                            skuName("")
                            .skuNum(hasStock.getNum())
                            .taskId(wareOrderTaskEntity.getId())
                            .wareId(wareId)
                            .lockStatus(1).build();
                    wareOrderTaskDetailService.save(wareOrderTaskDetailEntity);
                    //告诉MQ库存锁定成功
                    StockLockedTo lockedTo = new StockLockedTo();
                    lockedTo.setId(wareOrderTaskEntity.getId());
                    StockDetailTo detailTo = new StockDetailTo();
                    BeanUtils.copyProperties(wareOrderTaskDetailEntity, detailTo);
                    lockedTo.setDetailTo(detailTo);
                    rabbitTemplate.convertAndSend("stock-event-exchange", "stock.locked", lockedTo);
                    break;
                } else {
                    //当前仓库锁失败，尝试下一个仓库
                }
            }
            if (skuStocked == false) {
                //当前商品所有仓库都没有锁住
                throw new NoStockException(skuid);
            }
        }
        return false;
    }

    @Override
    public List<SkuHasStockVo> getSkusHasStock(List<Long> skuIds) {
        List<SkuHasStockVo> stockVos = new ArrayList<>();
        for (Long skuid : skuIds) {
            SkuHasStockVo skuHasStockVo = new SkuHasStockVo();
            Long count = baseMapper.getSkuStock(skuid);
            skuHasStockVo.setSkuId(skuid);
            skuHasStockVo.setHasStock(count == null ? false : count > 0);
            stockVos.add(skuHasStockVo);
        }
        return stockVos;
    }

    @Data
    class SkuWareHasStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }
}
