package com.yzx.product.listen;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.model.product.PmsSkuSaleAttrValue;
import com.yzx.model.product.SkuInfoEntity;
import com.yzx.product.entity.ProductEsDoc;
import com.yzx.product.mapper.PmsSkuSaleAttrValueMapper;
import com.yzx.product.repository.SkuRepository;
import com.yzx.product.service.SkuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductUpConsumer {

    @Autowired
    private SkuInfoService skuInfoMapper;

    @Autowired
    private SkuRepository productEsRepository; // Spring Data ES

    @Autowired
    private PmsSkuSaleAttrValueMapper skuSaleAttrMapper;

    /**
     * 监听上架消息 → 把该 SPU 下所有 SKU 同步到 ES
     */
    @RabbitListener(queues = "product.up.queue")
    public void upSpuSyncEs(Long spuId) {
        log.info("开始同步 ES spuId:{}", spuId);

        // 1. 查该 spu 下所有 sku
        List<Long> skuIdList = skuInfoMapper.list(new LambdaQueryWrapper<SkuInfoEntity>().eq(SkuInfoEntity::getSpuId, spuId)).stream().map(item -> item.getSpuId()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(skuIdList)) {
            log.error("spuId:{} 下无 sku", spuId);
            return;
        }
        // 2. 逐个同步到 ES
        for (Long skuId : skuIdList) {
            buildAndSaveEsDoc(skuId);
        }
    }

    // 构造 ES 文档并保存
    private void buildAndSaveEsDoc(Long skuId) {
        // 1. 查询 sku 基本信息
        SkuInfoEntity sku = skuInfoMapper.getById(skuId);

        // 2. 查询销售属性
        List<PmsSkuSaleAttrValue> saleAttrList =
                skuSaleAttrMapper.selectList(new LambdaQueryWrapper<PmsSkuSaleAttrValue>().eq(PmsSkuSaleAttrValue::getSkuId, skuId));
        if (CollectionUtils.isEmpty(saleAttrList)) {
            log.error("skuId:{} 销售属性为空", skuId);
            return;
        }
        // 3. 构造 ES 数据
        ProductEsDoc doc = new ProductEsDoc();
        doc.setId(sku.getSkuId());
        doc.setSpuId(sku.getSpuId());
        doc.setSkuName(sku.getSkuName());
        doc.setSkuTitle(sku.getSkuTitle());
        doc.setPrice(sku.getPrice());
        doc.setCatalogId(sku.getCatalogId());
        doc.setBrandId(sku.getBrandId());
        doc.setSaleCount(sku.getSaleCount());
        doc.setPublishStatus(1); // 已上架

        // 销售属性封装
        List<ProductEsDoc.SaleAttrEsVO> attrVoList = new ArrayList<>();
        for (PmsSkuSaleAttrValue attr : saleAttrList) {
            ProductEsDoc.SaleAttrEsVO vo = new ProductEsDoc.SaleAttrEsVO();
            vo.setAttrName(attr.getAttrName());
            vo.setAttrValue(attr.getAttrValue());
            attrVoList.add(vo);
        }
        doc.setSaleAttrs(attrVoList);

        // 4. 保存到 ES
        productEsRepository.save(doc);
    }
}