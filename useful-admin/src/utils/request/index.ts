// axios配置  可自行根据项目进行更改，只需更改该文件即可，其他文件可以不动
import merge = require('lodash/merge');
import { transform } from './AxiosTransform';
import type { CreateAxiosOptions } from './AxiosTransform';
import { VAxios } from './Axios';
import proxy from '@/config/proxy';

const env = import.meta.env.MODE || 'development';

// 如果是mock模式 或 没启用直连代理 就不配置host 会走本地Mock拦截 或 Vite 代理
const host = env === 'mock' || !proxy.isRequestProxy ? '' : proxy[env].host;

// 数据处理由 `AxiosTransform.ts` 中的 `transform` 提供

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
