package com.yzx.product.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * ES 商品搜索索引文档
 * 只存「检索字段 + SKU ID」，不存全量详情
 */
@Data
@Document(indexName = "product_index", shards = 3, replicas = 1) // 分片/副本数根据业务调整
public class ProductEsDoc {
    /**
     * SKU ID（ES 文档 ID 与 SKU ID 一致）
     */
    @Id
    private Long id;

    /**
     * SPU ID
     * 来源：pms_sku_info.spu_id
     */
    @Field(type = FieldType.Long)
    private Long spuId;

    /**
     * SKU 名称（分词检索）
     * 来源：pms_sku_info.sku_name
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word") // ik分词器，需提前安装
    private String skuName;

    /**
     * SKU 标题（分词检索）
     * 来源：pms_sku_info.sku_title
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String skuTitle;

    /**
     * 价格（支持范围检索）
     * 来源：pms_sku_info.price
     */
    @Field(type = FieldType.Double)
    private BigDecimal price;

    /**
     * 分类 ID（支持过滤）
     * 来源：pms_sku_info.catalog_id
     */
    @Field(type = FieldType.Long)
    private Long catalogId;

    /**
     * 分类名称（支持检索）
     * 来源：pms_category.name
     */
    @Field(type = FieldType.Keyword) // 不分词，精确匹配
    private String catalogName;

    /**
     * 品牌 ID（支持过滤）
     * 来源：pms_sku_info.brand_id
     */
    @Field(type = FieldType.Long)
    private Long brandId;

    /**
     * 品牌名称（支持检索）
     * 来源：pms_brand.name
     */
    @Field(type = FieldType.Keyword)
    private String brandName;

    /**
     * 销量（支持排序）
     * 来源：pms_sku_info.sale_count
     */
    @Field(type = FieldType.Long)
    private Long saleCount;

    /**
     * 上架状态（0-下架，1-上架）
     * 来源：pms_spu_info.publish_status
     * 仅检索上架商品
     */
    @Field(type = FieldType.Integer)
    private Integer publishStatus;

    /**
     * 销售属性值列表（支持过滤，比如：颜色=黑色）
     * 来源：pms_sku_sale_attr_value
     */
    @Field(type = FieldType.Nested) // 嵌套类型，支持复杂过滤
    private List<SaleAttrEsVO> saleAttrs;

    /**
     * 创建时间（支持时间范围检索）
     * 来源：pms_spu_info.create_time
     */
    @Field(type = FieldType.Date)
    private Date createTime;

    // 子 VO：销售属性 ES 字段
    @Data
    public static class SaleAttrEsVO {
        @Field(type = FieldType.Keyword)
        private String attrName;

        @Field(type = FieldType.Keyword)
        private String attrValue;
    }
}