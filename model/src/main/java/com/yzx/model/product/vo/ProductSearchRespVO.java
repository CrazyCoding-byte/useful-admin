package com.yzx.model.product.vo;

import lombok.Data;
import java.util.List;

/**
 * 商品搜索响应VO
 */
@Data
public class ProductSearchRespVO {
    /**
     * 商品列表（Redis缓存的详情）
     */
    private List<SkuDetailRedisVO> productList;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 总页数
     */
    private Integer totalPages;
}