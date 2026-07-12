// axios配置  可自行根据项目进行更改，只需更改该文件即可，其他文件可以不动
import type { AxiosInstance } from 'axios';
import isString from 'lodash/isString';
import merge from 'lodash/merge';

import axios from 'axios';
import { ContentTypeEnum } from '@/constants';
import { getUserStore } from '@/store/modules/user';
import { getPermissionStore } from '@/store/modules/permission';
import router from '@/router';

import { VAxios } from './Axios';
import type { AxiosTransform, CreateAxiosOptions } from './AxiosTransform';
import { formatRequestDate, joinTimestamp, setObjToUrlParams } from './utils';

const env = import.meta.env.MODE || 'development';

function createAuthError(res: any, message: string) {
  const err: any = new Error(message);
  err.config = res.config;
  err.response = { status: res.data?.code || 401, data: res.data };
  return err;
}

// 如果是mock模式 或 没启用直连代理 就不配置host 会走本地Mock拦截 或 Vite 代理
const host = env === 'mock' || import.meta.env.VITE_IS_REQUEST_PROXY !== 'true' ? '' : import.meta.env.VITE_API_URL;

// 数据处理，方便区分多种处理方式
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

function onRrefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

/**
 * 从后端错误响应中提取错误信息，构造一个带有 code/data/message 的 Error 对象
 */
function extractBackendError(error: any): any {
  const { response } = error;
  if (!response?.data) return error;

  // 后端错误信息可能在 response.data.msg / message / error / error_description 中
  const errMsg =
    response.data.msg ||
    response.data.message ||
    response.data.error_description ||
    response.data.error ||
    (typeof response.data === 'string' ? response.data : '服务器异常');

  const bizError = new Error(errMsg) as any;
  bizError.code = response.status;           // HTTP 状态码，比如 500
  bizError.response = response;              // 保留原始响应对象
  bizError.data = response.data;             // 后端返回的完整 data
  return bizError;
}

