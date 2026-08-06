<template>
  <div class="tenant-package-container">
    <!-- 搜索表单 -->
    <t-card class="search-card" :bordered="false">
      <t-form :data="searchForm" label-width="80px" @submit="handleSearch" @reset="handleReset">
        <t-row :gutter="[16, 16]">
          <t-col :span="3">
            <t-form-item label="套餐名称" name="packageName">
              <t-input v-model="searchForm.packageName" placeholder="请输入套餐名称" clearable />
            </t-form-item>
          </t-col>
          <t-col :span="3">
            <t-form-item label="状态" name="status">
              <t-select v-model="searchForm.status" placeholder="请选择状态" clearable>
                <t-option value="0" label="正常" />
                <t-option value="1" label="停用" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="3" class="search-btns">
            <t-button theme="primary" type="submit">查询</t-button>
            <t-button type="reset" variant="base" theme="default">重置</t-button>
          </t-col>
        </t-row>
      </t-form>
    </t-card>

    <!-- 表格 -->
    <t-card class="table-card" :bordered="false">
      <div class="table-toolbar">
        <t-button theme="primary" @click="handleAdd">
          <template #icon><add-icon /></template>
          新增套餐
        </t-button>
      </div>

      <t-table
        :data="packageList"
        :columns="columns"
        :loading="loading"
        row-key="packageId"
        :pagination="pagination"
        hover
        stripe
        @page-change="onPageChange"
      >
        <template #status="{ row }">
          <t-tag :theme="row.status === '0' ? 'success' : 'danger'" variant="light">
            {{ row.status === '0' ? '正常' : '停用' }}
          </t-tag>
        </template>
        <template #menuIds="{ row }">
          <t-tooltip :content="row.menuIds">
            <span class="menu-ids-text">{{ row.menuIds || '-' }}</span>
          </t-tooltip>
        </template>
        <template #operation="{ row }">
          <t-space>
            <t-link theme="primary" hover="color" @click="handleEdit(row)">编辑</t-link>
            <t-link theme="danger" hover="color" @click="handleDelete(row)">删除</t-link>
          </t-space>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/编辑弹窗 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="600px"
      :confirm-btn="{ content: '确定', loading: submitLoading }"
      @confirm="handleSubmit"
      @close="handleDialogClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="100px">
        <t-form-item label="套餐名称" name="packageName">
          <t-input v-model="formData.packageName" placeholder="请输入套餐名称" />
        </t-form-item>
        <t-form-item label="关联菜单" name="menuIds">
          <t-tree-select
            v-model="formData.menuIds"
            :data="menuTreeData"
            :tree-props="{
              keys: { value: 'id', label: 'label', children: 'children' },
              checkStrictly: false,
              expandedKeys: expandedKeys,
            }"
            :popup-props="{ overlayInnerStyle: { maxHeight: '300px', overflowY: 'auto' } }"
            :min-collapsed-num="3"
            multiple
            clearable
            placeholder="请选择关联菜单"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-radio-group v-model="formData.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="备注" name="remark">
          <t-textarea v-model="formData.remark" placeholder="请输入备注" :rows="3" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 删除确认 -->
    <t-dialog
      v-model:visible="deleteVisible"
      header="确认删除"
      :body="deleteBody"
      @confirm="handleDeleteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { MessagePlugin, type FormInstanceFunctions, type PaginationProps, type PageInfo } from 'tdesign-vue-next';
import { AddIcon } from 'tdesign-icons-vue-next';
import { tenantPackageApi } from '@/api/system/tenant';
import { request } from '@/utils/request';

// ==================== 表格 ====================
const columns = [
  { colKey: 'packageId', title: '套餐ID', width: 80 },
  { colKey: 'packageName', title: '套餐名称', width: 160, ellipsis: true },
  { colKey: 'menuIds', title: '关联菜单ID', width: 200, ellipsis: true },
  { colKey: 'status', title: '状态', width: 80 },
  { colKey: 'remark', title: '备注', width: 200, ellipsis: true },
  { colKey: 'createTime', title: '创建时间', width: 170 },
  { colKey: 'operation', title: '操作', width: 130, fixed: 'right' },
];

const packageList = ref([]);
const loading = ref(false);
const pagination = reactive<PaginationProps>({
  current: 1,
  pageSize: 10,
  total: 0,
  showJumper: true,
  pageSizeOptions: [10, 20, 50],
});

// ==================== 搜索 ====================
const searchForm = reactive({
  packageName: '',
  status: '',
});

// ==================== 弹窗 ====================
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const dialogTitle = computed(() => (isEdit.value ? '编辑套餐' : '新增套餐'));

// ==================== 表单 ====================
const formData = reactive({
  packageId: undefined as number | undefined,
  packageName: '',
  menuIds: [] as any[],
  status: '0',
  remark: '',
});

const formRules = {
  packageName: [{ required: true, message: '请输入套餐名称', type: 'error' }],
};

// 菜单树数据
const menuTreeData = ref<any[]>([]);
const expandedKeys = ref<any[]>([]);

