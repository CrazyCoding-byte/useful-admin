<template>
  <div class="role-page">
    <t-card class="role-card-container">
      <t-row justify="space-between" align="middle">
        <div class="left-operation-container">
          <t-button theme="primary" @click="handleAddRole"> 新增角色 </t-button>
          <t-button variant="base" theme="default" :disabled="!selectedRowKeys.length"> 批量删除 </t-button>
          <p v-if="!!selectedRowKeys.length" class="selected-count">已选{{ selectedRowKeys.length }}项</p>
        </div>
        <div class="search-container">
          <t-input
            v-model="searchForm.roleName"
            placeholder="角色名称"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-input
            v-model="searchForm.roleKey"
            placeholder="角色权限"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-select
            v-model="searchForm.status"
            placeholder="角色状态"
            style="width: 120px; margin-right: 10px;"
          >
            <t-option value="0" label="正常" />
            <t-option value="1" label="禁用" />
          </t-select>
          <t-button theme="primary" @click="getRoleList">查询</t-button>
          <t-button @click="resetSearch">重置</t-button>
        </div>
      </t-row>
      
      <t-table
        :data="roleList"
        :loading="loading"
        :columns="columns"
        :row-key="rowKey"
        vertical-align="top"
        :hover="true"
        :pagination="pagination"
        :selected-row-keys="selectedRowKeys"
        @page-change="handlePageChange"
        @select-change="handleSelectChange"
      >
        <template #status="{ row }">
          <t-tag v-if="row.status === '0'" theme="success" variant="light"> 正常 </t-tag>
          <t-tag v-else theme="danger" variant="light"> 禁用 </t-tag>
        </template>
        <template #dataScope="{ row }">
          <span>{{ getDataScopeText(row.dataScope) }}</span>
        </template>
        <template #op="{ row }">
          <a class="t-button-link" @click="editRole(row)">编辑</a>
          <a class="t-button-link" @click="deleteRole(row.roleId!)">删除</a>
          <a class="t-button-link" @click="authDataScope(row.roleId!)">数据权限</a>
        </template>
      </t-table>
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
import { ref, onMounted, computed } from 'vue';
import { roleApi } from '@/api/system/role';
import type { SysRole } from '@/api/model/roleModel';
import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysRole>>({
  roleName: '',
  roleKey: '',
  status: '',
});

// 角色列表
const roleList = ref<SysRole[]>([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const selectedRowKeys = ref<number[]>([]);

// 表格列配置
const columns = [
  { title: '角色ID', key: 'roleId' },
  { title: '角色名称', key: 'roleName' },
  { title: '角色权限', key: 'roleKey' },
  { title: '角色排序', key: 'roleSort' },
  { title: '数据范围', key: 'dataScope', render: 'dataScope' },
  { title: '状态', key: 'status', render: 'status' },
  { title: '备注', key: 'remark' },
  { title: '操作', key: 'op', render: 'op' },
];

// 分页配置
const pagination = computed(() => {
  return {
    pageSize: pageSize.value,
    current: currentPage.value,
    total: total.value,
    showJumper: true,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
  };
});

// 行键
const rowKey = 'roleId';

// 获取数据范围文本
const getDataScopeText = (dataScope: string | undefined) => {
  const dataScopeMap: Record<string, string> = {
    '1': '全部数据权限',
    '2': '自定义数据权限',
    '3': '本部门数据权限',
    '4': '本部门及以下数据权限',
  };
  return dataScopeMap[dataScope || ''] || dataScope;
};

// 获取角色列表
const getRoleList = async () => {
  loading.value = true;
  try {
    const response = await roleApi.getRoleList({
      ...searchForm.value,
    });
    roleList.value = response.rows || [];
    total.value = response.total || 0;
  } catch (error) {
    MessagePlugin.error('获取角色列表失败');
    console.error('获取角色列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    roleName: '',
    roleKey: '',
    status: '',
  };
  getRoleList();
};

// 处理分页变化
const handlePageChange = (current: number, pageInfo: any) => {
  currentPage.value = current;
  pageSize.value = pageInfo.pageSize;
  getRoleList();
};

// 处理选择变化
const handleSelectChange = (val: number[]) => {
  selectedRowKeys.value = val;
};

// 新增角色
const handleAddRole = () => {
  console.log('新增角色');
  // 这里可以打开新增角色对话框
};

// 编辑角色
const editRole = (role: SysRole) => {
  console.log('编辑角色:', role);
  // 这里可以打开编辑对话框
};

// 删除角色
const deleteRole = (roleId: number) => {
  deleteIdx.value = roleId;
  confirmVisible.value = true;
};

// 数据权限设置
const authDataScope = (roleId: number) => {
  console.log('数据权限设置:', roleId);
  // 这里可以打开数据权限设置对话框
};

// 确认对话框
const confirmVisible = ref(false);
const deleteIdx = ref<number>(-1);
const confirmBody = computed(() => {
  return '删除后，角色的所有信息将被清空，且无法恢复';
});

// 确认删除
const onConfirmDelete = async () => {
  try {
    await roleApi.deleteRole([deleteIdx.value]);
    MessagePlugin.success('删除成功');
    getRoleList();
    selectedRowKeys.value = selectedRowKeys.value.filter(key => key !== deleteIdx.value);
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

// 页面挂载时获取角色列表
onMounted(() => {
  getRoleList();
});
</script>

<style lang="less" scoped>
.role-card-container {
  margin: 20px;
}

.left-operation-container {
  padding: 6px 0;
  margin-bottom: 16px;

  .selected-count {
    display: inline-block;
    margin-left: 8px;
    color: var(--td-text-color-secondary);
  }
}

.search-container {
  display: flex;
  align-items: center;
}
</style>