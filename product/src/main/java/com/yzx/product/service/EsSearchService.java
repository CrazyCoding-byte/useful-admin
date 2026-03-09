package com.yzx.product.service;

import com.yzx.model.AjaxResult;
import com.yzx.model.Result;
import com.yzx.product.entity.SearchParam;

import java.io.IOException;

/**
 * @className: EsSearchService
 * @author: yzx
 * @date: 2025/9/18 13:09
 * @Version: 1.0
 * @description:
 */
public interface EsSearchService {
    AjaxResult search(SearchParam searchParam) throws IOException;
}
