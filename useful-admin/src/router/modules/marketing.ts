import Layout from '@/layouts/index.vue';
import MarketingIcon from '@/assets/assets-slide-form.svg';

export default [
  {
    path: '/marketing',
    component: Layout,
    redirect: '/marketing/activity',
    name: 'marketing',
    meta: { title: '营销管理', icon: MarketingIcon },
    children: [
      {
        path: 'activity',
        name: 'MarketingActivity',
        component: () => import('@/pages/marketing/activity/index.vue'),
        meta: { title: '活动管理' },
      },
      {
        path: 'coupon',
        name: 'MarketingCoupon',
        component: () => import('@/pages/marketing/coupon/index.vue'),
        meta: { title: '优惠券管理' },
      },
    ],
  },
];
