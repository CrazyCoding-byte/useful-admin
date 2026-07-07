import { request } from '@/utils/request';

/**
 * 用户认证API
 */
export const userAuthApi = {
  /**
   * 获取租户列表
   * @returns 租户列表
   */
  getTenantList: () => {
    return request.get({
      url: '/auth/tenant/list',
    });
  },

  /**
   * 用户登录
   * @param userInfo 登录信息
   * @returns 登录结果
   */
  login: (userInfo: { account: string; password: string; tenantId?: string }) => {
    return request.post({
      url: '/auth/user/login',
      data: {
        clientId: 'yaohw',
        grantType: 'password',
        username: userInfo.account,
        password: userInfo.password,
        tenantId: userInfo.tenantId,
      },
    }, {
      // 登录失败不需要重试，避免不必要的延迟
      retry: { count: 0, delay: 0 },
    });
  },

  /**
   * 获取用户信息
   * @param token 认证令牌
   * @returns 用户信息
   */
  getUserInfo: (token: string) => {
    return request.get({
      url: '/auth/user/getInfo',
      headers: {
        'Authorization': 'Bearer ' + token,
      },
    });
  },

  /**
   * 用户登出
   * @param token 认证令牌
   * @returns 登出结果
   */
  logout: (token: string) => {
    return request.post({
      url: '/auth/logout',
      headers: {
        'Authorization': 'Bearer ' + token,
      },
    });
  },
};