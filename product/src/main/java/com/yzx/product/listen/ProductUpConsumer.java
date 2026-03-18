package com.yzx.product.listen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Slf4j
public class ProductUpConsumer {

    @Autowired
    private SkuInfoService skuInfoMapper;

    @Autowired
    private SkuRepository productEsRepository;

    @Autowired
    private PmsSkuSaleAttrValueService skuSaleAttrMapper;

    @Autowired
    private IMqMessageService mqMessageService;

    @Idempotent(
            key = "'PRODUCT_UP_'+#message.messageProperties.correlationId",
            message = "该SPU上架消息正在处理中，请勿重复消费",
            expireTime = 600
    )
    @RabbitListener(queues = "product.up.queue")
    public void upSpuSyncEs(Message message, Channel channel) throws Exception {
        log.info("接收到MQ消息，correlationId:{}", message.getMessageProperties().getCorrelationId());
        String msgId = message.getMessageProperties().getCorrelationId();
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        // 1. 幂等校验：已处理直接ACK
        MqMessage mqMsg = mqMessageService.lambdaQuery()
                .eq(MqMessage::getMsgId, msgId)
                .one();
        if (mqMsg != null && Objects.equals(mqMsg.getStatus(), MessageStatusEnum.SUCCESS.getCode())) {
            log.warn("【重复消费】消息已处理，直接跳过，msgId:{}", msgId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long spuId = null;
        try {
            String bodyStr = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("原始消息体：{}", bodyStr);
            // 3. 解析SPU_ID
            // 解析JSON（兼容所有格式）
            JSONObject json = JSON.parseObject(bodyStr);
            // 🔥 安全获取Long类型，永不报错
            spuId = json.getLong("spuId");
            log.info("开始处理SPU上架同步ES，spuId:{}，msgId:{}", spuId, msgId);

            // 4. 查询SKU
            List<SkuInfoEntity> skuList = skuInfoMapper.list(new LambdaQueryWrapper<SkuInfoEntity>()
                    .eq(SkuInfoEntity::getSpuId, spuId));
            if (CollectionUtils.isEmpty(skuList)) {
                log.error("spuId:{} 下无sku，直接确认消息", spuId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. 同步ES
            for (SkuInfoEntity sku : skuList) {
                buildAndSaveEsDoc(sku.getSkuId());
            }

            // 6. 成功：更新状态 + ACK
            mqMessageService.updateStatus(msgId, MessageStatusEnum.SUCCESS);
            channel.basicAck(deliveryTag, false);
            log.info("spuId:{} 同步ES成功", spuId);

        } catch (Exception e) {
            log.error("spuId:{} 同步ES失败，msgId:{}", spuId, msgId, e);
            // 异常直接ACK，避免卡死
            channel.basicAck(deliveryTag, false);
        }
    }

    // 构建ES文档
    private void buildAndSaveEsDoc(Long skuId) {
        try {
            SkuInfoEntity sku = skuInfoMapper.getById(skuId);
            if (Objects.isNull(sku)) {
                log.error("❌ skuId:{} 不存在，跳过同步ES", skuId);
                return;
            }

            List<PmsSkuSaleAttrValue> saleAttrList = skuSaleAttrMapper.list(
                    new LambdaQueryWrapper<PmsSkuSaleAttrValue>().eq(PmsSkuSaleAttrValue::getSkuId, skuId));
            log.info("🔴 skuId:{} 查到销售属性数量：{}", skuId, saleAttrList.size());
            ProductEsDoc doc = new ProductEsDoc();
            doc.setId(sku.getSkuId());
            doc.setSpuId(sku.getSpuId());
            doc.setSkuName(sku.getSkuName());
            doc.setSkuTitle(sku.getSkuTitle());
            doc.setPrice(sku.getPrice());
            doc.setCatalogId(sku.getCatalogId());
            doc.setBrandId(sku.getBrandId());
            doc.setSaleCount(sku.getSaleCount());
            doc.setPublishStatus(1);

            List<ProductEsDoc.SaleAttrEsVO> attrVoList = new ArrayList<>();
            if (!CollectionUtils.isEmpty(saleAttrList)) {
                for (PmsSkuSaleAttrValue attr : saleAttrList) {
                    ProductEsDoc.SaleAttrEsVO vo = new ProductEsDoc.SaleAttrEsVO();
                    vo.setAttrName(attr.getAttrName());
                    vo.setAttrValue(attr.getAttrValue());
                    attrVoList.add(vo);
                }
            }
            // 🔥 核心：保存ES + 打印结果
            doc.setSaleAttrs(attrVoList);
            log.info("🔴 准备保存到ES：{}", JSON.toJSONString(doc));
            productEsRepository.save(doc);
            log.info("skuId:{}同步ES成功", skuId);
        }catch(Exception e){
            log.error("skuId:{}同步ES失败", skuId, e);
        }
    }
}