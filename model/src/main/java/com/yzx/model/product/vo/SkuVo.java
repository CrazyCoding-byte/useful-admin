package com.yzx.model.product.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @className: SkuVo
 * @author: yzx
 * @date: 2026/4/6 12:45
 * @Version: 1.0
 * @description: SKU视图对象，包含SKU基本信息、图片和销售属性
 */
@Data
public class SkuVo implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== pms_sku_info 字段 ==========
    /**
     * skuId
     */
    private Long skuId;

    /**
     * spuId
     */
    private Long spuId;

    /**
     * sku名称
     */
    private String skuName;

    /**
     * sku介绍描述
     */
    private String skuDesc;

    /**
     * 所属分类id
     */
    private Long catalogId;

    /**
     * 品牌id
     */
    private Long brandId;

    /**
     * 默认图片
     */
    private String skuDefaultImg;

    /**
     * 标题
     */
    private String skuTitle;

    /**
     * 副标题
     */
    private String skuSubtitle;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 销量
     */
    private Long saleCount;

    /**
     * 仓库id
     */
    @TableField(exist = false)
    private Long wareId;
    /**
     *库存
     */
    @TableField(exist = false)
    private Integer stock;
    /**
     * 发布状态
     */
    private Integer publishStatus;

    // ========== pms_sku_images 字段（列表） ==========
    /**
     * SKU图片列表
     */
    private List<SkuImageVo> images;

    // ========== pms_sku_sale_attr_value 字段（列表） ==========
    /**
     * SKU销售属性值列表
     */
    private List<SkuSaleAttrValueVo> saleAttrValues;

    private List<PmsGroupVo> specCombination;

    /**
     * SKU图片VO
     */
    @Data
    public static class SkuImageVo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * id
         */
        private Long id;

        /**
         * sku_id
         */
        private Long skuId;

        /**
         * 图片地址
         */
        private String imgUrl;

        /**
         * 排序
         */
        private Integer imgSort;

        /**
         * 默认图[0 - 不是默认图，1 - 是默认图]
         */
        private Integer defaultImg;
    }

    /**
     * SKU销售属性值VO
     */
    @Data
    public static class SkuSaleAttrValueVo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * id
         */
        private Long id;

        /**
         * sku_id
         */
        private Long skuId;

        /**
         * attr_id
         */
        private Long attrId;

        /**
         * 销售属性名
         */
        private String attrName;

        /**
         * 销售属性值
         */
        private String attrValue;

        /**
         * 顺序
         */
        private Integer attrSort;
    }
}
