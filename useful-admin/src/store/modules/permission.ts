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

    console.log('[Route] /auth/user/getRouters 原始返回:', JSON.stringify(result));
    // 检查后端返回的数据结构
    // 经过请求拦截器处理，result 已经是路由数据（data 字段的内容）
    let routesData;
    if (Array.isArray(result)) {
      routesData = result;
    } else if (result && Array.isArray(result.data)) {
      // 兼容处理：如果拦截器配置改变，可能仍有 data 包装
      routesData = result.data;
    } else {
      console.error('[Route] 后端返回不是数组，无法生成动态路由:', result);
      return [];
    }

    console.log('[Route] 后端路由数组长度:', routesData.length);

    return convertBackendRoutesToFrontend(routesData);
  } catch (error) {
    console.error('[Route] 从后端获取路由失败:', error);
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
      if (!route) return false;
      if (route.menuType === 'F') return false;
      return route.component || (route.children && route.children.length > 0);
    })
    .map((route) => {
      let path = route.path || '';

      // 父路由（顶级路由）需要前导斜杠
      // 子路由使用相对路径，不要前导斜杠
      if (!isChild) {
        if (path && !path.startsWith('/')) {
          path = '/' + path;
        }
      } else {
        if (path && path.startsWith('/')) {
          path = path.substring(1);
        }
      }

      // 构建完整路径用于调试
      const fullPath = isChild ? `${parentPath}/${path}`.replace(/\/+/g, '/') : path;
      console.log(`[Route] 处理路由: ${route.menuName || route.title}, path: ${path}, fullPath: ${fullPath}, component: ${route.component}, menuType: ${route.menuType}`);

      // 过滤子路由：只保留 M/C 类型，F类型（按钮）不需要生成路由
      const validChildren = (route.children || []).filter(child => {
        if (!child) return false;
        if (child.menuType === 'F') return false;
        return child.component || (child.children && child.children.length > 0);
      });

      // 处理重定向
      let redirect: string | undefined;
      if (route.redirect === 'noRedirect') {
        redirect = undefined;
      } else if (route.redirect) {
        redirect = route.redirect;
      } else if (validChildren.length > 0) {
        const firstChildPath = validChildren[0].path?.replace(/^\//, '') || '';
        redirect = `${fullPath}/${firstChildPath}`.replace(/\/+/g, '/');
      }

      const routeName = fullPath.replace(/\//g, '_').replace(/^_/, '') || `route_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      const frontendRoute: RouteRecordRaw = {
        path: isChild ? path : `/${path}`.replace(/\/+/g, '/'),
        name: routeName,
        redirect: redirect,
        meta: {
          title: route.meta?.title || route.title || '',
          icon: route.meta?.icon || route.icon || '',
          hidden: route.hidden || false,
          alwaysShow: route.alwaysShow || false,
        },
      };

      // M类型（目录）或有子路由且无component的，用Layout
      if (route.component === 'Layout' || (!route.component && validChildren.length > 0)) {
        frontendRoute.component = Layout;
      } else if (route.component) {
        let componentPath = route.component;
        if (componentPath.startsWith('/')) {
          componentPath = componentPath.substring(1);
        }
        componentPath = componentPath.replace(/\.vue$/, '');

        const modulePath = `../../pages/${componentPath}.vue`;
        if (pageModules[modulePath]) {
          frontendRoute.component = pageModules[modulePath];
        } else {
          frontendRoute.component = () => import('../../pages/result/404/index.vue');
        }
      }

      // 只有过滤后还有有效子路由才生成children
      if (validChildren.length > 0) {
        frontendRoute.children = convertBackendRoutesToFrontend(validChildren, true, fullPath);
      }

      console.log(`[Route] 生成路由: name=${routeName}, path=${frontendRoute.path}, hasChildren=${!!frontendRoute.children?.length}, component=${typeof frontendRoute.component}`);

      return frontendRoute;
    });
}

/**
 * 按 path 合并路由：相同 path 的路由合并为一个，children 取并集
 * 用于处理后端返回重复顶级菜单的情况（如两个 /system）
 */
function mergeRoutesByPath(routes: Array<RouteRecordRaw>): Array<RouteRecordRaw> {
  const pathMap = new Map<string, RouteRecordRaw>();

  routes.forEach((route) => {
    if (!route.path) return;

    const existing = pathMap.get(route.path);
    if (!existing) {
      pathMap.set(route.path, route);
      return;
    }

    // 已存在相同 path，合并 children
    const existingChildren = existing.children || [];
    const newChildren = route.children || [];
    const childPathSet = new Set(existingChildren.map(c => c.path));
    const mergedChildren = [...existingChildren];

    newChildren.forEach((child) => {
      if (child.path && !childPathSet.has(child.path)) {
        mergedChildren.push(child);
        childPathSet.add(child.path);
      }
    });

    if (mergedChildren.length > 0) {
      existing.children = mergedChildren;
    }
  });

  return Array.from(pathMap.values());
}

/**
 * 动态添加路由 - 修复版：正确处理嵌套路由
 */
