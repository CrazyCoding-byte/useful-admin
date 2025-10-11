package com.yzx.wms.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.apiclient.api.OrderFeignService;
import com.yzx.model.AjaxResult;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.OrderItemVo;
import com.yzx.model.order.OrderVo;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.order.to.OrderTo;
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
    @Autowired
    private OrderFeignService orderFeignService;

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

    @Override
    public void unlockStock(StockLockedTo to) {
//库存工作单的id
        StockDetailTo detail = to.getDetailTo();
        Long detailId = detail.getId();

        /**
         * 解锁
         * 1、查询数据库关于这个订单锁定库存信息
         *   有：证明库存锁定成功了
         *      解锁：订单状况
         *          1、没有这个订单，必须解锁库存
         *          2、有这个订单，不一定解锁库存
         *              订单状态：已取消：解锁库存
         *                      已支付：不能解锁库存
         */
        WareOrderTaskDetailEntity taskDetailInfo = wareOrderTaskDetailService.getById(detailId);
        if (taskDetailInfo != null) {
            //查出wms_ware_order_task工作单的信息
            Long id = to.getId();
            WareOrderTaskEntity orderTaskInfo = wareOrderTaskService.getById(id);
            //获取订单号查询订单状态
            String orderSn = orderTaskInfo.getOrderSn();
            //远程查询订单信息
            AjaxResult orderData = orderFeignService.getOrderStatus(orderSn);
            if (orderData.getCode() == 0) {
                //订单数据返回成功
                OrderVo orderInfo = orderData.getData("data", new TypeReference<OrderVo>() {
                });
                //判断订单状态是否已取消或者支付或者订单不存在
                if (orderInfo == null || orderInfo.getStatus() == 4) {
                    //订单已被取消，才能解锁库存
                    if (taskDetailInfo.getLockStatus() == 1) {
                        //当前库存工作单详情状态1，已锁定，但是未解锁才可以解锁
                        unLockStock(detail.getSkuId(), detail.getWareId(), detail.getSkuNum(), detailId);
                    }
                }
            } else {
                //消息拒绝以后重新放在队列里面，让别人继续消费解锁
                //远程调用服务失败
                throw new RuntimeException("远程调用服务失败");
            }
        } else {
            //无需解锁
        }
    }

    @Override
    public void unlockStock(OrderTo to) {

        String orderSn = to.getOrderSn();
        //查一下最新的库存解锁状态，防止重复解锁库存
        WareOrderTaskEntity orderTaskEntity = wareOrderTaskService.getOrderTaskByOrderSn(orderSn);

        //按照工作单的id找到所有 没有解锁的库存，进行解锁
        Long id = orderTaskEntity.getId();
        List<WareOrderTaskDetailEntity> list = wareOrderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>()
                .eq("task_id", id).eq("lock_status", 1));

        for (WareOrderTaskDetailEntity taskDetailEntity : list) {
            unLockStock(taskDetailEntity.getSkuId(),
                    taskDetailEntity.getWareId(),
                    taskDetailEntity.getSkuNum(),
                    taskDetailEntity.getId());
        }
    }

    /**
     * 解锁库存的方法
     * @param skuId
     * @param wareId
     * @param num
     * @param taskDetailId
     */
    public void unLockStock(Long skuId, Long wareId, Integer num, Long taskDetailId) {

        //库存解锁
        wareSkuDao.unLockStock(skuId, wareId, num);

        //更新工作单的状态
        WareOrderTaskDetailEntity taskDetailEntity = new WareOrderTaskDetailEntity();
        taskDetailEntity.setId(taskDetailId);
        //变为已解锁
        taskDetailEntity.setLockStatus(2);
        wareOrderTaskDetailService.updateById(taskDetailEntity);

    }

    @Data
    class SkuWareHasStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }
}
