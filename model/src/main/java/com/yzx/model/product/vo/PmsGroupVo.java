package com.yzx.model.product.vo;

import com.yzx.model.product.PmsAttr;
import lombok.Data;

import java.util.List;

/**
 * @className: PmsGroupVo
 * @author: yzx
 * @date: 2026/5/9 15:37
 * @Version: 1.0
 * @description:
 */
@Data
public class PmsGroupVo {
    private Long attrGroupId;
    private String attrGroupName;
    private Integer sort;
    private String descript;
    private String icon;
    private Long catelogId;
    private List<SkuVo.SkuSaleAttrValueVo> pmsAttrs;
}
