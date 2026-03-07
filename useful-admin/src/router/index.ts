import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router';
import uniq from 'lodash/uniq';

// 自动导入modules文件夹下所有ts文件
const modules = import.meta.globEager('./modules/**/*.ts');

// 路由暂存
const routeModuleList: Array<RouteRecordRaw> = [];

Object.keys(modules).forEach((key) => {
  const mod = modules[key].default || {};
  const modList = Array.isArray(mod) ? [...mod] : [mod];
  routeModuleList.push(...modList);
});
console.log("当前的routeModulesList:", routeModuleList);
// 关于单层路由，meta 中设置 { single: true } 即可为单层路由，{ hidden: true } 即可在侧边栏隐藏该路由

// 存放固定的路由并导出
export const defaultRouterList: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login/index.vue'),
  },
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/system',
    name: 'system',
    component: () => import('@/layouts/index.vue'),
    redirect: '/system/user',
    children: [
      {
        path: 'user',
        name: 'system-user',
        component: () => import('@/pages/system/user/index.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'role',
        name: 'system-role',
        component: () => import('@/pages/system/role/index.vue'),
        meta: { title: '角色管理' },
      },
      {
        path: 'menu',
        name: 'system-menu',
        component: () => import('@/pages/system/menu/index.vue'),
        meta: { title: '菜单管理' },
      },
      {
        path: 'dept',
        name: 'system-dept',
        component: () => import('@/pages/system/dept/index.vue'),
        meta: { title: '部门管理' },
      },
      {
        path: 'post',
        name: 'system-post',
        component: () => import('@/pages/system/post/index.vue'),
        meta: { title: '岗位管理' },
      },
      {
        path: 'dict',
        name: 'system-dict',
        component: () => import('@/pages/system/dict/index.vue'),
        meta: { title: '字典管理' },
      },
      {
        path: 'config',
        name: 'system-config',
        component: () => import('@/pages/system/config/index.vue'),
        meta: { title: '参数设置' },
      },
      {
        path: 'notice',
        name: 'system-notice',
        component: () => import('@/pages/system/notice/index.vue'),
        meta: { title: '通知公告' },
      },
    ],
  },
  {
    path: '/product',
    name: 'product',
    component: () => import('@/layouts/index.vue'),
    children: [
      {
        path: '',
        name: 'product-list',
        component: () => import('@/pages/product/index.vue'),
        meta: { title: '商品管理' },
      },
    ],
  },
  {
    path: '/shop',
    name: 'shop',
    component: () => import('@/layouts/index.vue'),
    children: [
      {
        path: 'category',
        name: 'shop-category',
        component: () => import('@/pages/shop/category/index.vue'),
        meta: { title: '商品分类管理' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: '404Page',
    component: () => import('@/pages/result/404/index.vue'),
  },
];

// 存放动态路由（初始为空，由后端获取）
export const asyncRouterList: Array<RouteRecordRaw> = [];

export const allRoutes = [...defaultRouterList, ...asyncRouterList];

export const getRoutesExpanded = () => {
  const expandedRoutes = [];

  allRoutes.forEach((item) => {
    if (item.meta && item.meta.expanded) {
      expandedRoutes.push(item.path);
    }
    if (item.children && item.children.length > 0) {
      item.children
        .filter((child) => child.meta && child.meta.expanded)
        .forEach((child: RouteRecordRaw) => {
          expandedRoutes.push(item.path);
          expandedRoutes.push(`${item.path}/${child.path}`);
        });
    }
  });
  return uniq(expandedRoutes);
};

export const getActive = (maxLevel = 3): string => {
  // 简化实现，避免在路由初始化时访问 router.currentRoute
  try {
    // 非组件内调用必须通过Router实例获取当前路由
    const route = router.currentRoute.value;

    if (!route.path) {
      return '';
    }
    return route.path
      .split('/')
      .filter((_item: string, index: number) => index <= maxLevel && index > 0)
      .map((item: string) => `/${item}`)
      .join('');
  } catch (error) {
    // 如果路由未初始化完成，返回空字符串
    return '';
  }
};

const router = createRouter({
  history: createWebHashHistory(),
  routes: allRoutes,
  scrollBehavior() {
    return {
      el: '#app',
      top: 0,
      behavior: 'smooth',
    };
  },
});

export default router;
