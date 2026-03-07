package com.yzx.model.product.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * Redis 存储的 SPU 下 SKU 列表 VO
 * 用于前端规格选择器
 */
@Data
public class SpuSkuListRedisVO {
    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 销售属性组合（比如：颜色-黑色，内存-256G）
     */
    private List<SkuSaleAttrSimpleVO> saleAttrs;

    // 子 VO：简化版销售属性
    @Data
    public static class SkuSaleAttrSimpleVO {
        private String attrName;
        private String attrValue;
    }
}