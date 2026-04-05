import { MessagePlugin } from 'tdesign-vue-next';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';

import { getPermissionStore, getUserStore } from '@/store';
import router from '@/router';

NProgress.configure({ showSpinner: false });

let routesInitializedInThisSession = false;

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

  if (!routesInitializedInThisSession) {
    routesInitializedInThisSession = true;
    try {
      console.log('[路由守卫] 开始获取路由...');
      const routes = await permissionStore.initRoutes(userStore.roles || ['admin']);
      console.log('[路由守卫] 获取路由完成，数量:', routes?.length);
      next({ ...to, replace: true });
      return;
    } catch (error) {
      console.error('[路由守卫] 获取路由失败:', error);
      routesInitializedInThisSession = false;
      MessagePlugin.error('权限加载失败');
      next('/system');
      return;
    }
  }

  next();
});

router.afterEach(() => {
  NProgress.done();
});
