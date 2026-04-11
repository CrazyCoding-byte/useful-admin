import { MessagePlugin } from 'tdesign-vue-next';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';

import { getPermissionStore, getUserStore } from '@/store';
import router from '@/router';

NProgress.configure({ showSpinner: false });

// 路由初始化标志
let routesInitialized = false;

router.beforeEach(async (to, from, next) => {
  NProgress.start();

  const userStore = getUserStore();
  const permissionStore = getPermissionStore();
  const { whiteListRouters } = permissionStore;
  const { token } = userStore;

  console.log('[路由守卫] 导航到:', to.path, 'token:', !!token, 'routesInitialized:', routesInitialized);

  if (whiteListRouters.includes(to.path)) {
    console.log('[路由守卫] 白名单路由，直接放行');
    next();
    return;
  }

  if (!token || token === 'undefined' || token === 'null') {
    console.log('[路由守卫] 无 token，跳转登录');
    next({ path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } });
    return;
  }

  // 只在路由未初始化时获取路由
  if (!routesInitialized) {
    try {
      console.log('[路由守卫] 开始获取路由...');
      const routes = await permissionStore.initRoutes(userStore.roles || ['admin']);
      console.log('[路由守卫] 获取路由完成，数量:', routes?.length);
      routesInitialized = true;

      // 🔥 修复：直接使用 replace 重新导航，确保路由完全生效
      console.log('[路由守卫] 路由获取完成，重新导航到:', to.fullPath);
      next({ path: to.fullPath, replace: true });
    } catch (error) {
      console.error('[路由守卫] 获取路由失败:', error);
      MessagePlugin.error('权限加载失败');
      next('/login');
    }
  } else {
    // 路由已初始化，直接放行
    console.log('[路由守卫] 路由已初始化，直接放行');
    next();
  }
});

router.afterEach(() => {
  NProgress.done();
});
