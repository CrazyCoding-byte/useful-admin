package com.yzx.product.listen;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.yzx.common.aop.Idempotent;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import com.yzx.model.product.PmsSkuSaleAttrValue;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.product.entity.ProductEsDoc;
import com.yzx.product.repository.SkuRepository;
import com.yzx.product.service.PmsSkuSaleAttrValueService;
import com.yzx.product.service.SkuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductUpConsumer {

    @Autowired
    private SkuInfoService skuInfoMapper;

    @Autowired
    private SkuRepository productEsRepository; // Spring Data ES

    @Autowired
    private PmsSkuSaleAttrValueService skuSaleAttrMapper;
    @Autowired
    private IMqMessageService mqMessageService;

    @Idempotent( key = "'PRODUCT_UP_'+#spuId+'_'+#message.getMessageProperties().getHeader('spring_returned_message_correlation') ?: #message.getMessageProperties().getDeliveryTag()",
            message = "该SPU上架消息正在处理中，请勿重复消费",
            expireTime = 600 // 10分钟过期（覆盖业务最大执行时间）
    )
    @RabbitListener(queues = "product.up.queue")
    public void upSpuSyncEs(Message message, Channel channel) throws Exception {

        //获取唯一消费id
        String msgId=message.getMessageProperties().getCorrelationId();
        MqMessage mqmessage= mqMessageService.lambdaQuery().eq(MqMessage::getMsgId,msgId).one();
        long deliveryTag=message.getMessageProperties().getDeliveryTag();
        //消息已经完成->ack 不执行业务
        if(mqmessage!=null&& Objects.equals(mqmessage.getStatus(), MessageStatusEnum.SUCCESS.getCode())){
            log.warn("【重复消费】消息已处理，直接跳过，msgId:{}", msgId);
            channel.basicAck(deliveryTag,false);
            return;
        }
        // 2. 解析消息
        String content = new String(message.getBody());
        Long spuId = JSON.parseObject(content).getLong("spuId");
        try {
            // 1. 查该 spu 下所有 sku
            List<Long> skuIdList = skuInfoMapper.list(new LambdaQueryWrapper<SkuInfoEntity>().
                            eq(SkuInfoEntity::getSpuId, spuId)).
                    stream().map(item -> item.getSpuId()).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(skuIdList)) {
                log.error("spuId:{} 下无 sku", spuId);
                return;
            }

            // 2. 逐个同步到 ES
            for (Long skuId : skuIdList) {
                buildAndSaveEsDoc(skuId);
            }
           mqMessageService.updateStatus(msgId,MessageStatusEnum.SUCCESS);
            channel.basicAck(deliveryTag, false);
            log.info("spuId:{} 同步ES成功，消息tag:{} 已确认", spuId, deliveryTag);
        }catch (Exception e){
            log.error("spuId:{} 同步ES失败", spuId, e);
            // 判断是否可重试（例如网络异常可重试，参数异常不可重试）
            boolean retryable = isRetryable(e);
            if (retryable) {
                // 可重试：拒绝消息，重新入队（让MQ重试）
                channel.basicNack(deliveryTag, false, true);
                // 注意：消息表状态保持原有状态（可能是SUCCESS？但这里还未更新过，如果之前是SUCCESS不可能进这里）
                // 实际上消息表此时可能还是 INIT 或 FAIL，不更新，让定时任务兜底
            } else {
                // 不可重试：ACK移除消息，标记消息表为DEAD
                channel.basicAck(deliveryTag, false);
                mqMessageService.markAsDead(msgId);
            }
        }
    }
    private boolean isRetryable(Exception e) {
        // 根据异常类型判断
        return e instanceof java.net.ConnectException ||
                e instanceof org.springframework.dao.DataAccessException;
    }
    private void buildAndSaveEsDoc(Long skuId) {
        // 1. 查询 sku 基本信息
        SkuInfoEntity sku = skuInfoMapper.getById(skuId);

        // 2. 查询销售属性
        List<PmsSkuSaleAttrValue> saleAttrList =
                skuSaleAttrMapper.list(new LambdaQueryWrapper<PmsSkuSaleAttrValue>().eq(PmsSkuSaleAttrValue::getSkuId, skuId));
        if (CollectionUtils.isEmpty(saleAttrList)) {
            log.error("skuId:{} 销售属性为空", skuId);
            return;
        }
        // 3. 构造 ES 数据
        ProductEsDoc doc = new ProductEsDoc();
        doc.setId(sku.getSkuId());
        doc.setSpuId(sku.getSpuId());
        doc.setSkuName(sku.getSkuName());
        doc.setSkuTitle(sku.getSkuTitle());
        doc.setPrice(sku.getPrice());
        doc.setCatalogId(sku.getCatalogId());
        doc.setBrandId(sku.getBrandId());
        doc.setSaleCount(sku.getSaleCount());
        doc.setPublishStatus(1); // 已上架

        // 销售属性封装
        List<ProductEsDoc.SaleAttrEsVO> attrVoList = new ArrayList<>();
        for (PmsSkuSaleAttrValue attr : saleAttrList) {
            ProductEsDoc.SaleAttrEsVO vo = new ProductEsDoc.SaleAttrEsVO();
            vo.setAttrName(attr.getAttrName());
            vo.setAttrValue(attr.getAttrValue());
            attrVoList.add(vo);
        }
        doc.setSaleAttrs(attrVoList);

        // 4. 保存到 ES
        productEsRepository.save(doc);
        log.info("skuId:{}同步ES文档成功",skuId );
    }
}