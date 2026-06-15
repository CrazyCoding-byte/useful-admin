<template>
  <div>
    <template v-for="item in list" :key="item.path">
      <template v-if="!item.children || !item.children.length || item.meta?.single">
        <t-menu-item v-if="getHref(item)" :name="item.path" :value="getPath(item)" @click="openHref(getHref(item)[0])">
          <template #icon>
            <t-icon v-if="beIcon(item)" :name="item.icon" />
            <component :is="beRender(item).render" v-else-if="beRender(item).can" class="t-icon" />
          </template>
          {{ item.title }}
        </t-menu-item>
        <t-menu-item v-else :name="item.path" :value="getPath(item)" :to="item.path">
          <template #icon>
            <t-icon v-if="beIcon(item)" :name="item.icon" />
            <component :is="beRender(item).render" v-else-if="beRender(item).can" class="t-icon" />
          </template>
          {{ item.title }}
        </t-menu-item>
      </template>
      <t-submenu v-else :name="item.path" :value="item.path" :title="item.title">
        <template #icon>
          <t-icon v-if="beIcon(item)" :name="item.icon" />
          <component :is="beRender(item).render" v-else-if="beRender(item).can" class="t-icon" />
        </template>
        <menu-content v-if="item.children" :nav-data="item.children" :base-path="item.path" />
      </t-submenu>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { PropType } from 'vue';
import isObject from 'lodash/isObject';
import type { MenuRoute } from '@/types/interface';
import { getActive } from '@/router';

const props = defineProps({
  navData: {
    type: Array as PropType<MenuRoute[]>,
    default: () => [],
  },
  basePath: {
    type: String,
    default: '',
  },
});

const active = computed(() => getActive());
const list = computed(() => {
  const { navData, basePath } = props;
  return getMenuList(navData, basePath);
});

type ListItemType = MenuRoute & { icon?: string };

const getMenuList = (list: MenuRoute[], basePath?: string): ListItemType[] => {
  if (!list) {
    return [];
  }
  console.log('[Menu] getMenuList called, basePath:', basePath, 'list count:', list.length);
  // 如果meta中有orderNo则按照从小到大排序
  list.sort((a, b) => {
    return (a.meta?.orderNo || 0) - (b.meta?.orderNo || 0);
  });
  return list
    .map((item) => {
      let path = item.path;
      if (basePath && !item.path.includes(basePath)) {
        // 确保路径拼接时不会出现双斜杠
        path = `${basePath.replace(/\/$/, '')}/${item.path.replace(/^\//, '')}`;
      }
      console.log('[Menu] 处理菜单项:', item.meta?.title, '原始path:', item.path, 'basePath:', basePath, '最终path:', path);
      return {
        path,
        title: item.meta?.title,
        icon: item.meta?.icon || '',
        children: getMenuList(item.children, path),
        meta: item.meta,
        redirect: item.redirect,
      };
    })
    .filter((item) => item.meta && item.meta.hidden !== true);
};

const getHref = (item: MenuRoute) => {
  const { frameSrc, frameBlank } = item.meta;
  if (frameSrc && frameBlank) {
    return frameSrc.match(/(http|https):\/\/([\w.]+\/?)\S*/);
  }
  return null;
};

const getPath = (item) => {
  if (active.value.startsWith(item.path)) {
    return active.value;
  }
  return item.meta?.single ? item.redirect : item.path;
};

const beIcon = (item: MenuRoute) => {
  return item.icon && typeof item.icon === 'string';
};

const beRender = (item: MenuRoute) => {
  if (isObject(item.icon) && typeof item.icon.render === 'function') {
    return {
      can: true,
      render: item.icon.render,
    };
  }
  return {
    can: false,
    render: null,
  };
};

const openHref = (url: string) => {
  window.open(url);
};
</script>

<style lang="less" scoped></style>
