package com.yzx.system.controller;

import cn.hutool.core.util.ObjectUtil;
import com.yzx.model.AjaxResult;
import com.yzx.model.annotation.Log;
import com.yzx.model.constant.SystemConstants;
import com.yzx.model.enums.BusinessType;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysDeptBo;
import com.yzx.system.domain.bo.SysPostBo;
import com.yzx.system.domain.vo.SysPostVo;
import com.yzx.system.service.ISysDeptService;
import com.yzx.system.service.ISysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @className: SysUserPostController
 * @author: yzx
 * @date: 2026/6/17 15:47
 * @Version: 1.0
 * @description:
 */
@RestController
@RequiredArgsConstructor
public class SysUserPostController {
    private final ISysPostService sysPostService;
    private final ISysDeptService sysDeptService;


    /**
     * 获取岗位列表
     * @param sysPostBo
     * @param pageQuery
     * @return
     */
    @GetMapping("/list")
    public TableDataInfo<SysPostVo> list(SysPostBo sysPostBo, PageQuery pageQuery) {
        return sysPostService.selectPagePostList(sysPostBo, pageQuery);
    }

    /**
     * 导出岗位列表
     */
    @Log(title = "岗位管理", businessType = BusinessType.EXPORT)
    @PostMapping("export")
    public void export(SysPostBo sysPostBo, HttpServletResponse response) {
        List<SysPostVo> list = sysPostService.selectPostList(sysPostBo);
        ExcelUtil.exportExcel(list, "岗位数据", SysPostVo.class, response);
    }

    /**
     *   新增岗位
     */
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysPostBo sysPostBo) {
        if (!sysPostService.checkPostNameUnique(sysPostBo)) {
            return AjaxResult.error("新增岗位" + sysPostBo.getPostName() + "失败,岗位名称已经存在");
        } else if (!sysPostService.checkPostCodeUnique(sysPostBo)) {
            return AjaxResult.error("新增岗位'" + sysPostBo.getPostName() + "'失败，岗位编码已存在");
        }
        return AjaxResult.success(sysPostService.insertPost(sysPostBo));
    }

    /**
     * 修改岗位
     */
    @PostMapping()
    public AjaxResult edit(@Validated @RequestBody SysPostBo sysPostBo) {
        if (!sysPostService.checkPostNameUnique(sysPostBo)) {
            return AjaxResult.error("修改岗位'" + sysPostBo.getPostName() + "'失败，岗位名称已存在");
        } else if (!sysPostService.checkPostCodeUnique(sysPostBo)) {
            return AjaxResult.error("修改岗位'" + sysPostBo.getPostName() + "'失败，岗位编码已存在");
        } else if (SystemConstants.DISABLE.equals(sysPostBo.getStatus())
                && sysPostService.countUserPostById(sysPostBo.getPostId()) > 0) {
            return AjaxResult.error("该岗位下存在已分配用户，不能禁用!");
        }
        return AjaxResult.success(sysPostService.updatePost(sysPostBo));
    }


    /**
     * 删除岗位
     */
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @PostMapping("{/postIds}")
    public AjaxResult remove(@PathVariable Long[] postIds) {
        return AjaxResult.success(sysPostService.deletePostByIds(postIds));
    }

    /**
     * 获取岗位选择框列表
     */
    @GetMapping("/optionSelect")
    public AjaxResult optionSelect(@RequestParam(required = false) Long[] postIds, @RequestParam(required = false) Long deptId) {
        List<SysPostVo> list = new ArrayList<>();
        if (ObjectUtil.isNotNull(deptId)) {
            SysPostBo post = new SysPostBo();
            post.setDeptId(deptId);
            list = sysPostService.selectPostList(post);
        } else if (postIds != null) {
            list = sysPostService.selectPostByIds(Arrays.asList(postIds));
        }
        return AjaxResult.success(list);
    }

    /**
     * 获取部门树列表
     */
    @GetMapping("/deptTree")
    public AjaxResult deptTree(SysDeptBo dept) {
        return AjaxResult.success(sysDeptService.selectDeptTreeList(dept));
    }
}
