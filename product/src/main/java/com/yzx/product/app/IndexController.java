package com.yzx.product.app;

import com.yzx.model.AjaxResult;
import com.yzx.product.entity.SearchParam;
import com.yzx.product.service.EsSearchService;
import com.yzx.product.service.ProductCateGoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * @className: IndexController
 * @author: yzx
 * @date: 2025/9/18 11:28
 * @Version: 1.0
 * @description:首页数据
 */
@RestController
@RequestMapping("/product")
public class IndexController {

    @Autowired
    private ProductCateGoryService productCateGoryService;

    @Autowired
    private EsSearchService esSearchService;

    //查询出分类信息
    @PostMapping("/index/category")
    public AjaxResult index(Map<String, Object> params) {
        return AjaxResult.success(productCateGoryService.getCateGory(params));
    }

    //es搜索
    @PostMapping("/search")
    public AjaxResult search(SearchParam searchParam) {
        try {
            return esSearchService.search(searchParam);
        } catch (IOException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    //根据spuId查询商品信息
//    @GetMapping("/getProductInfo/{spuId}")
//    public Result getProductInfo(@PathVariable Long spuId) {
//
//    }
    @GetMapping("getBanner")
    public AjaxResult getBanner() {
        return AjaxResult.success();
    }


    @GetMapping("getProductInfo/{spuId}")
    public AjaxResult getProductInfo(@PathVariable Long spuId) {
        return AjaxResult.success();
    }
}
