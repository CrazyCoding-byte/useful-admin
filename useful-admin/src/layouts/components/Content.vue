<template>
  <router-view v-if="!isRefreshing" v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <keep-alive :include="aliveViews">
        <component :is="Component" />
      </keep-alive>
    </transition>
  </router-view>
  <frame-page />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ComputedRef } from 'vue';
import { useTabsRouterStore } from '@/store';
import FramePage from '@/layouts/frame/index.vue';

// <suspense>标签属于实验性功能，请谨慎使用
// 如果存在需解决/page/1=> /page/2 刷新数据问题 请修改代码 使用activeRouteFullPath 作为key
// <suspense>
//  <component :is="Component" :key="activeRouteFullPath" />
// </suspense>

// import { useRouter } from 'vue-router';
// const activeRouteFullPath = computed(() => {
//   const router = useRouter();
//   return router.currentRoute.value.fullPath;
// });

const aliveViews = computed(() => {
  const tabsRouterStore = useTabsRouterStore();
  const { tabRouters } = tabsRouterStore;

  return tabRouters.filter((route) => route.isAlive).map((route) => route.name);
}) as ComputedRef<string[]>;

const isRefreshing = computed(() => {
  const tabsRouterStore = useTabsRouterStore();
  const { refreshing } = tabsRouterStore;
  return refreshing;
});
</script>
<style lang="less" scoped>
/*
 * 路由切换过渡。
 * 重要：本项目是 Vue 3，进入动画的"起始状态"类名必须是 .fade-enter-from。
 * 原来沿用了 Vue 2 的 .fade-enter，在 Vue 3 中不会命中，导致新页面没有淡入起始态，
 * 表现为切换时新页面"直接出来"、很生硬。这里修正为 -from 并补上轻微上浮位移。
 */
.fade-leave-active {
  transition:
    opacity 0.16s @anim-time-fn-ease-out,
    transform 0.16s @anim-time-fn-ease-out;
}

.fade-enter-active {
  transition:
    opacity 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}

.fade-enter-from {
  opacity: 0;
  transform: translate3d(0, 14px, 0);
}

.fade-leave-to {
  opacity: 0;
  transform: translate3d(0, -6px, 0);
}

/* 尊重系统"减少动态效果"设置 */
@media (prefers-reduced-motion: reduce) {
  .fade-leave-active,
  .fade-enter-active {
    transition-duration: 0.01ms;
  }

  .fade-enter-from,
  .fade-leave-to {
    opacity: 1;
    transform: none;
  }
}
</style>
