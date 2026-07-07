package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.annotation.Log;
import com.yzx.model.enums.BusinessType;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysDictTypeBo;
import com.yzx.system.domain.vo.SysDictDataVo;
import com.yzx.system.domain.vo.SysDictTypeVo;
import com.yzx.system.service.ISysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 数据字典信息
 *
 * @author yzx
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/dict/type")
public class SysDictTypeController {

    private final ISysDictTypeService dictTypeService;

    /**
     * 查询字典类型列表
     */
    @PreAuthorize("hasAuthority('system:dict:list')")
    @GetMapping("/list")
    public TableDataInfo<SysDictTypeVo> list(SysDictTypeBo dictType, PageQuery pageQuery) {
        return dictTypeService.selectPageDictTypeList(dictType, pageQuery);
    }

    /**
     * 查询字典类型详细
     *
     * @param dictId 字典ID
     */
    @PreAuthorize("hasAuthority('system:dict:query')")
    @GetMapping(value = "/{dictId}")
    public AjaxResult getInfo(@PathVariable Long dictId) {
        return AjaxResult.success(dictTypeService.selectDictTypeById(dictId));
    }

    /**
     * 新增字典类型
     */
    @PreAuthorize("hasAuthority('system:dict:add')")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            return AjaxResult.error("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.insertDictType(dict);
        return AjaxResult.success();
    }

    /**
     * 修改字典类型
     */
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            return AjaxResult.error("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.updateDictType(dict);
        return AjaxResult.success();
    }

    /**
     * 删除字典类型
     *
     * @param dictIds 字典ID串
     */
    @PreAuthorize("hasAuthority('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictIds}")
    public AjaxResult remove(@PathVariable Long[] dictIds) {
        dictTypeService.deleteDictTypeByIds(Arrays.asList(dictIds));
        return AjaxResult.success();
    }

    /**
     * 刷新字典缓存
     */
    @PreAuthorize("hasAuthority('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache() {
        dictTypeService.resetDictCache();
        return AjaxResult.success();
    }

    /**
     * 获取字典选择框列表
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        List<SysDictTypeVo> dictTypes = dictTypeService.selectDictTypeAll();
        return AjaxResult.success(dictTypes);
    }

    /**
     * 根据字典类型查询信息
     * @param dictType
     * @return
     */
    @GetMapping("/getDictTypeByType/{dictType}")
    public AjaxResult selectDictTypeByType(@PathVariable String dictType) {
        SysDictTypeVo sysDictTypeVo = dictTypeService.selectDictTypeByType(dictType);
        return AjaxResult.success(sysDictTypeVo);
    }

    @GetMapping("/getDictDataByType/{dictType}")
    public AjaxResult getDictDataByType(@PathVariable String dictType) {
        List<SysDictDataVo> sysDictDataVos = dictTypeService.selectDictDataByType(dictType);
        return AjaxResult.success(sysDictDataVos);
    }
}
