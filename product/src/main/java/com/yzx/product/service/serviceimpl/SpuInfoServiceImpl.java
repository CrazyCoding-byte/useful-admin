package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.impl.MqMessageServiceImpl;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.model.product.SpuInfoEntity;
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
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpuInfoServiceImpl extends ServiceImpl<SpuMapper, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SkuInfoService skuInfoService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqMessageServiceImpl mqMessageService;

    @Override
    public Page<SpuInfoEntity> queryPage(Integer pageNum, Integer pageSize, Map<String, Object> params) {
        // 1. 构造分页对象
        Page<SpuInfoEntity> page = new Page<>(pageNum, pageSize);
        
        // 2. 构造查询条件
        LambdaQueryWrapper<SpuInfoEntity> wrapper = new LambdaQueryWrapper<>();
        
        // 3. 动态添加查询条件
        if (params != null && !params.isEmpty()) {
            String spuName = (String) params.get("spuName");
            Integer publishStatus = (Integer) params.get("publishStatus");
            
            if (StringUtils.hasText(spuName)) {
                wrapper.like(SpuInfoEntity::getSpuName, spuName);
            }
            if (publishStatus != null) {
                wrapper.eq(SpuInfoEntity::getPublishStatus, publishStatus);
            }
        }
        
        // 4. 执行分页查询
        return this.page(page, wrapper);
    }

    @Override
    public boolean downSpu(String spuId) {
        if (StringUtils.isEmpty(spuId)) {
            log.error("spuId 不能为空");
            return false;
        }
        
        // 1. 查询 SPU
        SpuInfoEntity spuInfo = this.getOne(new LambdaQueryWrapper<SpuInfoEntity>().eq(SpuInfoEntity::getId, spuId));
        if (Objects.isNull(spuInfo)) {
            log.error("SPU 不存在：{}", spuId);
            return false;
        }
        
        // 2. 校验状态
        if (Objects.equals(spuInfo.getPublishStatus(), 0)) {
            log.info("SPU 已下架：{}", spuId);
            return true;
        }
        
        // 3. 更新下架状态
        spuInfo.setPublishStatus(0);
        spuInfo.setUpdateTime(new Date());
        boolean updateSuccess = this.updateById(spuInfo);
        
        if (!updateSuccess) {
            log.error("SPU 下架失败：{}", spuId);
            return false;
        }
        
        // 4. 清理缓存（可选）
        List<SkuInfoEntity> skuList = skuInfoService.list(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSpuId, spuId));
        for (SkuInfoEntity sku : skuList) {
            String key = "product:sku:" + sku.getSkuId();
            redisTemplate.delete(key);
            log.info("删除缓存：{}", key);
        }
        
        log.info("SPU 下架成功，spuId:{}", spuId);
        return true;
    }

    public static void main(String[] args) {
        String jsonstr="{\"spuId\":\"11\"}";
        Map<String,Object> parse = JSON.parseObject(jsonstr,Map.class);

        System.out.println(parse);
    }
    @Override
    @Transactional
    public boolean upSpu(String spuId) {
        if(StringUtils.isEmpty(spuId)){
            log.error("spuId不能为空");
            return false;
        }
        // 1. 查询SPU
        SpuInfoEntity spuInfo = this.getOne(new LambdaQueryWrapper<SpuInfoEntity>().eq(SpuInfoEntity::getId, spuId));
        if (Objects.isNull(spuInfo)) {
            log.error("SPU不存在：{}", spuId);
            return false;
        }

        // 2. 校验状态
        if (Objects.equals(spuInfo.getPublishStatus(), 1)) {
            log.info("SPU已上架：{}", spuId);
            return true;
        }

        // 3. 更新上架状态
        spuInfo.setPublishStatus(1);
        spuInfo.setUpdateTime(new java.util.Date());
        boolean updateSuccess = this.updateById(spuInfo);
        if (!updateSuccess) {
            log.error("SPU更新失败：{}", spuId);
            return false;
        }

        // 4. 清理缓存
        List<SkuInfoEntity> skuList = skuInfoService.list(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSpuId, spuId));
        for (SkuInfoEntity sku : skuList) {
            String key = "product:sku:" + sku.getSkuId();
            redisTemplate.delete(key);
            log.info("删除缓存：{}", key);
        }
        String spuSkuKey = "product:spu:" + spuId + ":skus";
        redisTemplate.delete(spuSkuKey);
        log.info("删除SPU-SKU列表缓存成功，key:{}", spuSkuKey);

        // 5. 构造消息
        MqMessage msg = new MqMessage();
        String msgId = UUID.randomUUID().toString().replace("-", "");
        msg.setMsgId(msgId);
        msg.setAppName("product-service");
        msg.setExchange("product.up.exchange");
        msg.setRoutingKey("product.up");
        Map<String,Object> content = new java.util.HashMap<>();
        content.put("spuId", Long.parseLong(spuId));
        String jsonString = JSON.toJSONString(content);
        msg.setContent(jsonString);
        msg.setBizType("product.up");
        msg.setStatus(MessageStatusEnum.INIT.getCode());
        msg.setRetryCount(0);
        msg.setCreateTime(LocalDateTime.now());
        mqMessageService.save(msg);

        // 6. 事务提交后发送【纯JSON字符串】，绝对不编Base64
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    // 🔥 核心修复：直接发送JSON字符串，不经过任何序列化
                    rabbitTemplate.convertAndSend(
                            "product.up.exchange",
                            "product.up",
                            content,
                            message->{
                                message.getMessageProperties().setCorrelationId(msgId);
                                return message;
                            }
                    );
                    log.info("消息发送成功，msgId：{}", msgId);
                } catch (Exception e) {
                    log.error("消息发送失败", e);
                }
            }
        });

        log.info("SPU上架成功，spuId:{}", spuId);
        return true;
    }
}