<template>
  <div class="breadcrumb-wrapper">
    <t-breadcrumb :max-item-width="'150'" class="tdesign-breadcrumb">
      <t-breadcrumbItem v-for="item in crumbs" :key="item.to" :to="item.to">
        {{ item.title }}
      </t-breadcrumbItem>
    </t-breadcrumb>
    <div style="font-size: 12px; color: #999; margin-left: 10px;">
      路径: {{ route.path }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

const crumbs = computed(() => {
  console.log('[Breadcrumb] route:', route);
  console.log('[Breadcrumb] route.matched:', route.matched);

  const pathArray = route.path.split('/');
  pathArray.shift();

  const breadcrumbs = pathArray.reduce((breadcrumbArray, path, idx) => {
    // 如果路由下有hiddenBreadcrumb或当前遍历到参数则隐藏
    if (route.matched[idx]?.meta?.hiddenBreadcrumb || Object.values(route.params).includes(path)) {
      return breadcrumbArray;
    }

    breadcrumbArray.push({
      path,
      to: breadcrumbArray[idx - 1] ? `/${breadcrumbArray[idx - 1].path}/${path}` : `/${path}`,
      title: route.matched[idx]?.meta?.title ?? path,
    });
    return breadcrumbArray;
  }, []);
  
  console.log('[Breadcrumb] breadcrumbs:', breadcrumbs);
  return breadcrumbs;
});
</script>
<style scoped>
.breadcrumb-wrapper {
  display: flex;
  align-items: center;
}
.tdesign-breadcrumb {
  margin-bottom: 0;
}
</style>
