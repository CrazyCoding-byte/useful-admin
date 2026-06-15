package com.yzx.system.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysPostBo;
import com.yzx.system.domain.vo.SysPostVo;
import com.yzx.system.service.ISysPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 岗位信息操作处理
 *
 * @author yzx
 */
@RestController
@RequestMapping("/post")
public class SysPostController extends BaseController {

    @Autowired
    private ISysPostService postService;

    /**
     * 获取岗位列表
     */
    @GetMapping("/list")
    public TableDataInfo<SysPostVo> list(SysPostBo post, PageQuery pageQuery) {
        return postService.selectPagePostList(post, pageQuery);
    }

    /**
     * 导出岗位列表
     */
    @PostMapping("/export")
    public AjaxResult export(SysPostBo post) {
        List<SysPostVo> list = postService.selectPostList(post);
        // TODO: 实现Excel导出功能
        return AjaxResult.success(list);
    }

    /**
     * 根据岗位编号获取详细信息
     *
     * @param postId 岗位ID
     */
    @GetMapping(value = "/{postId}")
    public AjaxResult getInfo(@PathVariable Long postId) {
        return AjaxResult.success(postService.selectPostById(postId));
    }

    /**
     * 新增岗位
     */
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return AjaxResult.error("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return AjaxResult.error("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        return toAjax(postService.insertPost(post));
    }

    /**
     * 修改岗位
     */
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return AjaxResult.error("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return AjaxResult.error("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        } else if ("1".equals(post.getStatus()) && postService.countUserPostById(post.getPostId()) > 0) {
            return AjaxResult.error("该岗位下存在已分配用户，不能禁用!");
        }
        return toAjax(postService.updatePost(post));
    }

    /**
     * 删除岗位
     *
     * @param postIds 岗位ID串
     */
    @DeleteMapping("/{postIds}")
    public AjaxResult remove(@PathVariable Long[] postIds) {
        return toAjax(postService.deletePostByIds(postIds));
    }

    /**
     * 获取岗位选择框列表
     *
     * @param postIds 岗位ID串
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect(@RequestParam(required = false) Long[] postIds) {
        List<SysPostVo> list;
        if (postIds != null && postIds.length > 0) {
            list = postService.selectPostByIds(Arrays.asList(postIds));
        } else {
            list = postService.selectPostAll();
        }
        return AjaxResult.success(list);
    }

}
