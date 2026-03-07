package com.yzx.product.repository;

import com.yzx.product.entity.ProductEsDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @className: SkuRepository
 * @author: yzx
 * @date: 2025/9/18 14:42
 * @Version: 1.0
 * @description:
 */
public interface SkuRepository extends ElasticsearchRepository<ProductEsDoc,Long> {
}
