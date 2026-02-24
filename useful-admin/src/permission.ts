import { MessagePlugin } from 'tdesign-vue-next';
import NProgress from 'nprogress'; // progress bar
import 'nprogress/nprogress.css'; // progress bar style

import { getPermissionStore, getUserStore } from '@/store';
import router from '@/router';

NProgress.configure({ showSpinner: false });

router.beforeEach(async (to, from, next) => {
  NProgress.start();

  const userStore = getUserStore();
  const permissionStore = getPermissionStore();
  const { whiteListRouters } = permissionStore;

  const { token } = userStore;
  console.log("当前的token:", token);
  
  // 白名单路由直接放行
  if (whiteListRouters.indexOf(to.path) !== -1) {
    next();
    NProgress.done();
    return;
  }
  
  // 有token的情况
  if (token) {
    // 登录页重定向到仪表盘
    if (to.path === '/login') {
      next('/dashboard/base');
      NProgress.done();
      return;
    }
    
    // 检查是否有角色信息
    const { roles } = userStore;
    if (roles && roles.length > 0) {
      // 有角色信息，直接放行
      next();
      NProgress.done();
      return;
    } else {
      // 没有角色信息，尝试获取
      try {
        await userStore.getUserInfo();
        // 获取成功后放行
        next();
      } catch (error) {
        // 获取失败，跳转到登录页
        console.error('获取用户信息失败:', error);
        next({
          path: '/login',
          query: { redirect: encodeURIComponent(to.fullPath) },
        });
      } finally {
        NProgress.done();
      }
    }
  } else {
    // 没有token，跳转到登录页
    next({
      path: '/login',
      query: { redirect: encodeURIComponent(to.fullPath) },
    });
    NProgress.done();
  }
});

router.afterEach((to) => {
  if (to.path === '/login') {
    const userStore = getUserStore();
    const permissionStore = getPermissionStore();

    userStore.logout();
    permissionStore.restore();
  }
  NProgress.done();
});
