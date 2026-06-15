package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.StringUtils;
import com.yzx.model.annotation.Log;
import com.yzx.model.enums.BusinessType;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysDictDataBo;
import com.yzx.system.domain.vo.SysDictDataVo;
import com.yzx.system.service.ISysDictDataService;
import com.yzx.system.service.ISysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
@RequestMapping("/system/dict/data")
public class SysDictDataController {

    private final ISysDictDataService dictDataService;
    private final ISysDictTypeService dictTypeService;

    /**
     * 查询字典数据列表
     */
    @PreAuthorize("hasAuthority('system:dict:list')")
    @GetMapping("/list")
    public TableDataInfo<SysDictDataVo> list(SysDictDataBo dictData, PageQuery pageQuery) {
        return dictDataService.selectPageDictDataList(dictData, pageQuery);
    }

    /**
     * 查询字典数据详细
     *
     * @param dictCode 字典code
     */
    @PreAuthorize("hasAuthority('system:dict:query')")
    @GetMapping(value = "/{dictCode}")
    public AjaxResult getInfo(@PathVariable Long dictCode) {
        return AjaxResult.success(dictDataService.selectDictDataById(dictCode));
    }

    /**
     * 根据字典类型查询字典数据信息
     *
     * @param dictType 字典类型
     */
    @GetMapping(value = "/type/{dictType}")
    public AjaxResult dictType(@PathVariable String dictType) {
        List<SysDictDataVo> data = dictTypeService.selectDictDataByType(dictType);
        if (StringUtils.isNull(data)) {
            data = new ArrayList<>();
        }
        return AjaxResult.success(data);
    }

    /**
     * 新增字典数据
     */
    @PreAuthorize("hasAuthority('system:dict:add')")
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            return AjaxResult.error("新增字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.insertDictData(dict);
        return AjaxResult.success();
    }

    /**
     * 修改保存字典数据
     */
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            return AjaxResult.error("修改字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.updateDictData(dict);
        return AjaxResult.success();
    }

    /**
     * 删除字典数据
     *
     * @param dictCodes 字典code串
     */
    @PreAuthorize("hasAuthority('system:dict:remove')")
    @Log(title = "字典数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictCodes}")
    public AjaxResult remove(@PathVariable Long[] dictCodes) {
        dictDataService.deleteDictDataByIds(Arrays.asList(dictCodes));
        return AjaxResult.success();
    }
}
