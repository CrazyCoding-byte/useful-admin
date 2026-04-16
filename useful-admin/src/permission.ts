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


  if (whiteListRouters.includes(to.path)) {
    next();
    return;
  }

  if (!token || token === 'undefined' || token === 'null') {
    next({ path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } });
    return;
  }

  // 只在路由未初始化时获取路由
  if (!routesInitialized) {
    try {
      const routes = await permissionStore.initRoutes(userStore.roles || ['admin']);
      routesInitialized = true;

      next({ path: to.fullPath, replace: true });
    } catch (error) {
      MessagePlugin.error('权限加载失败');
      next('/login');
    }
  } else {
    // 路由已初始化，直接放行
    next();
  }
});

router.afterEach(() => {
  NProgress.done();
});
