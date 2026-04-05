import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import router, { defaultRouterList } from '@/router';
import { store, getUserStore } from '@/store';
import Layout from '@/layouts/index.vue';

// 存储已注册的动态路由名称，用于后续清理
const registeredDynamicRouteNames = new Set<string>();

/**
 * 从后端获取路由
 */
async function fetchRoutesFromBackend(token: string): Promise<Array<RouteRecordRaw>> {
  try {
    const response = await fetch('/auth/user/getRouters', {
      method: 'GET',
      headers: {
        'Authorization': 'Bearer ' + token,
        'Content-Type': 'application/json',
      },
      signal: AbortSignal.timeout(5000)
    });

    if (!response.ok) {
      throw new Error('获取路由失败: ' + response.statusText);
    }

    const data = await response.json();
    if (data.code !== 200) {
      throw new Error('获取路由失败: ' + data.message);
    }

    return convertBackendRoutesToFrontend(data.data);
  } catch (error) {
    console.error('从后端获取路由失败:', error);
    return [];
  }
}

/**
 * 转换后端路由格式为前端路由格式
 * @param backendRoutes 后端返回的路由数组
 * @param parentPath 父路由的路径（用于处理嵌套路由）
 */
function convertBackendRoutesToFrontend(backendRoutes: any[], parentPath: string = ''): Array<RouteRecordRaw> {
  if (!Array.isArray(backendRoutes)) {
    return [];
  }

  return backendRoutes
    .filter(route => route.component || (route.children && route.children.length > 0))
    .map((route) => {
    let path = route.path || '';

    // 顶级路由需要确保以 / 开头，子路由保持相对路径
    if (!parentPath) {
      if (path && !path.startsWith('/')) {
        path = '/' + path;
      }
    }

    // 用 path 生成路由名称
    const routeName = path.replace(/\//g, '_').replace(/^_/, '');

    let redirect = route.redirect === 'noRedirect' ?
      (route.children && route.children.length > 0 ?
        (path + '/' + route.children[0].path).replace('//', '/') : undefined) :
      route.redirect;

    if (redirect && typeof redirect === 'string' && !redirect.startsWith('/')) {
      redirect = '/' + redirect;
    }

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

    // 处理子路由
    if (route.children && route.children.length > 0) {
      frontendRoute.children = convertBackendRoutesToFrontend(route.children, path).map(childRoute => {
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

  routes.forEach((route) => {
    if (!route || !route.path) {
      return;
    }

    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
    }

    // 跳过默认路由
    if (route.name === 'login' || route.name === '404Page') {
      return;
    }

    try {
      // 标记为动态路由，便于后续清理
      if (!route.meta) route.meta = {} as any;
      (route.meta as any).dynamic = true;

      // 如果路由已经存在（按 path 判断），跳过添加，避免与静态默认路由或已注册路由冲突
      const existingByPath = router.getRoutes().find(r => r.path === route.path);
      if (existingByPath) {
        console.warn('[addRoutes] 已存在相同 path 的路由，跳过添加:', route.path, '存在路由 name=', existingByPath.name);
        return;
      }

      // 为动态路由添加统一前缀，避免与静态路由 name 冲突
      if (route.name && typeof route.name === 'string' && !route.name.startsWith('dyn_')) {
        route.name = `dyn_${route.name}`;
      }
      // 再次检查 name 是否已被注册，若已注册则跳过
      if (route.name && router.hasRoute(route.name as string)) {
        console.log('[addRoutes] 路由名已被占用，跳过:', route.name);
        return;
      }

      if (route.meta?.parent) {
        // 嵌套路由：添加到父路由下
        if (router.hasRoute(route.meta.parent as string)) {
          router.addRoute(route.meta.parent as string, route);
          console.log('[addRoutes] 添加嵌套路由:', route.meta.parent, '/', route.name);
          if (route.name) registeredDynamicRouteNames.add(route.name as string);
        } else {
          console.warn('[addRoutes] 父路由不存在，延迟添加:', route.meta.parent);
        }
      } else {
        // 顶级路由
        router.addRoute(route);
        console.log('[addRoutes] 添加顶级路由:', route.name, route.path);
        if (route.name) registeredDynamicRouteNames.add(route.name as string);
      }
    } catch (error) {
      console.error('[addRoutes] 添加路由失败:', route.name, error);
    }
  });
}

/**
 * 移除所有动态路由
 */
function removeAllDynamicRoutes() {
  console.log('[removeAllDynamicRoutes] 将移除已注册的动态路由数:', registeredDynamicRouteNames.size);
  console.log('[removeAllDynamicRoutes] 已注册动态路由列表:', Array.from(registeredDynamicRouteNames));
  registeredDynamicRouteNames.forEach((name) => {
    try {
      if (router.hasRoute(name) && name !== 'login' && name !== '404Page') {
        router.removeRoute(name);
        console.log('[removeAllDynamicRoutes] 移除已注册动态路由:', name);
      }
    } catch (error) {
      console.error('[removeAllDynamicRoutes] 移除路由失败:', name, error);
    }
  });
  // 清空已注册列表
  registeredDynamicRouteNames.clear();
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    whiteListRouters: ['/login'],
    routers: [],
    dynamicRoutes: [],
  }),
  actions: {
    async initRoutes(roles: Array<unknown>) {
      try {
        console.log('[initRoutes] 开始初始化路由，角色:', roles);

        // 移除所有现有路由
        removeAllDynamicRoutes();
        console.log('[initRoutes] 移除现有路由完成');

        let accessedRouters: Array<RouteRecordRaw> = [];

        // 获取当前token
        const userStoreInstance = getUserStore();
        const token = userStoreInstance.token || localStorage.getItem('tdesign-starter');

        if (token) {
          const backendRoutes = await fetchRoutesFromBackend(token);
          console.log('[initRoutes] 后端返回路由数量:', backendRoutes.length);

          if (backendRoutes && backendRoutes.length > 0) {
            accessedRouters = backendRoutes;
          } else {
            accessedRouters = defaultRouterList.filter(route => route.path !== '/login' && route.path !== '/');
          }
        } else {
          accessedRouters = [];
        }

        // 添加路由
        addRoutes(accessedRouters);
        console.log('[initRoutes] 添加路由完成');

        // 验证路由是否添加成功
        const allRoutes = router.getRoutes();
        console.log('[initRoutes] 当前所有路由（name,path,dynamic）:', allRoutes.map(r => ({ name: r.name, path: r.path, dynamic: !!(r.meta && (r.meta as any).dynamic) })));

        // 更新状态
        this.routers = accessedRouters;
        this.dynamicRoutes = accessedRouters;

        console.log('[initRoutes] 初始化完成');
        return accessedRouters;
      } catch (error) {
        console.error('[initRoutes] 初始化失败:', error);
        // 清空状态，避免无限循环
        this.routers = [];
        this.dynamicRoutes = [];
        return [];
      }
    },

    async resetRoutes() {
      removeAllDynamicRoutes();
      this.routers = [];
      this.dynamicRoutes = [];
    },

    restore() {
      this.routers = [];
      this.dynamicRoutes = [];
    },

    getRoutes() {
      return this.routers;
    },
  },
});

export function getPermissionStore() {
  return usePermissionStore(store);
}
