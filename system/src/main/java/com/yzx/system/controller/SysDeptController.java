package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.system.SysDept;
import com.yzx.system.service.ISysDeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理 Controller
 *
 * @author yzx
 */
@Slf4j
@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController {

    @Autowired
    private ISysDeptService deptService;

    /**
     * 查询部门列表
     */
    @GetMapping("/list")
    public AjaxResult list(SysDept dept) {
        List<SysDept> depts = deptService.selectDeptTreeList(dept);
        return AjaxResult.success(depts);
    }

    /**
     * 查询部门详情
     */
    @GetMapping(value = "/{deptId}")
    public AjaxResult getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return AjaxResult.success(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @PostMapping
    public AjaxResult add(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return AjaxResult.error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改部门
     */
    @PutMapping
    public AjaxResult edit(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return AjaxResult.error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        deptService.checkDeptDataScope(dept.getDeptId());
        return toAjax(deptService.updateDept(dept));
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{deptId}")
    public AjaxResult remove(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }

    /**
     * 获取部门选择框列表
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        List<SysDept> depts = deptService.selectDeptTreeList(new SysDept());
        return AjaxResult.success(depts);
    }

    /**
     * 查询部门列表（排除节点）
     */
    @GetMapping("/list/exclude/{deptId}")
    public AjaxResult excludeChild(@PathVariable(value = "deptId", required = false) Long deptId) {
        List<SysDept> depts = deptService.selectDeptTreeList(new SysDept());
        depts.removeIf(d -> d.getDeptId().equals(deptId) || d.getAncestors().contains(String.valueOf(deptId)));
        return AjaxResult.success(depts);
    }
}
