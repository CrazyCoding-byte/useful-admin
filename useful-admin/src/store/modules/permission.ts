import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import router, { defaultRouterList, allRoutes } from '@/router';
import { store, getUserStore } from '@/store';
import Layout from '@/layouts/index.vue';
import { request } from '@/utils/request';


// permission.ts 位于 src/store/modules/，所以到 src/pages/ 的路径是 ../../pages/
const pageModules = import.meta.glob('../../pages/**/*.vue');

/**
 * 从后端获取路由 - 使用 request 实例，这样token过期时会自动刷新
 */
async function fetchRoutesFromBackend(token: string): Promise<Array<RouteRecordRaw>> {
  try {

    const result = await request.get({ url: '/auth/user/getRouters' });

    // 检查后端返回的数据结构
    // 经过请求拦截器处理，result 已经是路由数据（data 字段的内容）
    let routesData;
    if (Array.isArray(result)) {
      routesData = result;
    } else if (result && Array.isArray(result.data)) {
      // 兼容处理：如果拦截器配置改变，可能仍有 data 包装
      routesData = result.data;
    } else {
      return [];
    }

    routesData.slice(0, 3).forEach((route, index) => {
      if (route.children) {
        route.children.forEach((child, cidx) => {
        });
      }
    });

    return convertBackendRoutesToFrontend(routesData);

    return [];
  } catch (error) {
    return [];
  }
}

/**
 * 转换后端路由格式为前端路由格式
 * @param backendRoutes 后端路由数组
 * @param isChild 是否为子路由（内部递归使用）
 */
function convertBackendRoutesToFrontend(backendRoutes: any[], isChild: boolean = false, parentPath: string = ''): Array<RouteRecordRaw> {
  if (!Array.isArray(backendRoutes)) {
    return [];
  }

  return backendRoutes
    .filter(route => {
      return route.component || (route.children && route.children.length > 0);
    })
    .map((route) => {
      let path = route.path || '';

      // 父路由（顶级路由）需要前导斜杠
      // 子路由使用相对路径，不要前导斜杠
      if (!isChild) {
        // 父路由：确保有前导斜杠
        if (path && !path.startsWith('/')) {
          path = '/' + path;
        }
      } else {
        // 子路由：确保没有前导斜杠（相对路径）
        if (path && path.startsWith('/')) {
          path = path.substring(1);
        }
      }

      // 构建完整路径用于调试
      const fullPath = isChild ? `${parentPath}/${path}`.replace(/\/+/g, '/') : path;
      console.log(`[Route] 处理路由: ${route.menuName || route.title}, path: ${path}, fullPath: ${fullPath}, component: ${route.component}`);

      let redirect = route.redirect === 'noRedirect' ?
        (route.children && route.children.length > 0 ?
          (isChild ? '' : '/') + route.children[0].path : undefined) :
        route.redirect;

      if (redirect && typeof redirect === 'string' && !redirect.startsWith('/') && !isChild) {
        redirect = '/' + redirect;
      }

      const routeName = route.name || path.replace(/\//g, '_').replace(/^_/, '') || `route_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      const frontendRoute: RouteRecordRaw = {
        path: path,
        name: routeName,
        redirect: redirect,
        meta: {
          title: route.meta?.title || route.title || '',
          icon: route.meta?.icon || route.icon || '',
          hidden: route.hidden || false,
          alwaysShow: route.alwaysShow || false,
        },
      };

      if (route.component === 'Layout' || (!route.component && route.children && route.children.length > 0)) {
        frontendRoute.component = Layout;
      } else if (route.component) {
        // 处理 component 路径，确保格式正确
        let componentPath = route.component;
        // 移除开头的斜杠
        if (componentPath.startsWith('/')) {
          componentPath = componentPath.substring(1);
        }
        // 确保路径不带 .vue 扩展名
        componentPath = componentPath.replace(/\.vue$/, '');


        const modulePath = `../../pages/${componentPath}.vue`;
        if (pageModules[modulePath]) {
          frontendRoute.component = pageModules[modulePath];
        } else {
          // 如果找不到组件，使用 404 页面作为 fallback
          frontendRoute.component = () => import('../../pages/result/404/index.vue');
        }
      }

      // 递归处理子路由，标记为子路由
      if (route.children && route.children.length > 0) {
        frontendRoute.children = convertBackendRoutesToFrontend(route.children, true, fullPath);
      }

      return frontendRoute;
    });
}

