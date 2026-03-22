import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { AxiosError } from 'axios';
import { useUserStore } from '@/store/modules/user';
import type { RequestOptions, Result } from '@/types/axios';
import axios from 'axios';

// 全局无感刷新状态（严格按你的规范，不新增类型）
let isRefreshing = false;
let requests: ((token: string) => void)[] = [];

// ====================== 你原有的类型定义【完全不动，严格保留】 ======================
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
  responseInterceptorsCatch?: (error: AxiosError) => void;
}

// ====================== 以下是严格匹配类型的实现代码 ======================
import isString from 'lodash/isString';
import merge from 'lodash/merge';
import proxy from '@/config/proxy';
import { joinTimestamp, formatRequestDate, setObjToUrlParams } from './utils';
import { TOKEN_NAME } from '@/config/global';
import { VAxios } from './Axios';

const env = import.meta.env.MODE || 'development';
const host = env === 'mock' || !proxy.isRequestProxy ? '' : proxy[env].host;

// 核心转换实现（严格继承 AxiosTransform 类型）
const transform: AxiosTransform = {
  // 请求前处理（原有逻辑，完全不变）
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

  // 响应数据处理（原有逻辑，完全不变）
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

  // ====================== 请求拦截器：从UserStore拿token（严格匹配类型） ======================
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

  // 响应拦截器（原有逻辑，完全不变）
  responseInterceptors: (res) => {
    return res;
  },

  // 请求拦截器错误（原有逻辑，完全不变）
  requestInterceptorsCatch: (error) => {
    console.error('请求拦截器错误:', error);
  },

  // ====================== 响应错误拦截器：【严格匹配你的类型定义】无感刷新核心 ======================
  responseInterceptorsCatch: (error: AxiosError) => {
    const { config, response } = error;
    const userStore = useUserStore();

    if (!config || !response) return;

    // 1. 捕获Token过期（401）
    const isTokenExpired = response.status === 401 || (response.data as Result)?.code === 401;
    if (!isTokenExpired) return;

    const refreshToken = userStore.getRefreshToken;
    if (!refreshToken) {
      userStore.logout();
      return;
    }

    // 2. 处理刷新逻辑（严格遵循 void 返回值，不破坏类型）
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

        // 3. 保存新Token到UserStore
        const newToken = data.data;
        userStore.setToken(newToken.accessToken, newToken.refreshToken);

        // 4. 重试所有缓存请求
        requests.forEach((cb) => cb(newToken.accessToken));
        requests = [];

        // 5. 重试当前请求
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

    // 6. 缓存请求，防止重复刷新
    if (isRefreshing) {
      requests.push((newToken: string) => {
        config.headers!.Authorization = `Bearer ${newToken}`;
        axios(config);
      });
    } else {
      handleRefresh();
    }
  },
};

// 创建Axios实例（原有逻辑，完全不变）
function createAxios(opt?: Partial<CreateAxiosOptions>) {
  return new VAxios(
    merge(
      <CreateAxiosOptions>{
        authenticationScheme: 'Bearer',
        timeout: 10 * 1000,
        withCredentials: true,
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        transform,
        requestOptions: {
          apiUrl: host,
          isJoinPrefix: false,
          urlPrefix: '/api',
          isReturnNativeResponse: false,
          isTransformResponse: true,
          joinParamsToUrl: false,
          formatDate: true,
          joinTime: true,
          ignoreRepeatRequest: true,
          withToken: true,
          retry: { count: 0, delay: 1000 },
        },
      },
      opt || {},
    ),
  );
}

export const request = createAxios();
