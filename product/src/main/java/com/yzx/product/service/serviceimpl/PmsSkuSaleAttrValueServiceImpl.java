package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.product.PmsSkuSaleAttrValue;
import com.yzx.product.mapper.PmsSkuSaleAttrValueMapper;
import com.yzx.product.service.PmsSkuSaleAttrValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * @className: PmsSkuSaleAttrValueServiceImpl
 * @author: yzx
 * @date: 2026/3/7 12:52
 * @Version: 1.0
 * @description: SKU销售属性值服务实现类
 */
@Slf4j
@Service("pmsSkuSaleAttrValueService")
public class PmsSkuSaleAttrValueServiceImpl extends ServiceImpl<PmsSkuSaleAttrValueMapper, PmsSkuSaleAttrValue> implements PmsSkuSaleAttrValueService {

    @Override
    public List<PmsSkuSaleAttrValue> listBySkuId(Long skuId) {
        log.info("查询SKU销售属性值列表，skuId={}", skuId);
        LambdaQueryWrapper<PmsSkuSaleAttrValue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsSkuSaleAttrValue::getSkuId, skuId)
                .orderByAsc(PmsSkuSaleAttrValue::getAttrSort);
        return this.list(wrapper);
    }

    public List<PmsSkuSaleAttrValue> listBySkuIds(List<Long> skuIds) {
        log.info("查询SKU销售属性值列表，skuIds={}", skuIds);
        if (!CollectionUtils.isEmpty(skuIds)) {
            LambdaQueryWrapper<PmsSkuSaleAttrValue> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(PmsSkuSaleAttrValue::getSkuId, skuIds)
                    .orderByAsc(PmsSkuSaleAttrValue::getAttrSort);
            return this.list(wrapper);
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBySkuId(Long skuId, List<PmsSkuSaleAttrValue> attrValues) {
        log.info("保存或更新SKU销售属性值，skuId={}, 数量={}", skuId, attrValues != null ? attrValues.size() : 0);

        // 先删除原有的销售属性值
        this.removeBySkuId(skuId);

        // 如果新列表为空，直接返回
        if (attrValues == null || attrValues.isEmpty()) {
            return true;
        }

        // 设置skuId并批量保存
        for (PmsSkuSaleAttrValue attrValue : attrValues) {
            attrValue.setSkuId(skuId);
        }

        return this.saveBatch(attrValues);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBySkuId(Long skuId) {
        log.info("删除SKU销售属性值，skuId={}", skuId);
        LambdaQueryWrapper<PmsSkuSaleAttrValue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsSkuSaleAttrValue::getSkuId, skuId);
        return this.remove(wrapper);
    }
}
