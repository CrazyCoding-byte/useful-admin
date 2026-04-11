import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import router, { defaultRouterList, allRoutes } from '@/router';
import { store, getUserStore } from '@/store';
import Layout from '@/layouts/index.vue';
import { request } from '@/utils/request';

// 🔥 预加载所有页面组件，避免动态导入问题
// permission.ts 位于 src/store/modules/，所以到 src/pages/ 的路径是 ../../pages/
const pageModules = import.meta.glob('../../pages/**/*.vue');

/**
 * 从后端获取路由 - 使用 request 实例，这样token过期时会自动刷新
 */
async function fetchRoutesFromBackend(token: string): Promise<Array<RouteRecordRaw>> {
  try {
    console.log('[fetchRoutesFromBackend] 开始获取路由');

    const result = await request.get({ url: '/auth/user/getRouters' });
    console.log('[fetchRoutesFromBackend] 后端返回完整数据:', result);

    // 检查后端返回的数据结构
    let routesData;
    if (result && result.data) {
      routesData = result.data;
    } else if (result && Array.isArray(result)) {
      routesData = result;
    } else {
      console.warn('[fetchRoutesFromBackend] 无法识别后端返回的数据结构');
      return [];
    }
    
    // 🔥 打印后端返回的原始路由结构（前3个）
    console.log('[fetchRoutesFromBackend] 后端路由原始数据（前3个）:');
    routesData.slice(0, 3).forEach((route, index) => {
      console.log(`  [${index}] path: ${route.path}, component: ${route.component}, children:`, route.children?.length || 0);
      if (route.children) {
        route.children.forEach((child, cidx) => {
          console.log(`    [${cidx}] child path: ${child.path}, component: ${child.component}`);
        });
      }
    });
    
    return convertBackendRoutesToFrontend(routesData);

    return [];
  } catch (error) {
    console.error('[fetchRoutesFromBackend] 失败:', error);
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

      // 🔥 修复：如果路由有 children 且 component 为 undefined 或 'Layout'，则使用 Layout
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
        
        console.log('[convertBackendRoutesToFrontend] component 路径:', componentPath);
        
        // 🔥 使用预加载的模块，而不是动态 import
        // 路径格式: ../../pages/system/user/index.vue
        const modulePath = `../../pages/${componentPath}.vue`;
        if (pageModules[modulePath]) {
          frontendRoute.component = pageModules[modulePath];
          console.log('[convertBackendRoutesToFrontend] 找到组件:', modulePath);
        } else {
          console.error('[convertBackendRoutesToFrontend] 未找到组件:', modulePath);
          console.error('[convertBackendRoutesToFrontend] 可用路径:', Object.keys(pageModules).slice(0, 10));
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
        console.log('[addRoutes] 成功(子路由):', route.name, route.path, '→ 父路由:', parentName);
      } else {
        // 添加顶级路由
        router.addRoute(route);
        console.log('[addRoutes] 成功(父路由):', route.name, route.path);
      }

      // 递归处理子路由
      if (route.children && route.children.length > 0) {
        route.children.forEach(child => {
          addRouteRecursive(child, route.name as string);
        });
      }
    } catch (error) {
      console.warn('[addRoutes] 失败:', route.name, error);
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