package com.yzx.product.service.serviceimpl;

import com.yzx.model.AjaxResult;
import com.yzx.model.Result;
import com.yzx.model.StringUtils;
import com.yzx.model.product.vo.SkuDetailRedisVO;
import com.yzx.product.entity.SearchParam;
import com.yzx.product.service.EsSearchService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @className: EsSearchServiceImpl
 * @author: yzx
 * @date: 2025/9/18 13:09
 * @Version: 1.0
 * @description:
 */
@Service
public class EsSearchServiceImpl implements EsSearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public AjaxResult search(SearchParam searchParam) throws IOException {
        SearchRequest searchRequest = new SearchRequest("product_index");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //2.搜索框关键词:分词匹配skuName/skuTitle
        if (StringUtils.hasText(searchParam.getKeyword())) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("skuName", searchParam.getKeyword())
                    .operator(Operator.AND);
            boolQueryBuilder.must(matchQuery);
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", searchParam.getKeyword()));
        }
        //3.分类筛选,精确匹配三级categoryId
        if (searchParam.getCatalogId() != null && searchParam.getCatalogId() > 0) {
            TermsQueryBuilder catalogId = QueryBuilders.termsQuery("catalogId", searchParam.getCatalogId());
            boolQueryBuilder.filter(catalogId);
        }
        //过滤上架商品
        boolQueryBuilder.filter(QueryBuilders.termQuery("publishStatus", 1));
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.from(searchParam.getPageNum() - 1 * searchParam.getPageSize());
        searchSourceBuilder.size(searchParam.getPageSize());
        searchSourceBuilder.sort("saleCount", SortOrder.DESC);
        searchRequest.source(searchSourceBuilder);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        List<Long> skuIdList = new ArrayList<>();
        long total = response.getHits().getTotalHits().value;
        if (total < 0) {
            response.getHits().forEach(item->{
                skuIdList.add(Long.parseLong(item.getId()));
            });
        }
        List<?> productList=batchGetSkuFromRedis(skuIdList);

        return AjaxResult.success(new SearchResultVo(productList,total,searchParam.getPageNum(),searchParam.getPageSize(),(int)Math.ceil((double)total/searchParam.getPageSize())));
    }

    /**
     * 批量从Redis获取Sku详情
     */
    private List<SkuDetailRedisVO> batchGetSkuFromRedis(List<Long> skuIdList) {

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
