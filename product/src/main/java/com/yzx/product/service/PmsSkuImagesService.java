package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.product.PmsSkuImages;

import java.util.List;

/**
 * @className: PmsSkuImagesService
 * @author: yzx
 * @date: 2026/4/6 13:00
 * @Version: 1.0
 * @description: SKU图片服务接口
 */
public interface PmsSkuImagesService extends IService<PmsSkuImages> {
    
    /**
     * 根据SKU ID查询图片列表
     * @param skuId SKU ID
     * @return 图片列表
     */
    List<PmsSkuImages> listBySkuId(Long skuId);
    
    /**
     * 保存或更新SKU图片
     * @param skuId SKU ID
     * @param images 图片列表
     * @return 是否成功
     */
    boolean saveOrUpdateBySkuId(Long skuId, List<PmsSkuImages> images);
    
    /**
     * 删除指定SKU的所有图片
     * @param skuId SKU ID
     * @return 是否成功
     */
    boolean removeBySkuId(Long skuId);
}
