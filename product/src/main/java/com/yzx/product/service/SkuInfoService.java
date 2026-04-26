package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.product.SkuInfoEntity;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @className: SkuInfoService
 * @author: yzx
 * @date: 2025/9/18 15:19
 * @Version: 1.0
 * @description:
 */ 
public interface SkuInfoService extends IService<SkuInfoEntity> {
    boolean updateSkuImage(@NotNull(message = "skuId 不能为空") String skuId, List<String> images);

    boolean setSkuDefaultImg(@NotNull(message = "skuId 不能为空") String skuId, @NotNull(message = "imgId 不能为空") String imgId);
}
