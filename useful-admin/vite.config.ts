import { ConfigEnv, UserConfig, loadEnv } from 'vite';
import { viteMockServe } from 'vite-plugin-mock';
import createVuePlugin from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';
import svgLoader from 'vite-svg-loader';

import path from 'path';

const CWD = process.cwd();

// https://vitejs.dev/config/
export default ({ mode }: ConfigEnv): UserConfig => {
  const { VITE_BASE_URL } = loadEnv(mode, CWD);
  return {
    base: VITE_BASE_URL,
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },

    css: {
      preprocessorOptions: {
        less: {
          modifyVars: {
            hack: `true; @import (reference) "${path.resolve('src/style/variables.less')}";`,
          },
          math: 'strict',
          javascriptEnabled: true,
        },
      },
    },

    plugins: [
      createVuePlugin(),
      vueJsx(),
      viteMockServe({
        mockPath: 'mock',
        localEnabled: true,
      }),
      svgLoader(),
    ],

    server: {
      port: 3002,
      host: '0.0.0.0',
      proxy: {
        // 统一走网关（9100），由网关根据路径前缀路由到对应微服务
        '/auth': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/system': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/product': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/coupon': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/wms': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/post': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/shop': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
        },
        '/api': {
          target: 'http://127.0.0.1:9100/',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  };
};