/**
 * 动态添加路由 - 修复版：正确处理嵌套路由
 */
function addRoutes(routes: Array<RouteRecordRaw>) {
  if (!routes || !Array.isArray(routes)) {
    console.log('[Route] 没有路由需要添加');
    return;
  }

  console.log('[Route] 开始添加路由，数量:', routes.length);

  // 递归添加路由（包括子路由）
  const addRouteRecursive = (route: RouteRecordRaw, parentName?: string) => {
    if (!route || !route.path) {
      console.log('[Route] 跳过无效路由');
      return;
    }

    // 确保路由有名称
    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
    }

    console.log(`[Route] 添加路由: name=${route.name}, path=${route.path}, parent=${parentName || 'root'}, component=${typeof route.component === 'function' ? 'async' : route.component}`);

    // 跳过默认路由（登录和404）
    const isDefaultRoute = defaultRouterList.some(defaultRoute =>
      defaultRoute.name === route.name &&
      (defaultRoute.name === 'login' || defaultRoute.name === '404Page')
    );
    if (isDefaultRoute) {
      console.log(`[Route] 跳过默认路由: ${route.name}`);
      return;
    }

    try {
      if (parentName) {
        // 添加子路由到指定父路由
        router.addRoute(parentName, route);
        console.log(`[Route] 成功添加子路由 ${route.name} 到父路由 ${parentName}`);
      } else {
        // 添加顶级路由
        router.addRoute(route);
        console.log(`[Route] 成功添加顶级路由 ${route.name}`);
      }

      // 递归处理子路由
      if (route.children && route.children.length > 0) {
        console.log(`[Route] 处理子路由，数量: ${route.children.length}`);
        route.children.forEach(child => {
          addRouteRecursive(child, route.name as string);
        });
      }
    } catch (error) {
      console.error(`[Route] 添加路由失败: ${route.name}`, error);
    }
  };

  // 开始递归添加所有路由
  routes.forEach(route => addRouteRecursive(route));

  // 重新添加404路由，确保它始终在最后匹配
  // 先移除旧的404路由（如果存在）
  try {
    router.removeRoute('404Page');
  } catch (e) {
    // 忽略移除失败的错误
  }
  // 添加新的404路由到最后
  router.addRoute({
    path: '/:pathMatch(.*)*',
    name: '404Page',
    component: () => import('../../pages/result/404/index.vue'),
  });

  console.log('[Route] 路由添加完成');
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    whiteListRouters: ['/login'],
    routers: [],
    dynamicRoutes: [],
  }),
  persist: {
    paths: ['whiteListRouters'], // 只持久化白名单路由
  },
  actions: {
    async initRoutes(roles: Array<unknown>) {
      try {

        let accessedRouters: Array<RouteRecordRaw> = [];

        try {
          const userStoreInstance = getUserStore();
          const token = userStoreInstance.token;

          if (token) {
            const backendRoutes = await fetchRoutesFromBackend(token);


            if (backendRoutes && backendRoutes.length > 0) {
              accessedRouters = backendRoutes;
            } else {
              accessedRouters = allRoutes.filter(route =>
                route.path !== '/login' && route.path !== '/'
              );
            }
          }
        } catch (error) {
          accessedRouters = allRoutes.filter(route =>
            route.path !== '/login' && route.path !== '/'
          );
        }

        addRoutes(accessedRouters);

        this.routers = accessedRouters;
        this.dynamicRoutes = accessedRouters;

        return accessedRouters;
      } catch (error) {
        return [];
      }
    },

    getRoutes() {
      return this.routers;
    },
  },
});

export function getPermissionStore() {
  return usePermissionStore(store);
}
