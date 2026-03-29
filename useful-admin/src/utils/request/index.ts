// axios配置  可自行根据项目进行更改，只需更改该文件即可，其他文件可以不动
import isString from 'lodash/isString';
import merge from 'lodash/merge';
import axios from 'axios';
import { getUserStore } from '@/store/modules/user';
import type { AxiosTransform, CreateAxiosOptions } from './AxiosTransform';
import { VAxios } from './Axios';
import proxy from '@/config/proxy';
import { joinTimestamp, formatRequestDate, setObjToUrlParams } from './utils';
import { TOKEN_NAME } from '@/config/global';

const env = import.meta.env.MODE || 'development';

// 如果是mock模式 或 没启用直连代理 就不配置host 会走本地Mock拦截 或 Vite 代理
const host = env === 'mock' || !proxy.isRequestProxy ? '' : proxy[env].host;

// 全局无感刷新状态
let isRefreshing = false;
let requests: ((token: string) => void)[] = [];

// 数据处理，方便区分多种处理方式
const transform: AxiosTransform = {
  // 处理请求数据。如果数据不是预期格式，可直接抛出错误
  transformRequestHook: (res, options) => {
    const { isTransformResponse, isReturnNativeResponse } = options;

    // 如果204无内容直接返回
    const method = res.config.method?.toLowerCase();
    if (res.status === 204 || method === 'put' || method === 'patch') {
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
    const { code, data: responseData } = data || {};

    // 这里逻辑可以根据项目进行修改
    const hasSuccess = data && (code === 0 || code === 200);
    if (hasSuccess) {
      return responseData || data;
    }

    throw new Error(`请求接口错误, 错误码: ${code || '未知'}`);
  },

  // 请求前处理配置
  beforeRequestHook: (config, options) => {
    const { apiUrl, isJoinPrefix, urlPrefix, joinParamsToUrl, formatDate, joinTime = true } = options;

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
    const token = userStore.getAccessToken;
    if (token && token !== 'undefined' && token !== 'null' && (config as Recordable)?.requestOptions?.withToken !== false) {
      // jwt token
      (config as Recordable).headers.Authorization = options.authenticationScheme
        ? `${options.authenticationScheme} ${token}`
        : token;
    }
    return config;
  },

  // 响应拦截器处理
  responseInterceptors: (res) => {
    return res;
  },

  // 响应错误处理（含401刷新逻辑）
  responseInterceptorsCatch: (error: any) => {
    const { config, response } = error;
    // 如果没有配置或没有响应，退回原错误处理（包含原有重试逻辑）
    if (!config || !response) return Promise.reject(error);

    // 检测是否是token失效（后端可能返回 401 或 body.code === 401）
    const isTokenInvalid = response.status === 401 || (response.data && (response.data.error === 'invalid_token' || response.data.code === 401));
    if (!isTokenInvalid) {
      // 保持原来的重试逻辑（如果有配置重试）
      if (!config.requestOptions || !config.requestOptions.retry) return Promise.reject(error);

      config.retryCount = config.retryCount || 0;
      if (config.retryCount >= config.requestOptions.retry.count) return Promise.reject(error);
      config.retryCount += 1;
      const backoff = new Promise((resolve) => {
        setTimeout(() => {
          resolve(config);
        }, config.requestOptions.retry.delay || 1);
      });
      config.headers = { ...config.headers, 'Content-Type': 'application/json;charset=UTF-8' };
      return backoff.then((cfg) => request.request(cfg));
    }

    // 从 user store 获取 refresh token 与 clientId
    const userStore = getUserStore();
    const refreshToken = userStore.getRefreshToken;
    const clientId = userStore.getClientId || '';
    if (!refreshToken) {
      // 无刷新令牌，直接清理并登出（调用 store 的 removeToken）
      userStore.removeToken();
      return Promise.reject(error);
    }

    // 刷新逻辑：防止并发刷新，排队等待
    const handleRefresh = async () => {
      try {
        isRefreshing = true;
        const params = new URLSearchParams();
        params.append('client_id', clientId);
        params.append('refresh_token', refreshToken);
        params.append('grant_type', 'refresh_token');

        const res = await axios.post('/auth/refresh', params.toString(), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        });

        const data = res.data;
        if (!data || data.code !== 200) throw new Error('刷新失败');

        // 后端返回的 token 结构放在 data.data
        const newTokenData = data.data;
        const newAccess = newTokenData.accessToken || newTokenData.token || '';
        const newRefresh = newTokenData.refreshToken || newTokenData.refresh_token || '';

        if (newAccess || newRefresh) userStore.setToken(newAccess, newRefresh);

        // 逐个重试队列中的请求
        requests.forEach((cb) => cb(newAccess));
        requests = [];

        // 重试当前请求
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${newAccess}`;
        return axios(config);
      } catch (err) {
        // 刷新失败，清理并退回错误（调用方可触发跳转登录）
        userStore.removeToken();
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    };

    if (isRefreshing) {
      return new Promise((resolve) => {
        requests.push((token: string) => {
          config.headers = config.headers || {};
          config.headers.Authorization = `Bearer ${token}`;
          resolve(axios(config));
        });
      });
    } else {
      return handleRefresh();
    }
  },
};

function createAxios(opt?: Partial<CreateAxiosOptions>) {
  return new VAxios(
    merge(
      <CreateAxiosOptions>{
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes
        // 例如: authenticationScheme: 'Bearer'
        authenticationScheme: 'Bearer',
        // 超时
        timeout: 10 * 1000,
        // 携带Cookie
        withCredentials: true,
        // 头信息
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        // 数据处理方式
        transform,
        // 配置项，下面的选项都可以在独立的接口请求中覆盖
        requestOptions: {
          // 接口地址
          apiUrl: host,
          // 是否自动添加接口前缀
          isJoinPrefix: false,
          // 接口前缀
          // 例如: https://www.baidu.com/api
          // urlPrefix: '/api'
          urlPrefix: '',
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
          // 忽略重复请求
          ignoreRepeatRequest: true,
          // 是否携带token
          withToken: true,
          // 重试
          retry: {
            count: 0,
            delay: 1000,
          },
        },
      },
      opt || {},
    ),
  );
}
export const request = createAxios();
