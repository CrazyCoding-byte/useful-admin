package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.apiclient.api.WmsFeignService;
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

import java.util.List;
import java.util.Objects;
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
    private ProductAttrValueService productAttrValueService;
    @Autowired
    private AttrService attrService;
    @Autowired
    private WmsFeignService wmsFeignService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
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

        // 5. 步骤2：发送MQ消息，异步更新ES索引（核心：不阻塞接口）
        try {
            rabbitTemplate.convertAndSend("product.up.exchange", "product.up.key", spuId);
            log.info("发送SPU上架MQ消息成功，spuId:{}", spuId);
        } catch (Exception e) {
            log.error("发送MQ消息失败，spuId:{}", spuId, e);
            // 注意：MQ发送失败不影响上架核心逻辑，后续靠定时任务兜底
        }

        log.info("SPU上架成功，spuId:{}", spuId);
        return true;
    }
}
