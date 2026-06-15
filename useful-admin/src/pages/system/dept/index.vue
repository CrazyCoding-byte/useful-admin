<template>
  <div class="dept-page">
    <!-- 搜索 & 操作 合并区域 -->
    <t-card class="toolbar-card" :bordered="false">
      <div class="search-form">
        <div class="search-item">
          <span class="search-label">部门名称</span>
          <t-input v-model="searchForm.deptName" placeholder="请输入部门名称" clearable size="small" />
        </div>
        <div class="search-item">
          <span class="search-label">状态</span>
          <t-select v-model="searchForm.status" placeholder="部门状态" clearable size="small">
            <t-option label="正常" value="0" />
            <t-option label="停用" value="1" />
          </t-select>
        </div>
        <t-button theme="primary" size="small" @click="getDeptList">
          <template #icon><t-icon name="search" /></template>
          搜索
        </t-button>
        <t-button variant="outline" size="small" @click="resetSearch">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
        <t-divider layout="vertical" />
        <t-button theme="primary" size="small" @click="handleAddDept">
          <template #icon><t-icon name="add" /></template>
          新增
        </t-button>
        <t-button variant="outline" size="small" @click="toggleExpandAll">
          <template #icon><t-icon name="swap" /></template>
          {{ expandAll ? '折叠' : '展开' }}
        </t-button>
      </div>
    </t-card>

    <!-- 树形表格 -->
    <t-card class="table-card" :bordered="false">
      <t-enhanced-table
        :key="tableKey"
        :data="deptList"
        :columns="columns"
        :loading="loading"
        row-key="deptId"
        :default-expand-all="expandAll"
        :tree="{ childrenKey: 'children', treeNodeColumnIndex: 0 }"
        hover
        stripe
      >
        <template #deptName="{ row }">
          <span>{{ row.deptName }}</span>
        </template>
        <template #status="{ row }">
          <t-tag :theme="row.status === '0' ? 'success' : 'danger'">
            {{ row.status === '0' ? '正常' : '停用' }}
          </t-tag>
        </template>
        <template #op="{ row }">
          <div class="operation-icons">
            <t-button theme="primary" variant="text" size="small" @click="handleAddChild(row)">
              <t-icon name="add" />
            </t-button>
            <t-button theme="warning" variant="text" size="small" @click="handleEdit(row)">
              <t-icon name="edit" />
            </t-button>
            <t-button theme="danger" variant="text" size="small" @click="handleDelete(row)">
              <t-icon name="delete" />
            </t-button>
          </div>
        </template>
      </t-enhanced-table>
    </t-card>

    <!-- 部门对话框 -->
    <t-dialog
      v-model:visible="deptDialogVisible"
      :header="deptForm.deptId ? '修改部门' : '新增部门'"
      width="600px"
    >
      <t-form
        :data="deptForm"
        :rules="deptFormRules"
        ref="deptFormRef"
        label-width="100px"
      >
        <t-form-item label="上级部门" name="parentId">
          <t-tree-select
            v-model="deptForm.parentId"
            :data="deptTreeData"
            placeholder="请选择上级部门"
            clearable
            :keys="{ label: 'deptName', value: 'deptId', children: 'children' }"
          />
        </t-form-item>
        <t-form-item label="部门名称" name="deptName">
          <t-input v-model="deptForm.deptName" placeholder="请输入部门名称"/>
        </t-form-item>
        <t-form-item label="显示排序" name="orderNum">
          <t-input-number v-model="deptForm.orderNum" :min="0" />
        </t-form-item>
        <t-form-item label="负责人" name="leader">
          <t-input v-model="deptForm.leader" placeholder="请输入负责人"/>
        </t-form-item>
        <t-form-item label="联系电话" name="phone">
          <t-input v-model="deptForm.phone" placeholder="请输入联系电话"/>
        </t-form-item>
        <t-form-item label="邮箱" name="email">
          <t-input v-model="deptForm.email" placeholder="请输入邮箱"/>
        </t-form-item>
        <t-form-item label="部门状态" name="status">
          <t-radio-group v-model="deptForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closeDeptDialog">取消</t-button>
        <t-button theme="primary" @click="submitDeptForm">确定</t-button>
      </template>
    </t-dialog>

    <!-- 确认删除对话框 -->
    <t-dialog
      v-model:visible="confirmVisible"
      header="确认删除"
    >
      <div>确定要删除部门 "{{ deleteDeptName }}" 吗？删除后无法恢复！</div>
      <template #footer>
        <t-button @click="confirmVisible = false">取消</t-button>
        <t-button theme="danger" @click="confirmDelete">确认</t-button>
      </template>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { deptApi, type SysDept } from '@/api/system/dept';
import { MessagePlugin } from 'tdesign-vue-next';
import type { TableProps } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysDept>>({
  deptName: '',
  status: '',
});

