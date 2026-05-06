package com.yzx.model.product;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @className: PmsAttrGroup
 * @author: yzx
 * @date: 2026/5/6 10:55
 * @Version: 1.0
 * @description:
 */
@Data
@TableName("pms_attr_group")
public class PmsAttrGroup {
    private Long attrGroupId;
    private String attrGroupName;
    private Integer sort;
    private String descript;
    private String icon;
    private Long catelogId;
}
