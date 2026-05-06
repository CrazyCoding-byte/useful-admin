package com.yzx.model.product;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @className: PmsAttrAttrGroupRelation
 * @author: yzx
 * @date: 2026/5/6 9:24
 * @Version: 1.0
 * @description:
 */
@Data
@TableName("pms_attr_attrgroup_relation")
public class PmsAttrAttrgroupRelation {
    private Long id;
    private Long attrId;
    private Long attrGroupId;
    private Integer attrSort;
}
