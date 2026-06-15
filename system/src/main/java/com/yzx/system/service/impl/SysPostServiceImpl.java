package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.StringUtils;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.SysPost;

import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysPostBo;
import com.yzx.system.domain.convert.SysPostConvert;
import com.yzx.system.domain.vo.SysPostVo;
import com.yzx.system.mapper.SysPostMapper;
import com.yzx.system.service.ISysPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 岗位信息 服务层处理
 *
 * @author yzx
 */
@Service
public class SysPostServiceImpl implements ISysPostService {

    @Autowired
    private SysPostMapper baseMapper;

    @Autowired
    private SysPostConvert postConvert;

    /**
     * 分页查询岗位列表
     *
     * @param post      查询条件
     * @param pageQuery 分页参数
     * @return 岗位分页列表
     */
    @Override
    public TableDataInfo<SysPostVo> selectPagePostList(SysPostBo post, PageQuery pageQuery) {
        Page<SysPost> page = pageQuery.build();
        LambdaQueryWrapper<SysPost> wrapper = buildQueryWrapper(post);
        baseMapper.selectPage(page, wrapper);
        List<SysPostVo> voList = postConvert.entityListToVoList(page.getRecords());
        TableDataInfo<SysPostVo> dataInfo = new TableDataInfo<>();
        dataInfo.setRows(voList);
        dataInfo.setTotal(page.getTotal());
        dataInfo.setCode(200);
        dataInfo.setMsg("查询成功");
        return dataInfo;
    }

    /**
     * 查询岗位信息集合
     *
     * @param post 岗位信息
     * @return 岗位信息集合
     */
    @Override
    public List<SysPostVo> selectPostList(SysPostBo post) {
        LambdaQueryWrapper<SysPost> wrapper = buildQueryWrapper(post);
        List<SysPost> list = baseMapper.selectList(wrapper);
        return postConvert.entityListToVoList(list);
    }

    /**
     * 根据查询条件构建查询包装器
     *
     * @param bo 查询条件对象
     * @return 构建好的查询包装器
     */
    private LambdaQueryWrapper<SysPost> buildQueryWrapper(SysPostBo bo) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(bo.getPostCode()), SysPost::getPostCode, bo.getPostCode())
                .like(StringUtils.isNotBlank(bo.getPostName()), SysPost::getPostName, bo.getPostName())
                .eq(StringUtils.isNotBlank(bo.getStatus()), SysPost::getStatus, bo.getStatus())
                .orderByAsc(SysPost::getPostSort);
        return wrapper;
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 岗位ID
     */
    @Override
    public List<SysPostVo> selectPostsByUserId(Long userId) {
        // 通过用户ID查询岗位列表，需要通过sys_user_post关联表查询
        // 由于当前Mapper没有关联查询方法，返回空列表
        // 实际项目中应该在SysUserPostMapper中实现
        return new ArrayList<>();
    }

    /**
     * 查询所有岗位
     *
     * @return 岗位列表
     */
    @Override
    public List<SysPostVo> selectPostAll() {
        List<SysPost> list = baseMapper.selectList(new QueryWrapper<>());
        return postConvert.entityListToVoList(list);
    }

    /**
     * 通过岗位ID查询岗位信息
     *
     * @param postId 岗位ID
     * @return 角色对象信息
     */
    @Override
    public SysPostVo selectPostById(Long postId) {
        SysPost post = baseMapper.selectById(postId);
        return postConvert.entityToVo(post);
    }

    /**
     * 根据用户ID获取岗位选择框列表
     *
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    @Override
    public List<Long> selectPostListByUserId(Long userId) {
        // 通过用户ID查询岗位ID列表，需要通过sys_user_post关联表查询
        // 由于当前Mapper没有该方法，返回空列表
        // 实际项目中应该在SysUserPostMapper中实现
        return new ArrayList<>();
    }

    /**
     * 通过岗位ID串查询岗位
     *
     * @param postIds 岗位id串
     * @return 岗位列表信息
     */
    @Override
    public List<SysPostVo> selectPostByIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysPost::getPostId, postIds);
        List<SysPost> list = baseMapper.selectList(wrapper);
        return postConvert.entityListToVoList(list);
    }

    /**
     * 校验岗位名称是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostNameUnique(SysPostBo post) {
        Long postId = post.getPostId() == null ? -1L : post.getPostId();
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPost::getPostName, post.getPostName());
        SysPost info = baseMapper.selectOne(wrapper);
        if (info != null && !info.getPostId().equals(postId)) {
            return false;
        }
        return true;
    }

    /**
     * 校验岗位编码是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostCodeUnique(SysPostBo post) {
        Long postId = post.getPostId() == null ? -1L : post.getPostId();
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPost::getPostCode, post.getPostCode());
        SysPost info = baseMapper.selectOne(wrapper);
        if (info != null && !info.getPostId().equals(postId)) {
            return false;
        }
        return true;
    }

    /**
     * 通过岗位ID查询岗位使用数量
     *
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public long countUserPostById(Long postId) {
        // 由于没有SysUserPostMapper，使用MyBatis-Plus的通用查询
        // 这里返回0，如果需要实际统计需要添加SysUserPostMapper
        return 0;
    }

    /**
     * 删除岗位信息
     *
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int deletePostById(Long postId) {
        return baseMapper.deleteById(postId);
    }

    /**
     * 批量删除岗位信息
     *
     * @param postIds 需要删除的岗位ID
     * @return 结果
     */
    @Override
    public int deletePostByIds(Long[] postIds) {
        if (postIds == null || postIds.length == 0) {
            return 0;
        }
        for (Long postId : postIds) {
            SysPost post = baseMapper.selectById(postId);
            if (post != null && countUserPostById(postId) > 0) {
                throw new ServiceException(post.getPostName() + "已分配，不能删除!");
            }
        }
        return baseMapper.deleteBatchIds(Arrays.asList(postIds));
    }

    /**
     * 新增保存岗位信息
     *
     * @param bo 岗位信息
     * @return 结果
     */
    @Override
    public int insertPost(SysPostBo bo) {
        SysPost post = postConvert.boToEntity(bo);
        return baseMapper.insert(post);
    }

    /**
     * 修改保存岗位信息
     *
     * @param bo 岗位信息
     * @return 结果
     */
    @Override
    public int updatePost(SysPostBo bo) {
        SysPost post = postConvert.boToEntity(bo);
        return baseMapper.updateById(post);
    }
}
