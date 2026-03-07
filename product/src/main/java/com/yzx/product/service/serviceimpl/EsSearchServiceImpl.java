package com.yzx.product.service.serviceimpl;

import com.yzx.model.Result;
import com.yzx.model.product.vo.SkuDetailRedisVO;
import com.yzx.product.entity.SearchParam;
import com.yzx.product.service.EsSearchService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @className: EsSearchServiceImpl
 * @author: yzx
 * @date: 2025/9/18 13:09
 * @Version: 1.0
 * @description:
 */
@Service
public class EsSearchServiceImpl  implements EsSearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Override
    public Result search(SearchParam searchParam) {

        return null;
    }

    /**
     * 批量从Redis获取Sku详情
     */
    private List<SkuDetailRedisVO> batchGetSkuFromRedis(List<Long> skuIdList){

    }
}