const transform: AxiosTransform = {
  // 处理请求数据。如果数据不是预期格式，可直接抛出错误
  transformRequestHook: (res, options) => {
    const { isTransformResponse, isReturnNativeResponse } = options;

    // 如果204无内容直接返回
    const method = res.config.method?.toLowerCase();
    if (res.status === 204 && ['put', 'patch', 'delete'].includes(method)) {
      return res;
    }

    // 是否返回原生响应头 比如：需要获取响应头时使用该属性
    if (isReturnNativeResponse) {
      return res;
    }
    // 不进行任何处理，直接返回
    // 用于页面代码可能需要直接获取code，data，message这些信息时开启
    if (!isTransformResponse) {
      return res.data;
    }

    // 错误的时候返回
    const { data } = res;
    if (!data) {
      throw new Error('请求接口错误');
    }

    //  这里 code为 后台统一的字段，需要在 types.ts内修改为项目自己的接口返回格式
    const { code } = data;

    // 这里逻辑可以根据项目进行修改
    // 支持后端使用 0 或 200 表示成功
    const hasSuccess = data && (code === 0 || code === 200);
    if (hasSuccess) {
      // 如果后端返回 { code, data: {...} } 则优先返回 data
      // 如果后端直接返回 { code, token, ... }（没有 data 字段），则返回整个 data 对象以兼容旧逻辑
      return data.data !== undefined ? data.data : data;
    }

    // 业务错误：优先用后端返回的 msg，其次用 message/error_description
    const errMsg = data.msg || data.message || data.error_description || `请求接口错误, 错误码: ${code}`;
    throw createAuthError(res, errMsg);
  },

  // 请求前处理配置
  beforeRequestHook: (config, options) => {
    const { apiUrl, isJoinPrefix, urlPrefix, joinParamsToUrl, formatDate, joinTime = true } = options;

    console.log('[Request] 请求配置:', {
      url: config.url,
      method: config.method,
      data: config.data,
      contentType: config.headers?.['Content-Type'],
    });

    // 添加接口前缀
    if (isJoinPrefix && urlPrefix && isString(urlPrefix)) {
      config.url = `${urlPrefix}${config.url}`;
    }

    // 将baseUrl拼接
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
        // 给 get 请求加上时间戳参数，避免从缓存中拿数据。
        config.params = Object.assign(params || {}, joinTimestamp(joinTime, false));
      } else {
        // 兼容restful风格
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
        // 非GET请求如果没有提供data，则将params视为data
        config.data = params;
        config.params = undefined;
      }
      if (joinParamsToUrl) {
        config.url = setObjToUrlParams(config.url as string, { ...config.params, ...config.data });
      }
    } else {
      // 兼容restful风格
      config.url += params;
      config.params = undefined;
    }
    return config;
  },

  // 请求拦截器处理
  requestInterceptors: (config, options) => {
    // 请求之前处理config
    const userStore = getUserStore();
    const { token } = userStore;
    if (token && (config as Recordable)?.requestOptions?.withToken !== false) {
      // 如果调用时显式传了 Authorization，则不要覆盖（例如某些接口手动设置了 Bearer）
      const headers = (config as Recordable).headers || {};
      if (!headers.Authorization) {
        const scheme = options.authenticationScheme || 'Bearer';
        (config as Recordable).headers.Authorization = `${scheme} ${token}`;
      }
    }
    return config;
  },

  // 响应拦截器处理
  responseInterceptors: (res) => {
    return res;
  },

  // 响应错误处理
  responseInterceptorsCatch: (error: any, instance: AxiosInstance) => {
    const { config, response } = error;

    // If response indicates unauthorized, try refresh token flow
    const status = response?.status || response?.data?.code;
    console.log('[Request] 响应错误:', { url: config?.url, status, errorData: response?.data, message: error?.message });

    if (status === 401) {
      const userStore = getUserStore();
      const refreshToken = userStore.getRefreshToken;
      console.log('[Request] 401错误, refreshToken存在:', !!refreshToken);

      if (!refreshToken) {
        // 没有 refreshToken（比如登录场景），不做刷新，直接返回后端错误消息
        console.log('[Request] 没有refreshToken, 返回后端错误消息');
        return Promise.reject(extractBackendError(error));
      }

      if (!config) return Promise.reject(extractBackendError(error));

      // 避免对 /auth/refresh 接口进行重试，防止无限循环
      if (config.url?.includes('/auth/refresh')) {
        console.log('[Request] refresh接口401, 跳转登录页');
        userStore.removeToken();
        router.replace({ path: '/login' });
        return Promise.reject(extractBackendError(error));
      }

      if (isRefreshing) {
        console.log('[Request] 正在刷新token, 请求进入队列:', config.url);
        // push request to queue
        return new Promise((resolve) => {
          subscribeTokenRefresh((token: string) => {
            console.log('[Request] 队列请求使用新token重试:', config.url);
            // set header and retry
            config.headers = config.headers || {};
            // ensure Authorization has scheme (Bearer)
            config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
            resolve(instance.request(config));
          });
        });
      }

      console.log('[Request] 开始刷新token...');
      isRefreshing = true;

      // perform refresh with raw axios to avoid interceptors
      const params = new URLSearchParams();
      const clientId = userStore.getClientId || '';
      params.append('client_id', clientId);
      // 后端期望的参数名为 refresh_token，并且需要 grant_type=refresh_token
      params.append('refresh_token', refreshToken);
      params.append('grant_type', 'refresh_token');

      // 使用 axios 直接请求，避免拦截器
      return axios
        .post(`${host}/auth/refresh`, params.toString(), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          // 设置超时时间为5秒
          timeout: 5000,
        })
        .then(async (res) => {
          const data = res.data;
          console.log('[Request] 刷新token响应:', { code: data?.code, hasData: !!data?.data });
          if (data && data.code === 200 && data.data) {
            const newToken = data.data.accessToken || data.data.token || '';
            const newRefresh = data.data.refreshToken || data.data.refresh_token || '';
            console.log('[Request] 新token获取成功, 长度:', newToken?.length);
            userStore.setToken(newToken, newRefresh);

            // 🔥 关键修复：刷新token后，重新获取用户信息和路由
            try {
              console.log('[Request] 开始获取用户信息...');
              // 重新获取用户信息
              await userStore.getUserInfo();
              console.log('[Request] 获取用户信息成功, roles:', userStore.roles);

              // 重新初始化路由
              const permissionStore = getPermissionStore();
              const roles = userStore.roles;
              if (roles && roles.length > 0) {
                await permissionStore.initRoutes(roles);
                console.log('[Request] 初始化路由成功');
              } else {
                console.warn('[Request] 用户roles为空, 跳过初始化路由');
              }
            } catch (userInfoError) {
              console.error('[Request] 刷新token后获取用户信息失败:', userInfoError);
            }

            onRrefreshed(newToken);
            // retry original request
            config.headers = config.headers || {};
            config.headers.Authorization = newToken.startsWith('Bearer ') ? newToken : `Bearer ${newToken}`;
            console.log('[Request] 重试原始请求:', config.url);
            // 重试的请求失败不应该清除token，只是返回错误
            return instance.request(config).catch((retryErr) => {
              console.error('[Request] 重试请求失败:', retryErr);
              return Promise.reject(retryErr);
            });
          }
          // refresh failed
          console.log('[Request] 刷新token失败, 跳转登录页');
          userStore.removeToken();
          // 跳转到登录页，使用 replace 避免历史污染
          router.replace({ path: '/login' });
          return Promise.reject(error);
        })
        .catch((err) => {
          // 只有refresh请求本身失败才清除token，重试请求失败不清除
          if (err.config?.url?.includes('/auth/refresh')) {
            console.error('[Request] 刷新token请求失败:', err);
            userStore.removeToken();
            // 跳转到登录页，使用 replace 避免历史污染
            router.replace({ path: '/login' });
          } else {
            console.error('[Request] 请求失败:', err);
          }
          return Promise.reject(err);
        })
        .finally(() => {
          isRefreshing = false;
        });
    }

    // 🔥 非401错误：把后端返回的 msg/message 提取出来，业务代码才能拿到真正的错误信息
    const bizError = extractBackendError(error);

    // retry logic for transient errors
    if (!config || !config.requestOptions?.retry) return Promise.reject(bizError);

    // 避免对 /auth/refresh 接口进行重试，防止无限循环
    if (config.url?.includes('/auth/refresh')) {
      return Promise.reject(bizError);
    }

    config.retryCount = config.retryCount || 0;

    if (config.retryCount >= config.requestOptions.retry.count) return Promise.reject(bizError);

    config.retryCount += 1;

    const backoff = new Promise((resolve) => {
      setTimeout(() => {
        resolve(config);
      }, config.requestOptions.retry.delay || 1);
    });
    config.headers = { ...config.headers, 'Content-Type': ContentTypeEnum.Json };
    return backoff.then((cfg) => instance.request(cfg));
  },
};

