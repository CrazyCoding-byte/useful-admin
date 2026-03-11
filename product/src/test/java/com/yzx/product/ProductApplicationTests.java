package com.yzx.product;

import com.yzx.product.entity.ProductEsDoc;
import org.elasticsearch.index.query.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

@SpringBootTest
class ProductApplicationTests {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;



    @Test
    void contextLoads() {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery("华为手机", "name")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .analyzer("ik_smart")// 分词器
                .fuzziness("AUTO")//模糊匹配
                .lenient(true)//忽略类型转换
                .operator(Operator.OR);// 表示关键词分词后必须全部匹配（比如"华为"和"手机"都要出现）
        // .operator(Operator.OR); // 表示关键词分词后任意一个匹配即可（默认值）
        NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
                .withQuery(multiMatchQueryBuilder)
                .build();
        SearchHits<ProductEsDoc> search = elasticsearchOperations.search(nativeSearchQuery, ProductEsDoc.class);
        search.forEach(hit -> {
            ProductEsDoc content = hit.getContent();
            System.out.println(hit.getContent());
            float score = hit.getScore();
            System.out.println("商品：" + content + "，匹配得分：" + score);
        });
    }

    @Test
    void testTermsQuery() {
        QueryBuilder queryBuilder = QueryBuilders.termsQuery("name", "华为手机");
        NativeSearchQuery build = new NativeSearchQueryBuilder()
                .withQuery(queryBuilder)
                .build();
        SearchHits<ProductEsDoc> search = elasticsearchOperations.search(build, ProductEsDoc.class);
        // 3. 执行+解析
        search.forEach(hit -> System.out.println("精确匹配结果：" + hit.getContent()));
    }

    @Test
    void testRangeQuery(){
        RangeQueryBuilder price = QueryBuilders.rangeQuery("price")
                .gte(1000)
                .lte(2000);

    }
}
