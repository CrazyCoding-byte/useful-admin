import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { AxiosError } from 'axios';
import { useUserStore } from '@/store/modules/user';
import type { RequestOptions, Result } from '@/types/axios';
import axios from 'axios';

// 全局无感刷新状态
let isRefreshing = false;
let requests: ((token: string) => void)[] = [];

// ====================== 类型定义【完全不动】 ======================
export interface CreateAxiosOptions extends AxiosRequestConfig {
  authenticationScheme?: string;
  transform?: AxiosTransform;
  requestOptions?: RequestOptions;
}

export abstract class AxiosTransform {
  beforeRequestHook?: (config: AxiosRequestConfig, options: RequestOptions) => AxiosRequestConfig;
  transformRequestHook?: (res: AxiosResponse<Result>, options: RequestOptions) => any;
  requestCatchHook?: (e: Error | AxiosError, options: RequestOptions) => Promise<any>;
  requestInterceptors?: (config: AxiosRequestConfig, options: CreateAxiosOptions) => AxiosRequestConfig;
  responseInterceptors?: (res: AxiosResponse) => AxiosResponse;
  requestInterceptorsCatch?: (error: AxiosError) => void;
  responseInterceptorsCatch?: (error: AxiosError) => Promise<any> | void;
}

// ====================== 依赖导入【完全不动】 ======================
import isString = require('lodash/isString');
import { joinTimestamp, formatRequestDate, setObjToUrlParams } from './utils';

// 修复：定义缺失的 Recordable 类型
type Recordable<T = any> = Record<string, T>;

// 核心转换实现
export const transform: AxiosTransform = {
  // ====================== 原有逻辑【完全不动】 ======================
  beforeRequestHook: (config, options) => {
    const { apiUrl, isJoinPrefix, urlPrefix, joinParamsToUrl, formatDate, joinTime = true } = options;

    if (isJoinPrefix && urlPrefix && isString(urlPrefix)) {
      config.url = `${urlPrefix}${config.url}`;
    }
    if (apiUrl && isString(apiUrl)) {
      config.url = `${apiUrl}${config.url}`;
    }
    const params = config.params || {};
    const data = config.data || false;

    if (formatDate && data && !isString(data)) {
      formatRequestDate(data);
    }
    if (config.method?.toUpperCase() === 'GET') {
      if (!isString(params)) {
        config.params = Object.assign(params || {}, joinTimestamp(joinTime, false));
      } else {
        config.url = `${config.url + params}${joinTimestamp(joinTime, true)}`;
        config.params = undefined;
      }
    } else if (!isString(params)) {
      if (formatDate) {
        formatRequestDate(params);
      }
      if (
        Reflect.has(config, 'data') &&
        config.data &&
        (Object.keys(config.data).length > 0 || data instanceof FormData)
      ) {
        config.data = data;
        config.params = params;
      } else {
        config.data = params;
        config.params = undefined;
      }
      if (joinParamsToUrl) {
        config.url = setObjToUrlParams(config.url as string, { ...config.params, ...config.data });
      }
    } else {
      config.url += params;
      config.params = undefined;
    }
    return config;
  },

  transformRequestHook: (res, options) => {
    const { isTransformResponse, isReturnNativeResponse } = options;
    const method = res.config.method?.toLowerCase();
    if (res.status === 204 || method === 'put' || method === 'patch') {
      return res;
    }
    if (isReturnNativeResponse) {
      return res;
    }
    if (!isTransformResponse) {
      return res.data;
    }

    const { data } = res;
    if (!data) {
      throw new Error('请求接口错误');
    }
    const { code } = data || {};
    const hasSuccess = data && (code === 0 || code === 200);
    if (hasSuccess) {
      return data.data || data;
    }
    throw new Error(`请求接口错误, 错误码: ${code || '未知'}`);
  },

  requestInterceptors: (config, options) => {
    const userStore = useUserStore();
    const token = userStore.getAccessToken;

    if (token && (config as Recordable)?.requestOptions?.withToken !== false) {
      config.headers.Authorization = options.authenticationScheme
        ? `${options.authenticationScheme} ${token}`
        : token;
    }
    return config;
  },

  responseInterceptors: (res) => {
    console.log('响应拦截器触发，响应数据:', res);
    return res;
  },

  requestInterceptorsCatch: (error) => {
    console.error('请求拦截器错误:', error);
  },

  responseInterceptorsCatch: (error: AxiosError) => {
    const { config, response } = error as AxiosError & { response?: any };
    const userStore = useUserStore();
    console.log('当前用户的信息111111', userStore);
    // 无配置/无响应，直接抛出
    if (!config || !response) {
      return Promise.reject(error);
    }

    // 为了避免 response.data 被推断为 unknown，显式断言为 any
    const respData: any = response.data;

    // 判断Token过期/无效（兼容你的后端格式）
    const isTokenInvalid = response.status === 401 || (respData && (respData.error === 'invalid_token' || respData.code === 401));
    console.log('当前响应的状态', isTokenInvalid);
    if (!isTokenInvalid) {
      return Promise.reject(error);
    }

    // 无刷新Token，直接登出
    const refreshToken = userStore.getRefreshToken;
    if (!refreshToken) {
      userStore.logout();
      return Promise.reject(error);
    }

    // 刷新逻辑
    const handleRefresh = async () => {
      try {
        isRefreshing = true;
        // 调用刷新接口
        const res = await axios.post('/api/auth/refresh', {
          clientId: userStore.getClientId,
          refreshToken,
        });
        const data = res.data;
        if (data.code !== 200) throw new Error('刷新失败');

        // 保存新Token
        const newToken = data.data;
        userStore.setToken(newToken.accessToken, newToken.refreshToken);

        // 重试所有排队请求
        requests.forEach((cb) => cb(newToken.accessToken));
        requests = [];

        // 重试当前请求
        config.headers!.Authorization = `Bearer ${newToken.accessToken}`;
        return axios(config);
      } catch (err) {
        // 刷新失败，登出
        userStore.logout();
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    };

    if (isRefreshing) {
      return new Promise((resolve) => {
        requests.push((token: string) => {
          config.headers!.Authorization = `Bearer ${token}`;
          resolve(axios(config));
        });
      });
    } else {
      return handleRefresh();
    }
  },
};

// NOTE: Request instance is created in `index.ts` to avoid duplicate instances.
// This file exports types and the transform implementation only.