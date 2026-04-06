import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import router, { defaultRouterList, allRoutes } from '@/router';
import { store, getUserStore } from '@/store';
import Layout from '@/layouts/index.vue';
import { request } from '@/utils/request';

/**
 * 从后端获取路由 - 使用 request 实例，这样token过期时会自动刷新
 */
async function fetchRoutesFromBackend(token: string): Promise<Array<RouteRecordRaw>> {
  try {
    console.log('[fetchRoutesFromBackend] 开始获取路由');

    const result = await request.get({ url: '/auth/user/getRouters' });
    console.log('[fetchRoutesFromBackend] 后端返回完整数据:', result);

    // 检查后端返回的数据结构
    if (result && result.data) {
      console.log('[fetchRoutesFromBackend] 从 data 字段获取路由:', result.data);
      return convertBackendRoutesToFrontend(result.data);
    } else if (result && Array.isArray(result)) {
      console.log('[fetchRoutesFromBackend] 直接从 result 获取路由:', result);
      return convertBackendRoutesToFrontend(result);
    } else {
      console.warn('[fetchRoutesFromBackend] 无法识别后端返回的数据结构');
    }

    return [];
  } catch (error) {
    console.error('[fetchRoutesFromBackend] 失败:', error);
    return [];
  }
}

/**
 * 转换后端路由格式为前端路由格式
 */
function convertBackendRoutesToFrontend(backendRoutes: any[]): Array<RouteRecordRaw> {
  if (!Array.isArray(backendRoutes)) {
    return [];
  }

  return backendRoutes
    .filter(route => {
      return route.component || (route.children && route.children.length > 0);
    })
    .map((route) => {
      let path = route.path || '';
      if (path && !path.startsWith('/')) {
        path = '/' + path;
      }

      let redirect = route.redirect === 'noRedirect' ?
        (route.children && route.children.length > 0 ?
          (path + '/' + route.children[0].path).replace('//', '/') : undefined) :
        route.redirect;

      if (redirect && typeof redirect === 'string' && !redirect.startsWith('/')) {
        redirect = '/' + redirect;
      }

      const routeName = route.name || path.replace(/\//g, '_').replace(/^_/, '');

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

      if (route.component === 'Layout') {
        frontendRoute.component = Layout;
      } else if (route.component) {
        frontendRoute.component = () => import(`@/pages/${route.component}.vue`);
      }

      if (route.children && route.children.length > 0) {
        frontendRoute.children = convertBackendRoutesToFrontend(route.children).map(childRoute => {
          if (!childRoute.meta) {
            childRoute.meta = {};
          }
          childRoute.meta.parent = routeName;
          return childRoute;
        });
      }

      return frontendRoute;
    });
}

/**
 * 动态添加路由
 */
function addRoutes(routes: Array<RouteRecordRaw>) {
  if (!routes || !Array.isArray(routes)) {
    return;
  }

  // 先添加所有父路由（没有parent的）
  routes.forEach((route) => {
    if (!route || !route.path) {
      return;
    }

    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
    }

    const isDefaultRoute = defaultRouterList.some(defaultRoute =>
      defaultRoute.name === route.name &&
      (defaultRoute.name === 'login' || defaultRoute.name === '404Page')
    );
    if (isDefaultRoute) {
      return;
    }

    try {
      if (!route.meta?.parent) {
        router.addRoute(route);
        console.log('[addRoutes] 成功(父路由):', route.name, route.path);
      }
    } catch (error) {
      console.warn('[addRoutes] 失败(父路由):', error);
    }
  });

  // 再添加所有子路由（有parent的）
  routes.forEach((route) => {
    if (!route || !route.path) {
      return;
    }

    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
    }

    try {
      if (route.meta?.parent) {
        router.addRoute(route.meta.parent as string, route);
        console.log('[addRoutes] 成功(子路由):', route.name, route.path, '→', route.meta.parent);
      }
    } catch (error) {
      console.warn('[addRoutes] 失败(子路由):', error);
    }
  });
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    whiteListRouters: ['/login'],
    routers: [],
    dynamicRoutes: [],
  }),
  persist: {
    // 🔥 禁用路由数据的持久化，因为 Vue Router 实例在刷新时会重置
    // 如果持久化了路由数据，会导致 Store 有数据但 Router 没有的问题
    paths: ['whiteListRouters'], // 只持久化白名单路由
  },
  actions: {
    async initRoutes(roles: Array<unknown>) {
      try {
        console.log('[initRoutes] 开始，角色:', roles);

        let accessedRouters: Array<RouteRecordRaw> = [];

        try {
          const userStoreInstance = getUserStore();
          const token = userStoreInstance.token;

          if (token) {
            console.log('[initRoutes] 从后端获取路由...');
            const backendRoutes = await fetchRoutesFromBackend(token);

            console.log('[initRoutes] 后端返回数量:', backendRoutes.length);

            if (backendRoutes && backendRoutes.length > 0) {
              accessedRouters = backendRoutes;
            } else {
              console.warn('[initRoutes] 使用默认路由');
              accessedRouters = allRoutes.filter(route =>
                route.path !== '/login' && route.path !== '/'
              );
            }
          }
        } catch (error) {
          console.error('[initRoutes] 获取路由失败:', error);
          accessedRouters = allRoutes.filter(route =>
            route.path !== '/login' && route.path !== '/'
          );
        }

        addRoutes(accessedRouters);
        console.log('[initRoutes] 添加完成');

        this.routers = accessedRouters;
        this.dynamicRoutes = accessedRouters;

        console.log('[initRoutes] 完成');
        return accessedRouters;
      } catch (error) {
        console.error('[initRoutes] 失败:', error);
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