function addRoutes(routes: Array<RouteRecordRaw>) {
  if (!routes || !Array.isArray(routes)) {
    console.log('[Route] 没有路由需要添加');
    return;
  }

  // 深拷贝路由对象，避免响应式/引用污染导致已注册路由被外部修改
  const clonedRoutes: Array<RouteRecordRaw> = JSON.parse(JSON.stringify(routes.map(r => ({
    ...r,
    component: undefined,
    children: r.children,
  }))));
  // 恢复 component 函数引用（JSON 序列化会丢失函数）
  clonedRoutes.forEach((clonedRoute, index) => {
    const originalRoute = routes[index];
    clonedRoute.component = originalRoute.component;
    if (originalRoute.children) {
      clonedRoute.children = originalRoute.children.map((child, cIndex) => {
        const clonedChild = { ...child };
        return clonedChild;
      });
    }
  });

  console.log('[Route] 开始添加路由，数量:', clonedRoutes.length);

  // 打印每个顶级路由及其子路由结构
  clonedRoutes.forEach((r, i) => {
    console.log(`[Route] 待添加路由[${i}]: name=${r.name}, path=${r.path}, childrenCount=${r.children?.length || 0}`);
    if (r.children) {
      r.children.forEach((c, ci) => {
        console.log(`[Route]   └─ 子路由[${ci}]: name=${c.name}, path=${c.path}`);
      });
    }
  });

  // 先移除已存在的同名路由，避免旧动态路由覆盖新的动态路由。
  // 注意：不再按 path 前缀清理所有子路由，否则会把静态路由中独有的子路由（如 /shop/goods/sku/:productId）误删。
  // 静态路由和动态路由会在 initRoutes 中按 path 合并后再注册。
  routes.forEach((route) => {
    if (!route.name) return;
    let count = 0;
    while (router.getRoutes().some(r => r.name === route.name)) {
      try {
        router.removeRoute(route.name);
        count++;
      } catch (e) {
        break;
      }
      if (count > 10) break;
    }
    if (count > 0) {
      console.log(`[Route] 清理同名路由: ${route.name}, 移除数量=${count}`);
    }
  });

  // 顶层路由已经通过 children 数组嵌套了所有子路由，
  // 直接调用 router.addRoute(route) 即可，不需要再单独 addRoute(parentName, child)。
  clonedRoutes.forEach((route) => {
    if (!route || !route.path) {
      console.log('[Route] 跳过无效路由');
      return;
    }

    // 路由名必须存在，否则 Vue Router 在某些场景下会警告。
    if (!route.name) {
      route.name = `route_${(route.path || '').replace(/\//g, '_').replace(/^_/, '')}_${Date.now()}`;
    }

    // 跳过默认路由（登录和404）
    const isDefaultRoute = defaultRouterList.some(
      (defaultRoute) =>
        defaultRoute.name === route.name &&
        (defaultRoute.name === 'login' || defaultRoute.name === '404Page'),
    );
    if (isDefaultRoute) {
      console.log(`[Route] 跳过默认路由: ${route.name}`);
      return;
    }

    console.log(
      `[Route] 添加路由: name=${route.name}, path=${route.path}, component=${
        typeof route.component === 'function' ? 'async' : route.component
      }`,
    );

    try {
      router.addRoute(route);
      // addRoute 后立即检查是否生效
      const addedRoute = router.getRoutes().find(r => r.path === route.path);
      console.log(
        `[Route] 成功添加顶级路由 ${route.name}, 实际children=${addedRoute?.children?.length || 0}`,
      );
    } catch (error) {
      console.error(`[Route] 添加路由失败: ${route.name}`, error);
    }
  });

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

  // 🔥 关键调试：打印当前 Vue Router 中所有已注册路由（包含嵌套子路由）
  const allRegisteredRoutes = router.getRoutes().map((r) => ({
    path: r.path,
    name: r.name,
    redirect: r.redirect,
    childrenCount: r.children?.length || 0,
    children: r.children?.map(c => ({ path: c.path, name: c.name })) || []
  }));
  console.log('[Route] 当前已注册路由:', JSON.stringify(allRegisteredRoutes));
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
        // 静态路由（排除登录和首页重定向）
        const staticRouters = allRoutes.filter(
          (route) => route.path !== '/login' && route.path !== '/',
        );

        let backendRouters: Array<RouteRecordRaw> = [];

        try {
          const userStoreInstance = getUserStore();
          const token = userStoreInstance.token;

          if (token) {
            backendRouters = await fetchRoutesFromBackend(token);
          }
        } catch (error) {
          console.error('[Route] 获取后端路由失败:', error);
        }

        // 合并静态路由和动态路由：相同 path 的顶级路由 children 取并集。
        // 这样既保留静态路由中独有的子路由（如 /shop/goods/sku/:productId），
        // 又补充后端返回的动态子路由（如 /system/user）。
        // 同时也能处理后端返回重复顶级菜单的情况（如两个 /system）。
        const accessedRouters =
          backendRouters && backendRouters.length > 0
            ? [...staticRouters, ...backendRouters]
            : staticRouters;
        const mergedRouters = mergeRoutesByPath(accessedRouters);
        console.log('[Route] 合并去重后路由数量:', mergedRouters.length);

        addRoutes(mergedRouters);

        this.routers = mergedRouters;
        this.dynamicRoutes = mergedRouters;

        return mergedRouters;
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
