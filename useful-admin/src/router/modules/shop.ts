import Layout from '@/layouts/index.vue';

/**
 * 电商管理相关的静态路由（不在后端菜单里返回的页面）
 * 例如：规格管理（SKU）属于商品列表的内部跳转页，不需要在侧边栏显示
 */
export default [
  {
    path: '/shop/goods/sku/:productId',
    name: 'ShopGoodsSku',
    component: Layout,
    meta: { hidden: true, title: 'SKU规格管理' },
    children: [
      {
        path: '',
        name: 'ShopGoodsSkuIndex',
        component: () => import('@/pages/shop/goods/sku/index.vue'),
        meta: { hidden: true, title: 'SKU规格管理' },
      },
    ],
  },
];
