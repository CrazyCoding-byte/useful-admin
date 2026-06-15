import { request } from '@/utils/request';

/**
 * 租户管理API
 */
export const tenantApi = {
  /**
   * 获取租户列表
   */
  getList: () => {
    return request.get({
      url: '/system/tenant/list',
    });
  },

  /**
   * 根据ID获取租户详情
   */
  getById: (tenantId: string) => {
    return request.get({
      url: `/system/tenant/${tenantId}`,
    });
  },

  /**
   * 新增租户
   */
  add: (data: Record<string, unknown>) => {
    return request.post({
      url: '/system/tenant',
      data,
    });
  },

  /**
   * 修改租户
   */
  update: (data: Record<string, unknown>) => {
    return request.put({
      url: '/system/tenant',
      data,
    });
  },

  /**
   * 删除租户
   */
  delete: (tenantId: string) => {
    return request.delete({
      url: `/system/tenant/${tenantId}`,
    });
  },
};
