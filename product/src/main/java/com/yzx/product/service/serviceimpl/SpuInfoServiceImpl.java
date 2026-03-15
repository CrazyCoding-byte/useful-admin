package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.apiclient.api.WmsFeignService;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.impl.MqMessageServiceImpl;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.model.product.SpuInfoEntity;
import com.yzx.product.service.AttrService;
import com.yzx.product.service.ProductAttrValueService;
import com.yzx.product.mapper.SpuMapper;
import com.yzx.product.service.SkuInfoService;
import com.yzx.product.service.SpuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @className: SpuInfoServiceImpl
 * @author: yzx
 * @date: 2025/9/18 15:02
 * @Version: 1.0
 * @description:
 */
@Service
@Slf4j
public class SpuInfoServiceImpl extends ServiceImpl<SpuMapper, SpuInfoEntity> implements SpuInfoService {
    @Autowired
    private SkuInfoService skuInfoService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private MqMessageServiceImpl mqMessageService;

    @Override
    @Transactional
    public boolean upSpu(String spuId) {

        //1.查询spu信息
        SpuInfoEntity spuInfo = this.getOne(new LambdaQueryWrapper<SpuInfoEntity>().eq(SpuInfoEntity::getId, spuId));
        if (Objects.isNull(spuInfo)) {
            return false;
        }
        // 2. 校验是否已上架（避免重复操作）
        if (1 == spuInfo.getPublishStatus()) {
            log.info("SPU已上架，无需重复操作，spuId:{}", spuId);
            return true;
        }
        // 3. 核心：更新数据库上架状态（publish_status=1：上架）
        spuInfo.setPublishStatus(1);
        spuInfo.setUpdateTime(new java.util.Date());
        boolean updateCount = this.updateById(spuInfo);
        if (!updateCount) {
            log.error("SPU上架失败，数据库更新失败，spuId:{}", spuId);
            return false;
        }
        //清理缓存
        List<Long> skuIdList = skuInfoService.list(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSpuId, spuId)).stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());
        for (Long skuId : skuIdList) {
            String skuKey = "product:sku:" + skuId;
            redisTemplate.delete(skuKey);
            log.info("删除缓存：{}", skuKey);
        }
        // 4.2 删除SPU的SKU列表缓存
        String spuSkuCacheKey = "product:spu:" + spuId + ":skus";
        redisTemplate.delete(spuSkuCacheKey);
        log.info("删除SPU-SKU列表缓存成功，key:{}", spuSkuCacheKey);
        //保存本地消息(后续失败重试)
        MqMessage msg = new MqMessage();
        String msgId = UUID.randomUUID().toString().replace("-", "");
        msg.setMsgId(msgId);
        msg.setAppName("product-service");
        msg.setExchange("product.up.exchange");
        msg.setRoutingKey("product.up.key");
        msg.setContent("{\"spuId\":\"" + spuId + "\"}");
        msg.setBizType("product.up");
        msg.setStatus(MessageStatusEnum.INIT.getCode());
        msg.setRetryCount(0);
        msg.setCreateTime(LocalDateTime.now());
        mqMessageService.save(msg);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    //尝试同步发送
                    rabbitTemplate.convertAndSend(msg.getExchange(), msg.getRoutingKey(), msg.getContent(),
                            m -> {
                                m.getMessageProperties().setCorrelationId(msgId);
                                return m;
                            }
                    );
                   log.info("消息立即发送成功,msgId:{}",msgId);
                   //发送成功,更新状态为 SUCCESS
                    mqMessageService.lambdaUpdate()
                            .eq(MqMessage::getMsgId, msgId)
                            .set(MqMessage::getStatus, MessageStatusEnum.SUCCESS.getCode())
                            .update();
                }catch (Exception e) {
                    //发送消息失败,保留INIT状态,等待定时任务重试
                    log.error("消息发送失败,msgId:{}",msgId);
                }
            }
        });
        log.info("SPU上架成功，spuId:{}",spuId);
        return true;
    }
}
