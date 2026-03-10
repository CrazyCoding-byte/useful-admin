package com.yzx.product.service.serviceimpl;

import com.alibaba.fastjson.JSON;
import com.yzx.model.AjaxResult;
import com.yzx.model.Result;
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
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @className: EsSearchServiceImpl
 * @author: yzx
 * @date: 2025/9/18 13:09
 * @Version: 1.0
 * @description:
 */
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
       if(CollectionUtils.isEmpty(skuIdList)){
           return new ArrayList<>();
       }
       List<String> rediskeys=skuIdList.stream().map(item->"product:sku:"+item).collect(Collectors.toList());
       //查询redis保存的数据
        List<String> objects = redisTemplate.opsForValue().multiGet(rediskeys);
        List<SkuDetailRedisVO> productList=new ArrayList<>();
        for(int i=0;i<objects.size();i++){
            String json = objects.get(i);
            if(StringUtils.hasText(json)){
                //json反序列化为vo
                SkuDetailRedisVO skuDetailRedisVO = JSON.parseObject(json, SkuDetailRedisVO.class);
                productList.add(skuDetailRedisVO);
            }else{
                 log.warn("redis中不存在该sku的缓存数据，skuId:{}",skuIdList.get(i));
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
