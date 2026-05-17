package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import com.yzx.apiclient.api.WmsFeignService;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.impl.MqMessageServiceImpl;
import com.yzx.common.utils.PageResult;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.*;
import com.yzx.model.product.vo.PmsGroupVo;
import com.yzx.model.product.vo.SkuVo;
import com.yzx.model.wms.WareSkuEntity;
import com.yzx.product.mapper.SpuMapper;
import com.yzx.product.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final PmsAttrService pmsAttrService;
    private final PmsAttrGroupService pmsAttrGroupService;
    private final PmsAttrAttrgroupRelationService pmsAttrAttrgroupRelationService;
    private final WmsFeignService wmsFeignService;

    public static String longestCommonPrefix(String[] strs) {
        if (strs.length == 0) return "";
        //先取出第一个字符
        String s = strs[0];
        //每个进行判断
        for (String str : strs) {
            //如果不是以这个开头的
            while (!str.startsWith(s)) {
                if (s.length() == 0) return "";
                //就切割后面的 直到是相等的
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    public static void main(String[] args) {
        String[] strs = {"flow", "flower", "flight"};
        String s = longestCommonPrefix(strs);
        System.out.println(s);
    }

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
            // 3. 处理 SKU 销售属性值列表
            if (!CollectionUtils.isEmpty(skuVo.getSpecCombination())) {
                List<PmsGroupVo> specCombination = skuVo.getSpecCombination();
                List<PmsSkuSaleAttrValue> pmsSkuSaleAttrValues = specCombination.stream().map(item -> {
                    PmsSkuSaleAttrValue attrValue = new PmsSkuSaleAttrValue();
                    BeanUtils.copyProperties(item, attrValue);
                    return attrValue;
                }).collect(Collectors.toList());
                // 批量保存或更新销售属性值（先删后增）
                boolean attrSuccess = pmsSkuSaleAttrValueService.saveOrUpdateBySkuId(skuId, pmsSkuSaleAttrValues);
                if (!attrSuccess) {
                    throw new RuntimeException("保存SKU销售属性值失败");
                }
                log.info("保存SKU销售属性值成功，数量={}", pmsSkuSaleAttrValues.size());
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
    @Transactional
    public boolean upSku(String skuId) {
        //todo 上架sku
        SkuInfoEntity skuInfoEntity = skuInfoService.getOne(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSkuId, skuId));
        if (Objects.isNull(skuInfoEntity)) return false;
        //校验是否已经发布的状态
        if (Objects.equals(skuInfoEntity.getPublishStatus(), 1)) {
            log.info("skuId={} 已经发布", skuId);
            return true;
        }
        //发布
        skuInfoEntity.setPublishStatus(1);
        boolean update = skuInfoService.updateById(skuInfoEntity);
        if (!update) {
            log.error("skuId={} 发布失败", skuId);
            return false;
        }
        String skuKey = "product:sku:" + skuId;
        redisTemplate.delete(skuKey);
        log.info("SKU上架成功，删除缓存：{}", skuKey);

        log.info("SKU独立上架成功，skuId:{}", skuId);
        return true;
    }

    @Override
    public PageResult<SkuVo> getSkuInfoBySpuIdPage(Long spuId, Integer pageNum, Integer pageSize) {
        // 1. 创建分页对象
        Page<SkuInfoEntity> page = new Page<>(pageNum, pageSize);

        // 2. 查询 SKU 列表（分页）
        skuInfoService.page(page, new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSpuId, spuId));

        // 3. 如果没有数据，返回空分页结果
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return new PageResult<>(0L, (long) pageSize, (long) pageNum, Collections.emptyList());
        }

        // 4. 获取 SKU ID 列表
        List<Long> skuIds = page.getRecords().stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        // 5. 批量查询属性、图片和获取属性组定义
        List<PmsSkuSaleAttrValue> pmsSkuSaleAttrValues = pmsSkuSaleAttrValueService.listBySkuIds(skuIds);
        List<PmsSkuImages> pmsSkuImages = pmsSkuImagesService.listBySkuIds(skuIds);


        // 6. 组装数据
        List<SkuVo> skuVoList = page.getRecords().stream().map(item -> {
            SkuVo skuVo = new SkuVo();
            BeanUtils.copyProperties(item, skuVo);
            // 设置属性
            List<PmsSkuSaleAttrValue> filterSkuSaleAttrValue = pmsSkuSaleAttrValues.stream().filter(attr -> attr.getSkuId().equals(item.getSkuId())).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(filterSkuSaleAttrValue)) {
                List<SkuVo.SkuSaleAttrValueVo> skuSaleAttrValueVos = filterSkuSaleAttrValue.stream().map(attr -> {
                    SkuVo.SkuSaleAttrValueVo vo = new SkuVo.SkuSaleAttrValueVo();
                    BeanUtils.copyProperties(attr, vo);
                    return vo;
                }).collect(Collectors.toList());
                skuVo.setSaleAttrValues(skuSaleAttrValueVos);
            }

            // 设置图片
            List<PmsSkuImages> filterSkuImage = pmsSkuImages.stream().filter(img -> img.getSkuId().equals(item.getSkuId())).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(filterSkuImage)) {
                List<SkuVo.SkuImageVo> skuImageVos = filterSkuImage.stream().map(img -> {
                    SkuVo.SkuImageVo vo = new SkuVo.SkuImageVo();
                    BeanUtils.copyProperties(img, vo);
                    return vo;
                }).collect(Collectors.toList());
                skuVo.setImages(skuImageVos);
            }

            // 设置规格组合 specCombination（关联查询属性表和属性组表）
            if (!CollectionUtils.isEmpty(filterSkuSaleAttrValue)) {
                // 获取当前 SKU 的所有 attrId
                List<Long> attrIds = filterSkuSaleAttrValue.stream()
                        .map(PmsSkuSaleAttrValue::getAttrId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                // 批量查询属性定义 map(attrId,attr)
                Map<Long, PmsAttr> finalAttrMap;
                if (!CollectionUtils.isEmpty(attrIds)) {
                    List<PmsAttr> attrs = pmsAttrService.listByIds(attrIds);
                    finalAttrMap = attrs.stream().collect(Collectors.toMap(PmsAttr::getAttrId, attr -> attr));
                } else {
                    finalAttrMap = new HashMap<>();
                }

                // 获取当前 SKU 的所有 attrGroupId
                List<Long> attrGroupIds = filterSkuSaleAttrValue.stream()
                        .map(PmsSkuSaleAttrValue::getAttrGroupId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                // 批量查询属性组  map(groupId,pmsAttrGroup)
                Map<Long, PmsAttrGroup> finalAttrGroupMap;
                if (!CollectionUtils.isEmpty(attrGroupIds)) {
                    List<PmsAttrGroup> attrGroups = pmsAttrGroupService.listByIds(attrGroupIds);
                    finalAttrGroupMap = attrGroups.stream().collect(Collectors.toMap(PmsAttrGroup::getAttrGroupId, g -> g));
                } else {
                    finalAttrGroupMap = new HashMap<>();
                }

                List<PmsGroupVo> specCombination = filterSkuSaleAttrValue.stream()
                        .map(attr -> {
                            PmsGroupVo groupVo = new PmsGroupVo();
                            // 设置属性组信息（优先从查询结果获取）
                            PmsAttrGroup attrGroup = finalAttrGroupMap.get(attr.getAttrGroupId());
                            if (attrGroup != null) {
                                groupVo.setAttrGroupId(attrGroup.getAttrGroupId());
                                groupVo.setAttrGroupName(attrGroup.getAttrGroupName());
                            } else {
                                groupVo.setAttrGroupId(attr.getAttrGroupId());
                                groupVo.setAttrGroupName(attr.getAttrGroupName());
                            }
                            // 设置属性信息（优先从查询结果获取，兜底用数据库记录）
                            PmsAttr pmsAttr = finalAttrMap.get(attr.getAttrId());
                            if (pmsAttr != null) {
                                groupVo.setAttrId(pmsAttr.getAttrId());
                                groupVo.setAttrName(pmsAttr.getAttrName());
                            } else {
                                groupVo.setAttrId(attr.getAttrId());
                                groupVo.setAttrName(attr.getAttrName());
                            }
                            // 设置属性值
                            groupVo.setAttrValue(attr.getAttrValue());
                            return groupVo;
                        })
                        .collect(Collectors.toList());
                skuVo.setSpecCombination(specCombination);
            }
            //远程调用获取库存信息
            AjaxResult stockBySkuId = wmsFeignService.getStockBySkuId(skuVo.getSkuId());
            if (stockBySkuId.get("code").equals(200)) {
                String jsonString = JSON.toJSONString(stockBySkuId.get("data"));
                WareSkuEntity wareSkuEntity = JSON.parseObject(jsonString, WareSkuEntity.class);
                if (!Objects.isNull(wareSkuEntity)) {
                    skuVo.setStock(wareSkuEntity.getStock());
                    skuVo.setWareId(wareSkuEntity.getWareId());
                }
            }
            return skuVo;
        }).collect(Collectors.toList());

        // 7. 返回分页结果
        return new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), skuVoList);
    }

    @Override
    public List<PmsGroupVo> getAttrByCategoryId(Long id) {
        List<PmsAttrGroup> pmsAttrGroups = pmsAttrGroupService.list(new LambdaQueryWrapper<PmsAttrGroup>().eq(PmsAttrGroup::getCatelogId, id));
        if (!CollectionUtils.isEmpty(pmsAttrGroups)) {
            List<PmsGroupVo> pmsGroupVos = pmsAttrGroups.stream().map(item -> {
                PmsGroupVo pmsGroupVo = new PmsGroupVo();
                BeanUtils.copyProperties(item, pmsGroupVo);
                return pmsGroupVo;
            }).collect(Collectors.toList());
            List<Long> pmsAttrGroupIds = pmsGroupVos.stream().map(item -> item.getAttrGroupId()).collect(Collectors.toList());
            List<PmsAttrAttrgroupRelation> pmsAttrAttrgroupRelations = pmsAttrAttrgroupRelationService.list(new LambdaQueryWrapper<PmsAttrAttrgroupRelation>().in(PmsAttrAttrgroupRelation::getAttrGroupId, pmsAttrGroupIds));
            if (!CollectionUtils.isEmpty(pmsAttrAttrgroupRelations)) {
                // 按属性组ID分组，方便后续匹配 组->组下面的组id
                Map<Long, List<Long>> attrGroupToAttrMap = pmsAttrAttrgroupRelations.stream().collect(Collectors.groupingBy(PmsAttrAttrgroupRelation::getAttrGroupId, Collectors.mapping(PmsAttrAttrgroupRelation::getAttrId, Collectors.toList())));

                // 获取所有属性ID
                List<Long> allAttrIds = pmsAttrAttrgroupRelations.stream().map(PmsAttrAttrgroupRelation::getAttrId).distinct().collect(Collectors.toList());

                // 批量查询所有属性
                List<PmsAttr> allAttrs = pmsAttrService.listByIds(allAttrIds);

                // 将属性按ID建立映射
                Map<Long, PmsAttr> attrMap = allAttrs.stream().collect(Collectors.toMap(PmsAttr::getAttrId, attr -> attr));

                // 为每个属性组设置对应的属性列表
                pmsGroupVos.forEach(groupVo -> {
                    List<Long> attrIds = attrGroupToAttrMap.get(groupVo.getAttrGroupId());
                    if (attrIds != null && !attrIds.isEmpty()) {
                        List<PmsAttr> attrs = attrIds.stream().map(attrMap::get).filter(attr -> attr != null).collect(Collectors.toList());
                        groupVo.setPmsAttrs(attrs);
                    } else {
                        groupVo.setPmsAttrs(Collections.emptyList());
                    }
                });
            } else {
                // 如果没有关联关系，为每个组设置空列表
                pmsGroupVos.forEach(groupVo -> groupVo.setPmsAttrs(Collections.<PmsAttr>emptyList()));
            }
            return pmsGroupVos;
        }
        return Collections.emptyList();
    }

    @Transactional
    @Override
    public boolean downSku(String skuId) {
        if (StringUtils.isEmpty(skuId)) {
            log.error("skuId 不能为空");
            return false;
        }
        SkuInfoEntity skuInfoEntity = skuInfoService.getOne(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSkuId, skuId));
        if (Objects.isNull(skuInfoEntity)) {
            log.error("skuId={} 不存在", skuId);
            return false;
        }
        if (Objects.equals(skuInfoEntity.getPublishStatus(), 0)) {
            log.error("skuId={} 已经下架", skuId);
            return true;
        }
        skuInfoEntity.setPublishStatus(0);
        boolean update = skuInfoService.updateById(skuInfoEntity);
        if (!update) {
            log.error("skuId={} 下架失败", skuId);
            return false;
        }

        //删除缓存
        String skuKey = "product:sku:" + skuId;
        redisTemplate.delete(skuKey);
        log.info("SKU下架成功，删除缓存：{}", skuKey);
        return true;
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

        LambdaUpdateWrapper<SkuInfoEntity> skuInfoEntityLambdaQueryWrapper = new LambdaUpdateWrapper<>();
        skuInfoEntityLambdaQueryWrapper.eq(SkuInfoEntity::getSpuId, spuId).set(SkuInfoEntity::getPublishStatus, 1);
        skuInfoService.update(skuInfoEntityLambdaQueryWrapper);
        log.info("spu上架,同步批量上架该spu下所有sku,spuId:{}", spuId);

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

        // 6. 事务提交后发送
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend("product.up.exchange", "product.up", content, message -> {
                        message.getMessageProperties().setCorrelationId(msgId);
                        return message;
                    });
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