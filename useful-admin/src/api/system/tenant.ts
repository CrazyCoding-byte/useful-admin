import { request } from '@/utils/request';

/**
 * 租户管理API
 */
export const tenantApi = {
  /**
   * 获取租户列表（分页）
   */
  getList: (params?: Record<string, unknown>) => {
    return request.get({
      url: '/system/tenant/list',
      params,
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

/**
 * 租户套餐管理API
 */
export const tenantPackageApi = {
  /**
   * 获取套餐列表（分页）
   */
  getList: (params?: Record<string, unknown>) => {
    return request.get({
      url: '/system/tenantPackage/list',
      params,
    });
  },

  /**
   * 获取可用套餐（下拉选择用）
   */
  getOptions: () => {
    return request.get({
      url: '/system/tenantPackage/options',
    });
  },

  /**
   * 根据ID获取套餐详情
   */
  getById: (packageId: number) => {
    return request.get({
      url: `/system/tenantPackage/${packageId}`,
    });
  },

  /**
   * 新增套餐
   */
  add: (data: Record<string, unknown>) => {
    return request.post({
      url: '/system/tenantPackage',
      data,
    });
  },

  /**
   * 修改套餐
   */
  update: (data: Record<string, unknown>) => {
    return request.put({
      url: '/system/tenantPackage',
      data,
    });
  },

  /**
   * 删除套餐
   */
  delete: (packageId: number) => {
    return request.delete({
      url: `/system/tenantPackage/${packageId}`,
    });
  },
};
