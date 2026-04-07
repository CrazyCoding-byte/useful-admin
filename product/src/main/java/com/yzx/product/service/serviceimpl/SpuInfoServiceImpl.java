package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.impl.MqMessageServiceImpl;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.PmsSkuImages;
import com.yzx.model.product.PmsSkuSaleAttrValue;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.model.product.SpuInfoEntity;
import com.yzx.model.product.vo.SkuVo;
import com.yzx.product.mapper.SpuMapper;
import com.yzx.product.service.SkuInfoService;
import com.yzx.product.service.SpuInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpuInfoServiceImpl extends ServiceImpl<SpuMapper, SpuInfoEntity> implements SpuInfoService {

    private final SkuInfoService skuInfoService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final MqMessageServiceImpl mqMessageService;
    private final PmsSkuImagesServiceImpl pmsSkuImagesService;
    private final PmsSkuSaleAttrValueServiceImpl pmsSkuSaleAttrValueService;

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
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult saveOrUpdateSkuInfo(SkuVo skuVo) {
        if (Objects.isNull(skuVo)) {
            return AjaxResult.error("参数不能为空");
        }

        boolean isUpdate = Objects.nonNull(skuVo.getSkuId());
        if (isUpdate) {
            log.info("更新SKU信息，skuId={}", skuVo.getSkuId());
        } else {
            log.info("新增SKU信息，spuId={}", skuVo.getSpuId());
        }

        try {
            // 1. 处理 SKU 基本信息
            SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
            BeanUtils.copyProperties(skuVo, skuInfoEntity);

            if (isUpdate) {
                // 更新操作
                boolean updateSuccess = skuInfoService.updateById(skuInfoEntity);
                if (!updateSuccess) {
                    return AjaxResult.error("更新SKU基本信息失败");
                }
            } else {
                // 新增操作
                boolean saveSuccess = skuInfoService.save(skuInfoEntity);
                if (!saveSuccess) {
                    return AjaxResult.error("保存SKU基本信息失败");
                }
                // 回填生成的 SKU ID
                skuVo.setSkuId(skuInfoEntity.getSkuId());
            }

            Long skuId = skuVo.getSkuId();

            // 2. 处理 SKU 图片列表
            if (!CollectionUtils.isEmpty(skuVo.getImages())) {
                List<PmsSkuImages> imagesList = skuVo.getImages().stream()
                        .map(imageVo -> {
                            PmsSkuImages image = new PmsSkuImages();
                            BeanUtils.copyProperties(imageVo, image);
                            image.setSkuId(skuId);
                            return image;
                        })
                        .collect(Collectors.toList());

                // 批量保存或更新图片（先删后增）
                boolean imagesSuccess = pmsSkuImagesService.saveOrUpdateBySkuId(skuId, imagesList);
                if (!imagesSuccess) {
                    throw new RuntimeException("保存SKU图片失败");
                }
                log.info("保存SKU图片成功，数量={}", imagesList.size());
            } else {
                // 如果没有图片，删除原有图片
                pmsSkuImagesService.removeBySkuId(skuId);
                log.info("清空SKU图片，skuId={}", skuId);
            }

            // 3. 处理 SKU 销售属性值列表
            if (!CollectionUtils.isEmpty(skuVo.getSaleAttrValues())) {
                List<PmsSkuSaleAttrValue> attrValueList = skuVo.getSaleAttrValues().stream()
                        .map(attrVo -> {
                            PmsSkuSaleAttrValue attrValue = new PmsSkuSaleAttrValue();
                            BeanUtils.copyProperties(attrVo, attrValue);
                            attrValue.setSkuId(skuId);
                            return attrValue;
                        })
                        .collect(Collectors.toList());

                // 批量保存或更新销售属性值（先删后增）
                boolean attrSuccess = pmsSkuSaleAttrValueService.saveOrUpdateBySkuId(skuId, attrValueList);
                if (!attrSuccess) {
                    throw new RuntimeException("保存SKU销售属性值失败");
                }
                log.info("保存SKU销售属性值成功，数量={}", attrValueList.size());
            } else {
                // 如果没有销售属性值，删除原有数据
                pmsSkuSaleAttrValueService.removeBySkuId(skuId);
                log.info("清空SKU销售属性值，skuId={}", skuId);
            }

            // 4. 清理缓存（如果已上架）
            if (isUpdate) {
                String key = "product:sku:" + skuId;
                redisTemplate.delete(key);
                log.info("清理SKU缓存，key={}", key);
            }

            String message = isUpdate ? "更新成功" : "新增成功";
            log.info("SKU信息{}，skuId={}", message, skuId);
            return AjaxResult.success(message);

        } catch (Exception e) {
            log.error("保存或更新SKU信息失败", e);
            throw new RuntimeException("保存或更新SKU信息失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean upSku(String skuId) {
        //todo 上架sku

        return false;
    }

    @Override
    public AjaxResult removeSkuIds(List<Long> skuId) {
        if (CollectionUtils.isEmpty(skuId)) {
            log.error("skuId 不能为空");
            return null;
        }
        boolean remove = this.removeByIds(skuId);
        //删除images
        boolean remove2 = pmsSkuImagesService.remove(new LambdaQueryWrapper<PmsSkuImages>().in(PmsSkuImages::getSkuId, skuId));
        //删除绑定attr_value
        boolean remove1 = pmsSkuSaleAttrValueService.remove(new LambdaQueryWrapper<PmsSkuSaleAttrValue>().in(PmsSkuSaleAttrValue::getSkuId, skuId));

        return remove1 && remove2 && remove ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
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


    @Override
    @Transactional
    public boolean upSpu(String spuId) {
        if (StringUtils.isEmpty(spuId)) {
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
        Map<String, Object> content = new java.util.HashMap<>();
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
                            message -> {
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