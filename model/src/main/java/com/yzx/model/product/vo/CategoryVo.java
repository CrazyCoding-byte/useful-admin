package com.yzx.model.product.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @className: CategoryVo
 * @author: yzx
 * @date: 2025/9/18 11:52
 * @Version: 1.0
 * @description:
 */
@Data
public class CategoryVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long catId;
    private String name;
    private Integer parentCid;
    private Integer catLevel;
    private Integer showStatus;
    private String icon;
    private String productUnit;
    private List<CategoryVo> children;
}
