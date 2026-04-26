package com.yzx.product.service.serviceimpl;

import ch.qos.logback.core.util.StringCollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.StringUtils;
import com.yzx.model.product.PmsSkuImages;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.product.mapper.SkuInfoMapper;
import com.yzx.product.service.PmsSkuImagesService;
import com.yzx.product.service.SkuInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @className: SkuInfoServiceImpl
 * @author: yzx
 * @date: 2025/9/18 15:15
 * @Version: 1.0
 * @description:
 */
@Service
@RequiredArgsConstructor
public class SkuInfoServiceImpl extends ServiceImpl<SkuInfoMapper, SkuInfoEntity> implements SkuInfoService {
    private final PmsSkuImagesService pmsSkuImagesService;

    @Override
    public boolean updateSkuImage(String skuId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return false;
        }
        // 更新SKU图片信息
        SkuInfoEntity skuInfo = this.getById(skuId);
        if (Objects.isNull(skuInfo)) return true;
        pmsSkuImagesService.remove(new LambdaQueryWrapper<PmsSkuImages>().eq(PmsSkuImages::getSkuId, skuInfo.getSkuId()));
        if (!CollectionUtils.isEmpty(images)) {
            List<PmsSkuImages> pmsSkuImages = new ArrayList<>();
            for (String image : images) {
                PmsSkuImages skuImages = new PmsSkuImages();
                skuImages.setSkuId(Long.valueOf(skuId));
                skuImages.setImgUrl(image);
                skuImages.setDefaultImg(0);
                pmsSkuImages.add(skuImages);
            }
            pmsSkuImagesService.saveBatch(pmsSkuImages);
        }
        return true;
    }

    @Override
    public boolean setSkuDefaultImg(String skuId, String imgId) {
        if (StringUtils.isEmpty(skuId) || StringUtils.isEmpty(imgId)) {
            return false;
        }
        //先去查询出image
        PmsSkuImages pmsSkuImages = pmsSkuImagesService.getById(imgId);
        if (!Objects.isNull(pmsSkuImages)) {
            pmsSkuImages.setDefaultImg(1);
            pmsSkuImagesService.updateById(pmsSkuImages);
        }
        SkuInfoEntity skuInfo = this.getOne(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSkuId, skuId));
        skuInfo.setSkuDefaultImg(pmsSkuImages.getImgUrl());
        boolean b = this.updateById(skuInfo);
        return b;
    }
}
