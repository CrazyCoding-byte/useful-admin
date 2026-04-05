import { MessagePlugin } from 'tdesign-vue-next';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';

import { getPermissionStore, getUserStore } from '@/store';
import router from '@/router';

NProgress.configure({ showSpinner: false });

// 路由初始化锁
let isInitializing = false;

router.beforeEach((to, from, next) => {
  NProgress.start();

  const userStore = getUserStore();
  const permissionStore = getPermissionStore();
  const { whiteListRouters } = permissionStore;
  const { token } = userStore;

  console.log('[路由守卫] 开始处理导航到:', to.path);
  console.log('[路由守卫] token存在:', !!token);
  console.log('[路由守卫] hasData:', permissionStore.routers.length > 0);
  console.log('[路由守卫] to.name:', to.name);
  console.log('[路由守卫] redirectedFrom:', to.redirectedFrom);

  // 1. 白名单路由直接放行
  if (whiteListRouters.includes(to.path)) {
    console.log('[路由守卫] 白名单路由，直接放行');
    next();
    return;
  }

  // 2. 无token跳转登录页
  if (!token || token === 'undefined' || token === 'null') {
    console.log('[路由守卫] 无token，跳转登录页');
    next({ path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } });
    return;
  }

  // 3. 检查是否需要初始化路由
  // 🔥 关键修复：只要 Store 有数据，就直接放行
  // 因为默认路由已经存在，不需要每次都重新初始化
  if (permissionStore.routers && permissionStore.routers.length > 0) {
    console.log('[路由守卫] Store有数据，直接放行');
    next();
    return;
  }

  // 如果正在初始化中，等待
  if (isInitializing) {
    console.log('[路由守卫] 正在初始化中，等待...');
    const timer = setInterval(() => {
      if (!isInitializing) {
        clearInterval(timer);
        console.log('[路由守卫] 初始化完成，继续导航');
        next({ ...to, replace: true });
      }
    }, 50);
    return;
  }

  // 开始初始化
  console.log('[路由守卫] 开始初始化路由...');
  isInitializing = true;

  permissionStore.initRoutes(userStore.roles || ['admin'])
    .then((routes) => {
      console.log('[路由守卫] 路由初始化完成');
      isInitializing = false;

      // 检查初始化是否成功
      if (routes && routes.length > 0) {
        // 初始化成功，导航到目标路由
        next({ ...to, replace: true });
      } else {
        // 初始化失败，跳转到系统首页
        console.warn('[路由守卫] 路由初始化失败，跳转到系统首页');
        next('/system');
      }
    })
    .catch((error) => {
      isInitializing = false;
      console.error('[路由守卫] 路由初始化失败:', error);
      MessagePlugin.error('权限加载失败');
      permissionStore.routers = [];
      permissionStore.dynamicRoutes = [];
      // 跳转到系统首页，避免无限循环
      next('/system');
    });
});

router.afterEach(() => {
  NProgress.done();
});
