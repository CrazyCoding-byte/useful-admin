package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.SpuInfoEntity;
import com.yzx.model.product.vo.SkuVo;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * @className: SpuInfoService
 * @author: yzx
 * @date: 2025/9/18 14:56
 * @Version: 1.0
 * @description:
 */
public interface SpuInfoService extends IService<SpuInfoEntity> {

    boolean upSpu(@NotNull(message = "spuId 不能为空") String spuId);

    /**
     * 下架商品
     * @param spuId 商品 ID
     * @return 操作结果
     */
    boolean downSpu(String spuId);

    /**
     * 分页查询商品列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param params 查询参数（productName, productCode, status）
     * @return 分页结果
     */
    Page<SpuInfoEntity> queryPage(Integer pageNum, Integer pageSize, Map<String, Object> params);


    AjaxResult removeSkuIds(List<Long> skuId);

    AjaxResult saveOrUpdateSkuInfo(SkuVo skuVo);
}
