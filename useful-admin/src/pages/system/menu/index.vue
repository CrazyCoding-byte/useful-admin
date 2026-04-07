<template>
  <div class="menu-page">
    <t-card class="menu-card-container">
      <t-row justify="space-between" align="middle">
        <div class="left-operation-container">
          <t-button theme="primary" @click="handleAddMenu"> 新增菜单 </t-button>
        </div>
        <div class="search-container">
          <t-input
            v-model="searchForm.menuName"
            placeholder="菜单名称"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-select
            v-model="searchForm.menuType"
            placeholder="菜单类型"
            style="width: 120px; margin-right: 10px;"
          >
            <t-option value="M" label="目录" />
            <t-option value="C" label="菜单" />
            <t-option value="F" label="按钮" />
          </t-select>
          <t-button theme="primary" @click="getMenuList">查询</t-button>
          <t-button @click="resetSearch">重置</t-button>
        </div>
      </t-row>
      
      <t-tree
        :data="menuList"
        :loading="loading"
        :expanded-keys="expandedKeys"
        @expand="handleExpand"
        @click="handleMenuClick"
        class="menu-tree"
      >
        <template #default="{ node, model }">
          <div class="menu-item">
            <span>{{ model.menuName }}</span>
            <div class="menu-actions">
              <a class="t-button-link" @click.stop="editMenu(model)">编辑</a>
              <a class="t-button-link" @click.stop="deleteMenu(model.menuId!)">删除</a>
            </div>
          </div>
        </template>
      </t-tree>
    </t-card>

    <t-dialog
      v-model:visible="confirmVisible"
      header="确认删除"
    >
      <div>{{ confirmBody }}</div>
      <template #footer>
        <t-button @click="onCancel">取消</t-button>
        <t-button theme="primary" @click="onConfirmDelete">确认</t-button>
      </template>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { menuApi } from '@/api/system/menu';
import type { SysMenu } from '@/api/model/menuModel';
import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysMenu>>({
  menuName: '',
  menuType: '',
});

// 菜单列表
const menuList = ref<SysMenu[]>([]);
const loading = ref(false);
const expandedKeys = ref<string[]>([]);

// 确认对话框
const confirmVisible = ref(false);
const deleteIdx = ref<number>(-1);
const confirmBody = ref('删除后，菜单的所有信息将被清空，且无法恢复');

// 获取菜单列表
const getMenuList = async () => {
  loading.value = true;
  try {
    const response = await menuApi.getMenuList({
      ...searchForm.value,
    });
    menuList.value = response || [];
    // 默认展开所有节点
    expandAllNodes(menuList.value);
  } catch (error) {
    MessagePlugin.error('获取菜单列表失败');
    console.error('获取菜单列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 递归展开所有节点
const expandAllNodes = (nodes: SysMenu[]) => {
  nodes.forEach(node => {
    if (node.menuId) {
      expandedKeys.value.push(node.menuId.toString());
    }
    if (node.children && node.children.length > 0) {
      expandAllNodes(node.children);
    }
  });
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    menuName: '',
    menuType: '',
  };
  getMenuList();
};

// 处理节点展开
const handleExpand = (expandedKeys: string[]) => {
  expandedKeys.value = expandedKeys;
};

// 处理菜单点击
const handleMenuClick = (node: any, model: SysMenu) => {
  console.log('点击菜单:', model);
};

// 新增菜单
const handleAddMenu = () => {
  console.log('新增菜单');
  // 这里可以打开新增菜单对话框
};

// 编辑菜单
const editMenu = (menu: SysMenu) => {
  console.log('编辑菜单:', menu);
  // 这里可以打开编辑对话框
};

// 删除菜单
const deleteMenu = (menuId: number) => {
  deleteIdx.value = menuId;
  confirmVisible.value = true;
};

// 确认删除
const onConfirmDelete = async () => {
  try {
    await menuApi.deleteMenu(deleteIdx.value);
    MessagePlugin.success('删除成功');
    getMenuList();
  } catch (error) {
    MessagePlugin.error('删除失败');
    console.error('删除失败:', error);
  } finally {
    confirmVisible.value = false;
    deleteIdx.value = -1;
  }
};

// 取消删除
const onCancel = () => {
  deleteIdx.value = -1;
};

// 页面挂载时获取菜单列表
onMounted(() => {
  getMenuList();
});
</script>

<style lang="less" scoped>
.menu-card-container {
  margin: 20px;
}

.left-operation-container {
  padding: 6px 0;
  margin-bottom: 16px;
}

.search-container {
  display: flex;
  align-items: center;
}

.menu-tree {
  margin-top: 16px;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 4px 0;
}

.menu-actions {
  display: none;
  gap: 8px;
}

.menu-item:hover .menu-actions {
  display: flex;
}
</style>