// 递归收集所有节点 id
const collectTreeIds = (list: any[]): any[] => {
  const ids: any[] = [];
  list.forEach((item) => {
    ids.push(item.id);
    if (item.children && item.children.length > 0) {
      ids.push(...collectTreeIds(item.children));
    }
  });
  return ids;
};

// ==================== 删除 ====================
const deleteVisible = ref(false);
const deleteTarget = ref<Record<string, any>>({});
const deleteBody = computed(() => {
  return `确定要删除套餐「${deleteTarget.value.packageName}」吗？删除后不可恢复！`;
});

// ==================== 辅助 ====================
const resetFormData = () => {
  formData.packageId = undefined;
  formData.packageName = '';
  formData.menuIds = [];
  formData.status = '0';
  formData.remark = '';
  formRef.value?.clearValidate();
};

// ==================== 数据加载 ====================
const loadMenuTree = async () => {
  try {
    const res = await request.get({ url: '/system/menu/list' });
    const data = Array.isArray(res) ? res : (res?.data || []);
    // 后端已返回树形结构，递归转换字段名即可
    const transformTree = (list: any[]): any[] => {
      return list.map((item: any) => ({
        id: item.menuId,
        label: item.menuName,
        children: item.children && item.children.length > 0 ? transformTree(item.children) : [],
      }));
    };
    menuTreeData.value = transformTree(data);
    expandedKeys.value = collectTreeIds(menuTreeData.value);
  } catch (error) {
    console.error('加载菜单树失败:', error);
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const params = {
      ...searchForm,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    };
    const res = await tenantPackageApi.getList(params);
    if (res?.list && Array.isArray(res.list)) {
      packageList.value = res.list;
      pagination.total = res.total ?? res.list.length;
    } else if (Array.isArray(res)) {
      packageList.value = res;
      pagination.total = res.length;
    } else if (res?.data && Array.isArray(res.data)) {
      packageList.value = res.data;
      pagination.total = res.total ?? res.data.length;
    } else {
      packageList.value = [];
      pagination.total = 0;
    }
  } catch (error) {
    console.error('获取套餐列表失败:', error);
    MessagePlugin.error('获取套餐列表失败');
  } finally {
    loading.value = false;
  }
};

// ==================== 事件处理 ====================
const handleSearch = () => {
  pagination.current = 1;
  fetchData();
};

const handleReset = () => {
  searchForm.packageName = '';
  searchForm.status = '';
  pagination.current = 1;
  fetchData();
};

const onPageChange = (pageInfo: PageInfo) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

const handleAdd = async () => {
  isEdit.value = false;
  resetFormData();
  dialogVisible.value = true;
  await loadMenuTree();
};

const handleEdit = async (row: Record<string, any>) => {
  isEdit.value = true;
  resetFormData();
  formData.packageId = row.packageId;
  formData.packageName = row.packageName || '';
  formData.status = row.status || '0';
  formData.remark = row.remark || '';
  // 解析菜单ID字符串为数组
  if (row.menuIds) {
    formData.menuIds = row.menuIds.split(',').filter(Boolean).map(Number);
  } else {
    formData.menuIds = [];
  }
  dialogVisible.value = true;
  // 打开弹窗后重新加载菜单树
  await loadMenuTree();
};

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row;
  deleteVisible.value = true;
};

const handleDeleteConfirm = async () => {
  try {
    const res = await tenantPackageApi.delete(deleteTarget.value.packageId);
    if (res.code === 200) {
      MessagePlugin.success('删除成功');
      deleteVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '删除失败');
    }
  } catch (error) {
    console.error('删除套餐失败:', error);
    MessagePlugin.error('删除失败');
  }
};

const handleSubmit = async () => {
  const validateResult = await formRef.value?.validate();
  if (validateResult !== true) return;

  submitLoading.value = true;
  try {
    // 将菜单ID数组转为逗号分隔字符串
    const submitData = {
      ...formData,
      menuIds: Array.isArray(formData.menuIds) ? formData.menuIds.join(',') : formData.menuIds,
    };

    const api = isEdit.value ? tenantPackageApi.update : tenantPackageApi.add;
    const res = await api(submitData);
    if (res.code === 200) {
      MessagePlugin.success(isEdit.value ? '修改成功' : '创建成功');
      dialogVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '操作失败');
    }
  } catch (error) {
    console.error('保存套餐失败:', error);
    MessagePlugin.error('操作失败');
  } finally {
    submitLoading.value = false;
  }
};

const handleDialogClose = () => {
  resetFormData();
};

// ==================== 初始化 ====================
onMounted(() => {
  loadMenuTree();
  fetchData();
});
</script>

<style scoped lang="less">
.tenant-package-container {
  padding: 16px;

  .search-card {
    margin-bottom: 16px;

    .search-btns {
      display: flex;
      align-items: flex-end;
      gap: 8px;
      padding-bottom: 8px;
    }
  }

  .table-toolbar {
    margin-bottom: 16px;
  }

  .menu-ids-text {
    display: inline-block;
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
