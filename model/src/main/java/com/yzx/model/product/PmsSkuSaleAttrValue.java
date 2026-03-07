package com.yzx.model.product;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

/**
 * sku销售属性&值
 *
 * @TableName pms_sku_sale_attr_value
 */
@Data
@TableName(value = "pms_sku_sale_attr_value")
public class PmsSkuSaleAttrValue implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * SKU编号
     */
    @TableField(value = "sku_id")
    private Long skuId;

    /**
     * 销售属性ID
     */
    @TableField(value = "attr_id")
    private Long attrId;

    /**
     * 销售属性名
     */
    @TableField(value = "attr_name")
    private String attrName;

    /**
     * 销售属性值
     */
    @TableField(value = "attr_value")
    private String attrValue;

    /**
     * 展示顺序（升序排列）
     */
    @TableField(value = "attr_sort")
    private Integer attrSort;

    /**
     * 序列化版本号（避免反序列化异常）
     */
    private static final long serialVersionUID = 1L;
}