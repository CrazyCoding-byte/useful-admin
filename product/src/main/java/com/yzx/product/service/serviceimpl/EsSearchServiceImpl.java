package com.yzx.product.service.serviceimpl;

import com.alibaba.fastjson.JSON;
import com.yzx.model.AjaxResult;
import com.yzx.model.StringUtils;
import com.yzx.model.product.vo.SkuDetailRedisVO;
import com.yzx.product.entity.SearchParam;
import com.yzx.product.service.EsSearchService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EsSearchServiceImpl implements EsSearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public AjaxResult search(SearchParam searchParam) throws IOException {
        SearchRequest searchRequest = new SearchRequest("product_index");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 1. 关键词分词匹配
        if (StringUtils.hasText(searchParam.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuName", searchParam.getKeyword()).operator(Operator.AND));
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", searchParam.getKeyword()));
        }

        // 2. 分类筛选：单值精确匹配 → 用 termQuery（修复核心错误）
        if (searchParam.getCatalogId() != null && searchParam.getCatalogId() > 0) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId", searchParam.getCatalogId()));
        }

        // 3. 过滤上架状态
        boolQueryBuilder.filter(QueryBuilders.termQuery("publishStatus", 1));

        // 4. 分页修复：必须加括号 (pageNum-1)*pageSize
        int from = (searchParam.getPageNum() - 1) * searchParam.getPageSize();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());
        searchSourceBuilder.sort("saleCount", SortOrder.DESC);

        // 5. 执行查询
        searchRequest.source(searchSourceBuilder);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        // 6. 总条数判断修复：total > 0 才遍历数据
        List<Long> skuIdList = new ArrayList<>();
        long total = response.getHits().getTotalHits().value;
        if (total > 0) {
            response.getHits().forEach(item -> skuIdList.add(Long.parseLong(item.getId())));
        }

        // 7. 从Redis批量获取数据
        List<SkuDetailRedisVO> productList = batchGetSkuFromRedis(skuIdList);

        // 8. 封装返回结果
        int totalPages = (int) Math.ceil((double) total / searchParam.getPageSize());
        return AjaxResult.success(new SearchResultVo(productList, total, searchParam.getPageNum(), searchParam.getPageSize(), totalPages));
    }

    /**
     * 批量从Redis获取Sku详情
     */
    private List<SkuDetailRedisVO> batchGetSkuFromRedis(List<Long> skuIdList) {
        if (CollectionUtils.isEmpty(skuIdList)) {
            return new ArrayList<>();
        }
        List<String> redisKeys = skuIdList.stream().map(item -> "product:sku:" + item).collect(Collectors.toList());
        List<String> objects = redisTemplate.opsForValue().multiGet(redisKeys);
        List<SkuDetailRedisVO> productList = new ArrayList<>();

        for (int i = 0; i < objects.size(); i++) {
            String json = objects.get(i);
            if (StringUtils.hasText(json)) {
                SkuDetailRedisVO vo = JSON.parseObject(json, SkuDetailRedisVO.class);
                productList.add(vo);
            } else {
                log.warn("Redis缓存不存在，skuId:{}", skuIdList.get(i));
            }
        }
        return productList;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SearchResultVo {
        private List<?> productList;
        private Long total;
        private Integer pageNum;
        private Integer pageSize;
        private Integer totalPages;
    }
}