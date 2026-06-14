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

  console.log('[Router] 路由守卫:', { to: to.path, from: from?.path, token: token?.substring(0, 20), routesInitialized });

  if (whiteListRouters.includes(to.path)) {
    console.log('[Router] 白名单路由,直接放行');
    next();
    return;
  }

  if (!token || token === 'undefined' || token === 'null') {
    console.log('[Router] 无token,跳转登录页');
    next({ path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } });
    return;
  }

  // 只在路由未初始化时获取路由
  if (!routesInitialized) {
    console.log('[Router] 路由未初始化,开始初始化...');
    try {
      await permissionStore.initRoutes(userStore.roles || ['admin']);
      routesInitialized = true;
      console.log('[Router] 路由初始化完成,重新导航到:', to.path);
      // addRoute 后必须重新指定路径触发路由重新匹配，否则 Vue Router 不会识别新注册的路由
      // 第二次进入 beforeEach 时 routesInitialized 已为 true，直接放行
      // 使用 path + query 的方式重新导航，确保路由能正确匹配
      next({ path: to.path, query: to.query, replace: true });
    } catch (error) {
      console.error('[Router] 路由初始化失败:', error);
      MessagePlugin.error('权限加载失败');
      next('/login');
    }
  } else {
    // 路由已初始化，直接放行
    console.log('[Router] 路由已初始化,直接放行');
    next();
  }
});

router.afterEach(() => {
  NProgress.done();
});
