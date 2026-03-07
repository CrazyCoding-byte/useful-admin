package com.yzx.model.product.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * Redis 存储的 SKU 详情 VO
 * 字段来源对应你的数据库表，注释已标注
 */
@Data
public class SkuDetailRedisVO {
    /**
     * SKU ID
     * 来源：pms_sku_info.sku_id
     */
    private Long skuId;

    /**
     * SPU ID
     * 来源：pms_sku_info.spu_id
     */
    private Long spuId;

    /**
     * SKU 名称
     * 来源：pms_sku_info.sku_name
     */
    private String skuName;

    /**
     * SKU 标题
     * 来源：pms_sku_info.sku_title
     */
    private String skuTitle;

    /**
     * SKU 副标题
     * 来源：pms_sku_info.sku_subtitle
     */
    private String skuSubtitle;

    /**
     * 价格
     * 来源：pms_sku_info.price
     */
    private BigDecimal price;

    /**
     * 销量
     * 来源：pms_sku_info.sale_count
     */
    private Long saleCount;

    /**
     * SKU 默认图片
     * 来源：pms_sku_info.sku_default_img
     */
    private String skuDefaultImg;

    /**
     * 分类 ID
     * 来源：pms_sku_info.catalog_id
     */
    private Long catalogId;

    /**
     * 分类名称
     * 来源：pms_category.name
     */
    private String catalogName;

    /**
     * 品牌 ID
     * 来源：pms_sku_info.brand_id
     */
    private Long brandId;

    /**
     * 品牌名称
     * 来源：pms_brand.name
     */
    private String brandName;

    /**
     * SKU 图片列表
     * 来源：pms_sku_images
     */
    private List<SkuImageVO> skuImages;

    /**
     * SKU 销售属性列表
     * 来源：pms_sku_sale_attr_value
     */
    private List<SkuSaleAttrVO> saleAttrs;

    /**
     * SPU 描述
     * 来源：pms_spu_info_desc.decript
     */
    private String spuDesc;

    /**
     * 上架状态（0-下架，1-上架）
     * 来源：pms_spu_info.publish_status
     */
    private Integer publishStatus;

    // 子 VO：SKU 图片
    @Data
    public static class SkuImageVO {
        /**
         * 图片地址
         * 来源：pms_sku_images.img_url
         */
        private String imgUrl;

        /**
         * 是否默认图（0-否，1-是）
         * 来源：pms_sku_images.default_img
         */
        private Integer defaultImg;
    }

    // 子 VO：SKU 销售属性
    @Data
    public static class SkuSaleAttrVO {
        /**
         * 属性 ID
         * 来源：pms_sku_sale_attr_value.attr_id
         */
        private Long attrId;

        /**
         * 属性名称
         * 来源：pms_sku_sale_attr_value.attr_name
         */
        private String attrName;

        /**
         * 属性值
         * 来源：pms_sku_sale_attr_value.attr_value
         */
        private String attrValue;
    }
}