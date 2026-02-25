import { MessagePlugin } from 'tdesign-vue-next';
import NProgress from 'nprogress'; // progress bar
import 'nprogress/nprogress.css'; // progress bar style

import { getPermissionStore, getUserStore } from '@/store';
import router from '@/router';

NProgress.configure({ showSpinner: false });

// 注意：只在 beforeEach 里 start()，所有 done() 都移到 afterEach 里！
router.beforeEach(async (to, from, next) => {
  NProgress.start(); // 只有这里启动进度条

  const userStore = getUserStore();
  const permissionStore = getPermissionStore();
  const { whiteListRouters } = permissionStore;

  const { token } = userStore;
  console.log("当前的token:", token);

  // 1. 白名单路由：直接放行（无需任何路由初始化）
  if (whiteListRouters.includes(to.path)) {
    // 有token的话，登录页重定向到仪表盘（白名单里的特殊处理）
    if (token && to.path === '/login') {
      next('/dashboard/base');
      return;
    }
    next(); // 其他白名单路由直接放行
    return; // 注意：这里不调用done()！
  }

  // 2. 非白名单路由，无token → 跳登录页
  console.log(token)
  if (!token) {
    next({
      path: '/login',
      query: { redirect: encodeURIComponent(to.fullPath) },
    });
    return;
  }

  // 3. 有token，处理动态路由
  // 3.1 登录页重定向（非白名单里的登录页已经被上面过滤了，这里是冗余保护）
    if (to.path === '/login') {
      // 检查后端是否返回了路由
      if (permissionStore.routers && permissionStore.routers.length > 0) {
        // 有后端路由，跳转到第一个可用路由
        const firstRoute = permissionStore.routers[0];
        const redirectPath = firstRoute.path || '/';
        console.log('跳转到第一个后端路由:', redirectPath);
        next(redirectPath);
      } else {
        // 没有后端路由，跳转到默认的仪表盘页面
        console.log('没有后端路由，跳转到默认仪表盘页面');
        next('/dashboard/base');
      }
      return;
    }

  // 3.2 获取角色信息（确保角色存在）
  let { roles } = userStore;
  if (!roles || roles.length === 0) {
    try {
      await userStore.getUserInfo();
      roles = userStore.roles; // 重新获取角色
      if (!roles || roles.length === 0) {
        throw new Error('用户无角色权限');
      }
    } catch (error) {
      console.error('获取用户信息失败:', error);
      // 清除token，跳登录页
      userStore.logout();
      permissionStore.restore();
      next({
        path: '/login',
        query: { redirect: encodeURIComponent(to.fullPath) },
      });
      return;
    }
  }

  // 3.3 初始化动态路由（仅当路由未初始化时）
  console.log('开始处理动态路由，当前路由状态:', {
    routersLength: permissionStore.routers.length,
    targetPath: to.path,
    token: token
  });

  // 检查路由是否已经初始化
  if (permissionStore.routers.length === 0) {
    try {
      // 初始化路由
      await permissionStore.initRoutes(roles);
      console.log('路由初始化完成，检查目标路由:', to.path);

      // 检查后端是否返回了路由
      if (permissionStore.routers && permissionStore.routers.length > 0) {
        // 打印当前所有路由，以便调试
        const allRoutes = router.getRoutes();
        console.log('当前router实例中的路由:', allRoutes.map(r => ({ name: r.name, path: r.path })));

        // 检查目标路由是否存在于后端返回的路由中
        let targetRouteExists = false;
        let targetRoute = null;

        // 递归检查路由是否存在
        function checkRouteExists(routes: any[], path: string) {
          for (const route of routes) {
            if (route.path === path) {
              targetRouteExists = true;
              targetRoute = route;
              return;
            }
            if (route.children && route.children.length > 0) {
              checkRouteExists(route.children, path);
            }
          }
        }

        checkRouteExists(permissionStore.routers, to.path);
        console.log('目标路由检查结果:', { targetRouteExists, targetRoute });

        // 路由初始化完成后，重新导航到目标路由
        try {
          console.log('路由初始化完成，重新导航到目标路由:', to.path);
          next({ ...to, replace: true });
        } catch (error) {
          console.error('导航失败:', error);
          // 导航失败，跳转到第一个后端路由
          const firstRoute = permissionStore.routers[0];
          const redirectPath = firstRoute.path || '/';
          console.log('导航失败，跳转到第一个后端路由:', redirectPath);
          next(redirectPath);
        }
      } else {
        // 没有后端路由，跳转到登录页
        console.log('没有后端路由，跳转到登录页');
        next('/login');
      }
    } catch (error) {
      console.error('初始化路由失败:', error);
      MessagePlugin.error('权限初始化失败，请重新登录');
      userStore.logout();
      permissionStore.restore();
      next('/login');
    }
  } else {
    // 路由已初始化，直接放行
    console.log('路由已初始化，直接放行:', to.path);
    next();
  }
});

router.afterEach((to) => {
  // 所有导航完成后，统一结束进度条（唯一的done()调用处）
  NProgress.done();

  // 登录页清除状态（原有逻辑保留）
  if (to.path === '/login') {
    const userStore = getUserStore();
    const permissionStore = getPermissionStore();
    userStore.logout();
    permissionStore.restore();
  }
});