// 部门列表
const deptList = ref<SysDept[]>([]);
const deptTreeData = ref<SysDept[]>([]);
const loading = ref(false);
const expandAll = ref(true);
const tableKey = ref(0);

// 表格列配置
const columns: TableProps['columns'] = [
  {
    colKey: 'deptName',
    title: '部门名称',
    width: 200,
    ellipsis: true,
  },
  {
    colKey: 'orderNum',
    title: '排序',
    width: 80,
    align: 'center',
  },
  {
    colKey: 'status',
    title: '状态',
    width: 80,
    align: 'center',
  },
  {
    colKey: 'createTime',
    title: '创建时间',
    width: 180,
  },
  {
    colKey: 'op',
    title: '操作',
    width: 150,
    align: 'center',
    fixed: 'right',
  },
];

// 获取部门列表
const getDeptList = async () => {
  loading.value = true;
  try {
    const response = await deptApi.getDeptList(searchForm.value);
    const data = response.data || response;
    deptList.value = data || [];
    deptTreeData.value = data || [];
  } catch (error) {
    MessagePlugin.error('获取部门列表失败');
    console.error('获取部门列表失败:', error);
    deptList.value = [];
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    deptName: '',
    status: '',
  };
  getDeptList();
};

// 展开/折叠
const toggleExpandAll = () => {
  expandAll.value = !expandAll.value;
  tableKey.value++;
};

// 部门对话框
const deptDialogVisible = ref(false);
const deptFormRef = ref<any>();
const deptForm = ref<Partial<SysDept>>({
  deptId: undefined,
  parentId: 0,
  deptName: '',
  orderNum: 0,
  leader: '',
  phone: '',
  email: '',
  status: '0',
});

const deptFormRules = ref({
  deptName: [
    { required: true, message: '请输入部门名称', trigger: ['blur', 'change'] },
  ],
  orderNum: [
    { required: true, message: '请输入显示排序', trigger: ['blur', 'change'] },
  ],
});

// 新增部门
const handleAddDept = () => {
  deptForm.value = {
    deptId: undefined,
    parentId: 0,
    deptName: '',
    orderNum: 0,
    leader: '',
    phone: '',
    email: '',
    status: '0',
  };
  deptDialogVisible.value = true;
};

// 新增子部门
const handleAddChild = (row: SysDept) => {
  deptForm.value = {
    deptId: undefined,
    parentId: row.deptId,
    deptName: '',
    orderNum: 0,
    leader: '',
    phone: '',
    email: '',
    status: '0',
  };
  deptDialogVisible.value = true;
};

// 编辑部门
const handleEdit = (row: SysDept) => {
  deptForm.value = { ...row };
  deptDialogVisible.value = true;
};

// 关闭部门对话框
const closeDeptDialog = () => {
  deptDialogVisible.value = false;
};

// 提交部门表单
const submitDeptForm = async () => {
  if (!deptFormRef.value) return;
  try {
    await deptFormRef.value.validate();
    if (deptForm.value.deptId) {
      await deptApi.updateDept(deptForm.value);
      MessagePlugin.success('修改部门成功');
    } else {
      await deptApi.addDept(deptForm.value);
      MessagePlugin.success('新增部门成功');
    }
    deptDialogVisible.value = false;
    getDeptList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 删除部门
const confirmVisible = ref(false);
const deleteDeptId = ref<number>(0);
const deleteDeptName = ref('');

const handleDelete = (row: SysDept) => {
  deleteDeptId.value = row.deptId!;
  deleteDeptName.value = row.deptName!;
  confirmVisible.value = true;
};

const confirmDelete = async () => {
  try {
    await deptApi.deleteDept(deleteDeptId.value);
    MessagePlugin.success('删除成功');
    confirmVisible.value = false;
    getDeptList();
  } catch (error) {
    MessagePlugin.error('删除失败');
    console.error('删除失败:', error);
  }
};

// 页面挂载时获取部门列表
onMounted(() => {
  getDeptList();
});
</script>

<style lang="less" scoped>
.dept-page {
  padding: 8px 12px;
  background-color: var(--td-bg-color-page);

  .toolbar-card {
    margin-bottom: 10px;

    :deep(.t-card__body) {
      padding: 8px 14px;
    }

    .search-form {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      .search-item {
        display: flex;
        align-items: center;
        gap: 6px;

        .search-label {
          font-size: 13px;
          color: var(--td-text-color-secondary);
          white-space: nowrap;
        }
      }

      :deep(.t-divider) {
        margin: 0 4px;
        height: 16px;
      }
    }
  }

  .table-card {
    :deep(.t-card__body) {
      padding: 0;
    }

    :deep(.t-table) {
      font-size: 13px;
    }

    .operation-icons {
      display: flex;
      justify-content: center;
      gap: 2px;
    }
  }
}
</style>
