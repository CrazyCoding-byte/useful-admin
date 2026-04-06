package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.product.PmsSkuSaleAttrValue;

import java.util.List;

/**
 * @className: PmsSkuSaleAttrValue
 * @author: yzx
 * @date: 2026/3/7 12:51
 * @Version: 1.0
 * @description: SKU销售属性值服务接口
 */
public interface PmsSkuSaleAttrValueService extends IService<PmsSkuSaleAttrValue> {
    
    /**
     * 根据SKU ID查询销售属性值列表
     * @param skuId SKU ID
     * @return 销售属性值列表
     */
    List<PmsSkuSaleAttrValue> listBySkuId(Long skuId);
    
    /**
     * 保存或更新SKU销售属性值
     * @param skuId SKU ID
     * @param attrValues 销售属性值列表
     * @return 是否成功
     */
    boolean saveOrUpdateBySkuId(Long skuId, List<PmsSkuSaleAttrValue> attrValues);
    
    /**
     * 删除指定SKU的所有销售属性值
     * @param skuId SKU ID
     * @return 是否成功
     */
    boolean removeBySkuId(Long skuId);
}
