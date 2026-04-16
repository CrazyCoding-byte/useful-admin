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
    let routesData;
    if (result && result.data) {
      routesData = result.data;
    } else if (result && Array.isArray(result)) {
      routesData = result;
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
function convertBackendRoutesToFrontend(backendRoutes: any[], isChild: boolean = false): Array<RouteRecordRaw> {
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
        frontendRoute.children = convertBackendRoutesToFrontend(route.children, true);
      }

      return frontendRoute;
    });
}

/**
 * 动态添加路由 - 修复版：正确处理嵌套路由
 */
function addRoutes(routes: Array<RouteRecordRaw>) {
  if (!routes || !Array.isArray(routes)) {
    return;
  }

  // 递归添加路由（包括子路由）
  const addRouteRecursive = (route: RouteRecordRaw, parentName?: string) => {
    if (!route || !route.path) {
      return;
    }

    // 确保路由有名称
    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
    }

    // 跳过默认路由（登录和404）
    const isDefaultRoute = defaultRouterList.some(defaultRoute =>
      defaultRoute.name === route.name &&
      (defaultRoute.name === 'login' || defaultRoute.name === '404Page')
    );
    if (isDefaultRoute) {
      return;
    }

    try {
      if (parentName) {
        // 添加子路由到指定父路由
        router.addRoute(parentName, route);
      } else {
        // 添加顶级路由
        router.addRoute(route);
      }

      // 递归处理子路由
      if (route.children && route.children.length > 0) {
        route.children.forEach(child => {
          addRouteRecursive(child, route.name as string);
        });
      }
    } catch (error) {
    }
  };

  // 开始递归添加所有路由
  routes.forEach(route => addRouteRecursive(route));
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
