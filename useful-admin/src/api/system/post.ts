import { request } from '@/utils/request';

export interface SysPost {
  postId?: number;
  postCode?: string;
  postName?: string;
  postSort?: number;
  status?: string;
  remark?: string;
  createTime?: string;
}

export const postApi = {
  // 查询岗位列表
  getPostList: (params?: Partial<SysPost>) => {
    return request.get({
      url: '/post/list',
      params,
    });
  },

  // 查询岗位详情
  getPostById: (postId: number) => {
    return request.get({
      url: `/post/${postId}`,
    });
  },

  // 新增岗位
  addPost: (data: Partial<SysPost>) => {
    return request.post({
      url: '/post',
      data,
    });
  },

  // 修改岗位
  updatePost: (data: Partial<SysPost>) => {
    return request.put({
      url: '/post',
      data,
    });
  },

  // 删除岗位
  deletePost: (postIds: number[]) => {
    return request.delete({
      url: `/post/${postIds.join(',')}`,
    });
  },

  // 获取岗位选择框列表
  getPostOptions: (postIds?: number[]) => {
    return request.get({
      url: '/post/optionselect',
      params: postIds ? { postIds } : undefined,
    });
  },
};