function createAxios(opt?: Partial<CreateAxiosOptions>) {
  return new VAxios(
    merge(
      <CreateAxiosOptions>{
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes
        // 例如: authenticationScheme: 'Bearer'
        authenticationScheme: '',
        // 超时
        timeout: 10 * 1000,
        // 携带Cookie
        withCredentials: true,
        // 头信息
        headers: { 'Content-Type': ContentTypeEnum.Json },
        // 数据处理方式
        transform,
        // 配置项，下面的选项都可以在独立的接口请求中覆盖
        requestOptions: {
          // 接口地址
          apiUrl: host,
          // 是否自动添加接口前缀
          isJoinPrefix: true,
          // 接口前缀
          // 例如: https://www.baidu.com/api
          // urlPrefix: '/api'
          urlPrefix: import.meta.env.VITE_API_URL_PREFIX,
          // 是否返回原生响应头 比如：需要获取响应头时使用该属性
          isReturnNativeResponse: false,
          // 需要对返回数据进行处理
          isTransformResponse: true,
          // post请求的时候添加参数到url
          joinParamsToUrl: false,
          // 格式化提交参数时间
          formatDate: true,
          // 是否加入时间戳
          joinTime: true,
          // 是否忽略请求取消令牌
          // 如果启用，则重复请求时不进行处理
          // 如果禁用，则重复请求时会取消当前请求
          ignoreCancelToken: true,
          // 是否携带token
          withToken: true,
          // 重试
          retry: {
            count: 3,
            delay: 1000,
          },
        },
      },
      opt || {},
    ),
  );
}
export const request = createAxios();
