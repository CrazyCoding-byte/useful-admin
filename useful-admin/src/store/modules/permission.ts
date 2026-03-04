import { defineStore } from 'pinia';
import { RouteRecordRaw } from 'vue-router';
import router, { asyncRouterList, defaultRouterList } from '@/router';
import { store, getUserStore } from '@/store';
import Layout from '@/layouts/index.vue';

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
    });

    if (!response.ok) {
      throw new Error('获取路由失败: ' + response.statusText);
    }

    const data = await response.json();
    if (data.code !== 200) {
      throw new Error('获取路由失败: ' + data.message);
    }

    // 转换后端返回的路由格式为前端路由格式
    return convertBackendRoutesToFrontend(data.data);
  } catch (error) {
    console.error('从后端获取路由失败:', error);
    // 失败时返回默认路由
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
      // 只处理有component的路由，或者有children的路由（作为父菜单）
      return route.component || (route.children && route.children.length > 0);
    })
    .map((route) => {
    // 确保路径以斜杠开头
    let path = route.path || '';
    if (path && !path.startsWith('/')) {
      path = '/' + path;
    }

    // 处理重定向路径
    let redirect = route.redirect === 'noRedirect' ?
      (route.children && route.children.length > 0 ?
        (path + '/' + route.children[0].path).replace('//', '/') : undefined) :
      route.redirect;

    // 确保重定向路径以斜杠开头
    if (redirect && typeof redirect === 'string' && !redirect.startsWith('/')) {
      redirect = '/' + redirect;
    }

    const frontendRoute: RouteRecordRaw = {
      path: path,
      name: route.name || '',
      redirect: redirect,
      meta: {
        title: route.meta?.title || '',
        icon: route.meta?.icon || '',
        hidden: route.hidden || false,
        alwaysShow: route.alwaysShow || false,
      },
    };

    // 处理组件
    if (route.component === 'Layout') {
      frontendRoute.component = Layout;
    } else if (route.component) {
      // 处理页面组件路径
      frontendRoute.component = () => import(`@/pages/${route.component}.vue`);
    }

    // 处理子路由
    if (route.children && route.children.length > 0) {
      // 递归处理子路由，过滤掉没有component的子路由
      frontendRoute.children = convertBackendRoutesToFrontend(route.children);
    }

    return frontendRoute;
  });
}

/**
 * 过滤权限路由
 */
function filterPermissionsRouters(routes: Array<RouteRecordRaw>, roles: Array<unknown>) {
  const res: Array<RouteRecordRaw> = [];

  routes.forEach((route) => {
    const tmp = { ...route };

    // 检查路由是否有权限
    const roleCode = tmp.meta?.roleCode || tmp.name;
    if (!roleCode || roles.includes('all') || roles.indexOf(roleCode) !== -1) {
      // 递归处理子路由
      if (tmp.children && tmp.children.length > 0) {
        tmp.children = filterPermissionsRouters(tmp.children, roles);
        // 如果子路由过滤后为空，则不添加该路由
        if (tmp.children.length === 0) {
          return;
        }
      }
      res.push(tmp);
    }
  });

  return res;
}

/**
 * 动态添加路由
 */
function addRoutes(routes: Array<RouteRecordRaw>) {
  console.log('开始添加动态路由，共', routes.length, '个路由');
  console.log('路由列表:', routes);
  routes.forEach((route) => {
    // 为没有name的路由生成一个name
    if (!route.name) {
      route.name = route.path.replace(/\//g, '_').replace(/^_/, '');
      console.log('为路由生成name:', route.name, '路径:', route.path);
    }

    // 检查是否是默认路由（只跳过登录和404等基础路由）
    const isDefaultRoute = defaultRouterList.some(defaultRoute =>
      defaultRoute.name === route.name &&
      (defaultRoute.name === 'login' || defaultRoute.name === '404Page')
    );
    if (isDefaultRoute) {
      console.log('跳过默认路由:', route.name);
      return;
    }

    // 检查路由是否已存在
    if (!router.hasRoute(route.name)) {
      // 如果是嵌套路由，需要找到父路由
      if (route.meta?.parent) {
        console.log('添加嵌套路由:', route.name, '到父路由', route.meta.parent);
        router.addRoute(route.meta.parent as string, route);
      } else {
        console.log('添加顶级路由:', route.name, route.path);
        router.addRoute(route);
      }
      console.log('添加动态路由成功:', route.name, route.path);
    } else {
      console.log('路由已存在:', route.name);
    }
  });

  // 打印当前所有路由
  const allRoutes = router.getRoutes();
  console.log('当前所有路由:', allRoutes.map(r => ({ name: r.name, path: r.path })));
}

/**
 * 移除所有动态路由
 */
function removeAllDynamicRoutes() {
  // 只移除动态路由，保留默认路由
  asyncRouterList.forEach((route) => {
    if (route.name && router.hasRoute(route.name)) {
      // 跳过默认路由中的路由
      const isDefaultRoute = defaultRouterList.some(defaultRoute => defaultRoute.name === route.name);
      if (!isDefaultRoute) {
        router.removeRoute(route.name);
      }
    }
  });
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
        // 移除所有现有的动态路由
        removeAllDynamicRoutes();

        let accessedRouters: Array<RouteRecordRaw> = [];

        // 尝试从后端获取路由
        console.log('开始获取路由，角色信息:', roles);
        try {
          // 获取当前token
          const userStoreInstance = getUserStore();
          const token = userStoreInstance.token || localStorage.getItem('tdesign-starter');
          console.log('获取到的token:', token ? '存在' : '不存在');

          if (token) {
            console.log('尝试从后端获取路由...');
            // 调用后端获取路由
            const backendRoutes = await fetchRoutesFromBackend(token);
            console.log('后端返回的路由:', backendRoutes);

            if (backendRoutes && backendRoutes.length > 0) {
              accessedRouters = backendRoutes;
              console.log('从后端获取路由成功，使用后端路由');
            } else {
              // 后端路由获取失败或返回空，使用默认路由
              console.warn('从后端获取路由失败或返回空，使用默认路由');
              accessedRouters = defaultRouterList.filter(route => route.path !== '/login' && route.path !== '/');
            }
          } else {
            // 没有token，使用空路由列表
            console.warn('没有token，使用空路由列表');
            accessedRouters = [];
          }
        } catch (error) {
          // 后端路由获取失败，使用默认路由
          console.error('从后端获取路由失败:', error);
          accessedRouters = defaultRouterList.filter(route => route.path !== '/login' && route.path !== '/');
        }
        console.log('最终使用的路由:', accessedRouters);

        // 动态添加路由
        addRoutes(accessedRouters);

        // 更新状态
        this.routers = accessedRouters;
        this.dynamicRoutes = accessedRouters;

        console.log('动态路由初始化完成:', accessedRouters);
      } catch (error) {
        console.error('初始化动态路由失败:', error);
      }
    },
    async resetRoutes() {
      // 移除所有动态路由
      removeAllDynamicRoutes();

      // 重置状态
      this.routers = [];
      this.dynamicRoutes = [];

      console.log('路由已重置');
    },

    restore() {
      // 重置状态
      this.routers = [];
      this.dynamicRoutes = [];

      console.log('路由状态已恢复');
    },
    /**
     * 获取当前用户的完整路由列表
     */
    getRoutes() {
      return this.routers;
    },
  },
});

export function getPermissionStore() {
  return usePermissionStore(store);
}
