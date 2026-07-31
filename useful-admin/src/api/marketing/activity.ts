import { request } from '@/utils/request';

/**
 * 营销活动管理 API
 */
export const activityApi = {
  /**
   * 获取活动列表（分页）
   */
  getList: (params?: Record<string, unknown>) => {
    return request.get({
      url: '/coupon/activity/list',
      params,
    });
  },

  /**
   * 根据ID获取活动详情
   */
  getById: (id: number) => {
    return request.get({
      url: `/coupon/activity/${id}`,
    });
  },

  /**
   * 新增活动
   */
  add: (data: Record<string, unknown>) => {
    return request.post({
      url: '/coupon/activity',
      data,
    });
  },

  /**
   * 修改活动
   */
  update: (data: Record<string, unknown>) => {
    return request.put({
      url: '/coupon/activity',
      data,
    });
  },

  /**
   * 删除活动
   */
  delete: (id: number) => {
    return request.delete({
      url: `/coupon/activity/${id}`,
    });
  },
};
