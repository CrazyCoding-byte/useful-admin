package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.product.PmsSkuImages;
import com.yzx.product.mapper.PmsSkuImagesMapper;
import com.yzx.product.service.PmsSkuImagesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @className: PmsSkuImagesServiceImpl
 * @author: yzx
 * @date: 2026/4/6 13:00
 * @Version: 1.0
 * @description: SKU图片服务实现类
 */
@Slf4j
@Service("pmsSkuImagesService")
public class PmsSkuImagesServiceImpl extends ServiceImpl<PmsSkuImagesMapper, PmsSkuImages> implements PmsSkuImagesService {

    @Override
    public List<PmsSkuImages> listBySkuId(Long skuId) {
        log.info("查询SKU图片列表，skuId={}", skuId);
        LambdaQueryWrapper<PmsSkuImages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsSkuImages::getSkuId, skuId)
                .orderByAsc(PmsSkuImages::getImgSort);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBySkuId(Long skuId, List<PmsSkuImages> images) {
        log.info("保存或更新SKU图片，skuId={}, 图片数量={}", skuId, images != null ? images.size() : 0);
        
        // 先删除原有的图片
        this.removeBySkuId(skuId);
        
        // 如果新图片列表为空，直接返回
        if (images == null || images.isEmpty()) {
            return true;
        }
        
        // 设置skuId并批量保存
        for (PmsSkuImages image : images) {
            image.setSkuId(skuId);
        }
        
        return this.saveBatch(images);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBySkuId(Long skuId) {
        log.info("删除SKU图片，skuId={}", skuId);
        LambdaQueryWrapper<PmsSkuImages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsSkuImages::getSkuId, skuId);
        return this.remove(wrapper);
    }
}
