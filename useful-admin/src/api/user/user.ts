import { request } from '@/utils/request';

/**
 * 用户认证API
 */
export const userAuthApi = {
  /**
   * 用户登录
   * @param userInfo 登录信息
   * @returns 登录结果
   */
  login: (userInfo: { account: string; password: string }) => {
    return fetch('/auth/user/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        clientId: 'yaohw',
        grantType: 'password',
        username: userInfo.account,
        password: userInfo.password,
      }),
    }).then(response => response.json());
  },

  /**
   * 获取用户信息
   * @param token 认证令牌
   * @returns 用户信息
   */
  getUserInfo: (token: string) => {
    return fetch('/auth/user/getInfo', {
      method: 'GET',
      headers: {
        'Authorization': 'Bearer ' + token,
      },
    }).then(response => response.json());
  },

  /**
   * 用户登出
   * @param token 认证令牌
   * @returns 登出结果
   */
  logout: (token: string) => {
    return fetch('/auth/logout', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + token,
      },
    }).then(response => response.json());
  },
};