package com.yzx.common.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.common.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @className: DictServiceImpl
 * @author: yzx
 * @date: 2026/6/21 14:40
 * @Version: 1.0
 * @description:
 */
@Service
public class DictServiceImpl implements DictService {
    @Autowired
    private Cache<Object, Object> ceffeine;

    @Autowired
    private SystemApi systemApi;


}
