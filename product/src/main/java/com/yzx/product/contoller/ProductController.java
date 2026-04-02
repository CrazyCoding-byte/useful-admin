package com.yzx.product.contoller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.SpuInfoEntity;
import com.yzx.product.service.SpuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;

/**
 * @className: ProductController
 * @author: yzx
 * @date: 2025/9/18 14:49
 * @Version: 1.0
 * @description:
 */
@RequestMapping("/product")
@RestController
@Slf4j
public class ProductController {

    @Autowired
    private SpuInfoService spuInfoService;

    /**
     * 获取商品列表（分页）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param productName 商品名称（可选）
     * @param productCode 商品编码（可选）
     * @param status 商品状态（可选）
     * @return 分页数据
     */
    @PostMapping("/list/{pageNum}/{pageSize}")
    public AjaxResult list(
            @PathVariable Integer pageNum,
            @PathVariable Integer pageSize,
            @RequestBody(required = false) Map<String, Object> params) {
        log.info("查询商品列表，pageNum={}, pageSize={}, params={}", pageNum, pageSize, params);

        Page<SpuInfoEntity> page = spuInfoService.queryPage(pageNum, pageSize, params);

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());

        return AjaxResult.success(result);
    }

    /**
     * 根据 ID 获取商品信息
     * @param id 商品 ID
     * @return 商品信息
     */
    @GetMapping("/{id}")
    public AjaxResult info(@PathVariable String id) {
        log.info("查询商品信息，id={}", id);
        SpuInfoEntity spu = spuInfoService.getById(Long.valueOf(id
        ));
        if (spu == null) {
            return AjaxResult.error("商品不存在");
        }
        return AjaxResult.success(spu);
    }

    /**
     * 新增或修改商品
     * @param spuInfoEntity 商品信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult save(@Valid @RequestBody SpuInfoEntity spuInfoEntity) {
        log.info("保存商品信息，id={}, name={}",
                spuInfoEntity.getId(), spuInfoEntity.getSpuName());
        if (Objects.isNull(spuInfoEntity)) return AjaxResult.error("传的数据不能未未空");
        boolean success;
        if (spuInfoEntity.getId() == null) {
            // 新增
            spuInfoEntity.setPublishStatus(0); // 默认下架状态
            success = spuInfoService.save(spuInfoEntity);
        } else {
            // 修改
            spuInfoEntity.setUpdateTime(new java.util.Date());
            success = spuInfoService.updateById(spuInfoEntity);
        }

        if (success) {
            return AjaxResult.success("操作成功");
        } else {
            return AjaxResult.error("操作失败");
        }
    }

    /**
     * 删除商品（支持批量）
     * @param ids 商品 ID 数组，逗号分隔
     * @return 操作结果
     */
    @DeleteMapping("/{ids}")
    public AjaxResult delete(@PathVariable List<String> ids) {
        log.info("删除商品，ids={}", ids);

        try {
            for (String id : ids) {
                spuInfoService.removeById(Long.parseLong(id.trim()));
            }
            return AjaxResult.success("删除成功");
        } catch (Exception e) {
            log.error("删除失败", e);
            return AjaxResult.error("删除失败：" + e.getMessage());
        }
    }

    /**
     * 上架商品
     * @param spuId  上架/修改商品
     * @return
     */
    @PostMapping("/upPd/{spuId}")
    public AjaxResult upPd(@PathVariable("spuId") @NotNull(message = "spuId 不能为空") String spuId) {
        // 1. 基础日志（便于排查问题）
        log.info("开始执行 SPU 上架操作，spuId:{}", spuId);
        // 2. 调用服务层执行上架逻辑
        boolean success = spuInfoService.upSpu(spuId);

        // 3. 统一返回结果
        if (success) {
            return AjaxResult.success("SPU 上架成功");
        } else {
            return AjaxResult.error("SPU 上架失败，请检查 SPU 是否存在或已上架");
        }
    }

    /**
     * 下架商品
     * @param spuId 商品 ID
     * @return 操作结果
     */
    @PostMapping("/downPd/{spuId}")
    public AjaxResult downPd(@PathVariable("spuId") @NotNull(message = "spuId 不能为空") String spuId) {
        log.info("开始执行 SPU 下架操作，spuId:{}", spuId);
        boolean success = spuInfoService.downSpu(spuId);

        if (success) {
            return AjaxResult.success("SPU 下架成功");
        } else {
            return AjaxResult.error("SPU 下架失败");
        }
    }

    @GetMapping("Test")
    public AjaxResult ajaxResult() {
        return AjaxResult.success();
    }
}
