<template>
  <div class="role-page">
    <!-- 搜索区域 -->
    <t-card class="search-card" :bordered="false">
      <div class="search-form">
        <div class="search-item">
          <span class="search-label">角色名称</span>
          <t-input
            v-model="searchForm.roleName"
            placeholder="请输入角色名称"
            clearable
            class="search-input"
          />
        </div>
        <div class="search-item">
          <span class="search-label">权限字符</span>
          <t-input
            v-model="searchForm.roleKey"
            placeholder="请输入权限字符"
            clearable
            class="search-input"
          />
        </div>
        <div class="search-item">
          <span class="search-label">状态</span>
          <t-select
            v-model="searchForm.status"
            placeholder="角色状态"
            clearable
            class="search-input"
          >
            <t-option label="正常" value="0" />
            <t-option label="禁用" value="1" />
          </t-select>
        </div>
        <div class="search-item">
          <span class="search-label">创建时间</span>
          <t-date-range-picker
            v-model="searchForm.createTime"
            placeholder="开始日期 - 结束日期"
            class="search-input"
          />
        </div>
        <div class="search-buttons">
          <t-button theme="primary" @click="getRoleList">
            <template #icon><t-icon name="search" /></template>
            搜索
          </t-button>
          <t-button variant="outline" @click="resetSearch">
            <template #icon><t-icon name="refresh" /></template>
            重置
          </t-button>
        </div>
      </div>
    </t-card>

    <!-- 操作按钮区域 -->
    <t-card class="operation-card" :bordered="false">
      <div class="operation-bar">
        <div class="left-operations">
          <t-button theme="primary" @click="handleAddRole">
            <template #icon><t-icon name="add" /></template>
            新增
          </t-button>
          <t-button theme="warning" :disabled="!selectedRowKeys.length" @click="handleBatchEdit">
            <template #icon><t-icon name="edit" /></template>
            修改
          </t-button>
          <t-button theme="danger" :disabled="!selectedRowKeys.length" @click="handleBatchDelete">
            <template #icon><t-icon name="delete" /></template>
            删除
          </t-button>
          <t-button variant="outline">
            <template #icon><t-icon name="download" /></template>
            导出
          </t-button>
        </div>
        <div class="right-operations">
          <t-button variant="text" shape="circle" @click="getRoleList">
            <t-icon name="search" />
          </t-button>
          <t-button variant="text" shape="circle" @click="getRoleList">
            <t-icon name="refresh" />
          </t-button>
        </div>
      </div>
    </t-card>

    <!-- 表格区域 -->
    <t-card class="table-card" :bordered="false">
      <t-table
        :data="roleList"
        :loading="loading"
        :columns="columns"
        :row-key="rowKey"
        bordered
        stripe
        :pagination="pagination"
        :selected-row-keys="selectedRowKeys"
        @page-change="handlePageChange"
        @select-change="handleSelectChange"
      >
        <template #status="{ row }">
          <t-switch
            v-model="row.status"
            :custom-value="['0', '1']"
            size="small"
          />
        </template>
        <template #op="{ row }">
          <div class="operation-icons">
            <t-button variant="text" shape="square" @click="editRole(row)">
              <t-icon name="edit" />
            </t-button>
            <t-button variant="text" shape="square" @click="deleteRole(row.roleId!)">
              <t-icon name="delete" />
            </t-button>
            <t-button variant="text" shape="square" @click="authDataScope(row)">
              <t-icon name="check-circle" />
            </t-button>
            <t-button variant="text" shape="square" @click="assignUsers(row)">
              <t-icon name="user" />
            </t-button>
          </div>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/修改角色对话框 -->
    <t-dialog
      v-model:visible="roleDialogVisible"
      :header="roleForm.roleId ? '修改角色' : '新增角色'"
      width="700px"
    >
      <t-form
        :data="roleForm"
        :rules="roleFormRules"
        ref="roleFormRef"
        label-width="100px"
      >
        <t-form-item label="角色名称" name="roleName">
          <t-input v-model="roleForm.roleName" placeholder="请输入角色名称"/>
        </t-form-item>
        <t-form-item label="权限字符" name="roleKey">
          <t-input v-model="roleForm.roleKey" placeholder="请输入权限字符">
            <template #suffix-icon>
              <t-icon name="help-circle" />
            </template>
          </t-input>
        </t-form-item>
        <t-form-item label="角色顺序" name="roleSort">
          <t-input-number v-model="roleForm.roleSort" :min="0" />
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-radio-group v-model="roleForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="菜单权限" class="menu-permission-item">
          <div class="menu-permission-container">
            <div class="menu-permission-header">
              <t-checkbox v-model="expandAll">展开/折叠</t-checkbox>
              <t-checkbox v-model="selectAll">全选/全不选</t-checkbox>
            </div>
            <div class="menu-tree-wrapper">
              <t-tree
                ref="menuTreeRef"
                v-model="roleForm.menuIds"
                :data="menuTreeData"
                checkable
                :expand-all="expandAll"
                :keys="{ label: 'label', value: 'id', children: 'children' }"
              />
            </div>
          </div>
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closeRoleDialog">取消</t-button>
        <t-button theme="primary" @click="submitRoleForm">确定</t-button>
      </template>
    </t-dialog>

    <!-- 分配数据权限对话框 -->
    <t-dialog
      v-model:visible="dataScopeDialogVisible"
      header="分配数据权限"
      width="500px"
    >
      <t-form label-width="100px">
        <t-form-item label="角色名称">
          <t-input v-model="currentRole.roleName" disabled />
        </t-form-item>
        <t-form-item label="权限字符">
          <t-input v-model="currentRole.roleKey" disabled />
        </t-form-item>
        <t-form-item label="权限范围">
          <t-select v-model="currentRole.dataScope" placeholder="请选择权限范围">
            <t-option label="全部数据权限" value="1" />
            <t-option label="自定义数据权限" value="2" />
            <t-option label="本部门数据权限" value="3" />
            <t-option label="本部门及以下数据权限" value="4" />
            <t-option label="仅本人数据权限" value="5" />
          </t-select>
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="dataScopeDialogVisible = false">取消</t-button>
        <t-button theme="primary" @click="submitDataScope">确定</t-button>
      </template>
    </t-dialog>

    <!-- 分配用户对话框 -->
    <t-dialog
      v-model:visible="assignUserDialogVisible"
      header="分配用户"
      width="800px"
    >
      <div class="assign-user-container">
        <!-- 搜索区域 -->
        <div class="assign-user-search">
          <div class="search-item">
            <span class="search-label">用户名称</span>
            <t-input
              v-model="userSearchForm.userName"
              placeholder="请输入用户名称"
              clearable
            />
          </div>
          <div class="search-item">
            <span class="search-label">手机号码</span>
            <t-input
              v-model="userSearchForm.phonenumber"
              placeholder="请输入手机号码"
              clearable
            />
          </div>
          <t-button theme="primary" @click="searchUsers">
            <template #icon><t-icon name="search" /></template>
            搜索
          </t-button>
          <t-button variant="outline" @click="resetUserSearch">
            <template #icon><t-icon name="refresh" /></template>
            重置
          </t-button>
        </div>

        <!-- 操作按钮 -->
        <div class="assign-user-operations">
          <t-button theme="primary" @click="handleAddUsers">
            <template #icon><t-icon name="add" /></template>
            添加用户
          </t-button>
          <t-button theme="danger" :disabled="!selectedUserKeys.length" @click="handleBatchCancelAuth">
            <template #icon><t-icon name="close-circle" /></template>
            批量取消授权
          </t-button>
          <t-button variant="outline" @click="assignUserDialogVisible = false">
            <template #icon><t-icon name="close" /></template>
            关闭
          </t-button>
        </div>

        <!-- 用户列表 -->
        <t-table
          :data="userList"
          :columns="userColumns"
          :row-key="'userId'"
          bordered
          stripe
          :pagination="userPagination"
          :selected-row-keys="selectedUserKeys"
          @select-change="handleUserSelectChange"
        >
          <template #status="{ row }">
            <t-tag v-if="row.status === '0'" theme="success" variant="light">正常</t-tag>
            <t-tag v-else theme="danger" variant="light">禁用</t-tag>
          </template>
          <template #op="{ row }">
            <t-button variant="text" shape="square" @click="cancelAuthUser(row)">
              <t-icon name="close-circle" />
            </t-button>
          </template>
        </t-table>
      </div>
    </t-dialog>

    <!-- 确认删除对话框 -->
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
import { ref, onMounted, computed, watch } from 'vue';
import { roleApi } from '@/api/system/role';
import type { SysRole } from '@/api/model/roleModel';
import { MessagePlugin } from 'tdesign-vue-next';
import type { PaginationProps, TableProps } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysRole> & { createTime?: any }>({
  roleName: '',
  roleKey: '',
  status: '',
  createTime: [],
});

// 角色列表
const roleList = ref<SysRole[]>([]);
const loading = ref(false);
const selectedRowKeys = ref<number[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 表格列配置
const columns: TableProps['columns'] = [
  {
    colKey: 'row-select',
    type: 'multiple',
    width: 46,
  },
  {
    colKey: 'roleName',
    title: '角色名称',
    width: 150,
  },
  {
    colKey: 'roleKey',
    title: '权限字符',
    width: 150,
  },
  {
    colKey: 'roleSort',
    title: '显示顺序',
    width: 100,
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
    width: 160,
    align: 'center',
    fixed: 'right',
  },
];

// 分页配置
const pagination = computed(() => ({
  pageSize: pageSize.value,
  current: currentPage.value,
  total: total.value,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
}));

// 行键
const rowKey: TableProps['rowKey'] = 'roleId';

// 获取角色列表
const getRoleList = async () => {
  loading.value = true;
  try {
    const response = await roleApi.getRoleList({
      ...searchForm.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    });
    // 经过请求拦截器处理，response 已经是 data 字段的内容
    // 角色列表接口返回的是数组，不是分页对象
    if (Array.isArray(response)) {
      roleList.value = response;
      total.value = response.length;
    } else {
      roleList.value = [];
      total.value = 0;
    }
  } catch (error) {
    MessagePlugin.error('获取角色列表失败');
    console.error('获取角色列表失败:', error);
    roleList.value = [];
    total.value = 0;
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
    createTime: [],
  };
  getRoleList();
};

// 处理分页变化
const handlePageChange = (pageInfo: PaginationProps) => {
  currentPage.value = pageInfo.current || 1;
  pageSize.value = pageInfo.pageSize || 10;
  getRoleList();
};

// 处理选择变化
const handleSelectChange = (value: number[]) => {
  selectedRowKeys.value = value;
};

// 角色对话框
const roleDialogVisible = ref(false);
const roleFormRef = ref<any>();
const roleForm = ref({
  roleId: null as number | null,
  roleName: '',
  roleKey: '',
  roleSort: 0,
  status: '0',
  dataScope: '1',
  menuIds: [] as number[],
});

const roleFormRules = ref({
  roleName: [
    { required: true, message: '请输入角色名称', trigger: ['blur', 'change'] },
  ],
  roleKey: [
    { required: true, message: '请输入权限字符', trigger: ['blur', 'change'] },
  ],
  roleSort: [
    { required: true, message: '请输入角色顺序', trigger: ['blur', 'change'] },
  ],
});

// 菜单树数据
const expandAll = ref(true);
const selectAll = ref(false);
const menuTreeRef = ref<any>(null);
const menuTreeData = ref([
  {
    id: 1,
    label: '系统管理',
    children: [
      { id: 11, label: '用户管理' },
      { id: 12, label: '角色管理' },
      { id: 13, label: '菜单管理' },
      { id: 14, label: '部门管理' },
    ],
  },
  {
    id: 2,
    label: '系统监控',
    children: [
      { id: 21, label: '在线用户' },
      { id: 22, label: '定时任务' },
      { id: 23, label: '数据监控' },
      { id: 24, label: '服务监控' },
    ],
  },
]);

// 新增角色
const handleAddRole = () => {
  roleForm.value = {
    roleId: null,
    roleName: '',
    roleKey: '',
    roleSort: 0,
    status: '0',
    dataScope: '1',
    menuIds: [],
  };
  roleDialogVisible.value = true;
};

// 编辑角色
const editRole = (role: SysRole) => {
  roleForm.value = {
    roleId: role.roleId || null,
    roleName: role.roleName || '',
    roleKey: role.roleKey || '',
    roleSort: role.roleSort || 0,
    status: role.status || '0',
    dataScope: role.dataScope || '1',
    menuIds: [],
  };
  roleDialogVisible.value = true;
};

// 批量修改
const handleBatchEdit = () => {
  if (selectedRowKeys.value.length === 1) {
    const role = roleList.value.find(r => r.roleId === selectedRowKeys.value[0]);
    if (role) editRole(role);
  } else {
    MessagePlugin.warning('请选择一条记录进行修改');
  }
};

// 关闭角色对话框
const closeRoleDialog = () => {
  roleDialogVisible.value = false;
};

// 监听全选变化
watch(selectAll, (val) => {
  if (val) {
    roleForm.value.menuIds = getAllNodeIds(menuTreeData.value);
  } else {
    roleForm.value.menuIds = [];
  }
});

// 递归获取所有节点ID
const getAllNodeIds = (nodes: any[]): number[] => {
  const ids: number[] = [];
  nodes.forEach(node => {
    ids.push(node.id);
    if (node.children && node.children.length > 0) {
      ids.push(...getAllNodeIds(node.children));
    }
  });
  return ids;
};

// 提交角色表单
const submitRoleForm = async () => {
  if (!roleFormRef.value) return;
  try {
    await roleFormRef.value.validate();
    await roleApi.saveRole(roleForm.value);
    MessagePlugin.success(roleForm.value.roleId ? '修改角色成功' : '新增角色成功');
    roleDialogVisible.value = false;
    getRoleList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 删除角色
const deleteRole = (roleId: number) => {
  deleteIdx.value = roleId;
  confirmVisible.value = true;
};

// 批量删除
const handleBatchDelete = () => {
  if (selectedRowKeys.value.length > 0) {
    confirmVisible.value = true;
  }
};

// 数据权限对话框
const dataScopeDialogVisible = ref(false);
const currentRole = ref<Partial<SysRole>>({});

// 数据权限设置
const authDataScope = (role: SysRole) => {
  currentRole.value = { ...role };
  dataScopeDialogVisible.value = true;
};

// 提交数据权限
const submitDataScope = async () => {
  try {
    await roleApi.dataScope(currentRole.value);
    MessagePlugin.success('分配数据权限成功');
    dataScopeDialogVisible.value = false;
    getRoleList();
  } catch (error) {
    MessagePlugin.error('分配数据权限失败');
    console.error('分配数据权限失败:', error);
  }
};

// 分配用户对话框
const assignUserDialogVisible = ref(false);
const userSearchForm = ref({
  userName: '',
  phonenumber: '',
});
const userList = ref<any[]>([]);
const selectedUserKeys = ref<number[]>([]);
const userPagination = ref({
  pageSize: 10,
  current: 1,
  total: 0,
});

const userColumns: TableProps['columns'] = [
  { colKey: 'row-select', type: 'multiple', width: 46 },
  { colKey: 'userName', title: '用户名称', width: 120 },
  { colKey: 'nickName', title: '用户昵称', width: 120 },
  { colKey: 'email', title: '邮箱', width: 180 },
  { colKey: 'phonenumber', title: '手机', width: 140 },
  { colKey: 'status', title: '状态', width: 80 },
  { colKey: 'createTime', title: '创建时间', width: 180 },
  {
    colKey: 'op',
    title: '操作',
    width: 80,
    align: 'center',
  },
];

// 分配用户
const assignUsers = (role: SysRole) => {
  currentRole.value = role;
  assignUserDialogVisible.value = true;
  searchUsers();
};

// 搜索用户
const searchUsers = async () => {
  if (!currentRole.value.roleId) return;
  try {
    const response = await roleApi.getAllocatedList({
      roleId: currentRole.value.roleId,
      userName: userSearchForm.value.userName,
      phonenumber: userSearchForm.value.phonenumber,
      pageNum: userPagination.value.current,
      pageSize: userPagination.value.pageSize,
    });
    const data = response.data || response;
    userList.value = data.records || [];
    userPagination.value.total = data.total || 0;
  } catch (error) {
    MessagePlugin.error('获取用户列表失败');
    console.error('获取用户列表失败:', error);
    userList.value = [];
    userPagination.value.total = 0;
  }
};

// 重置用户搜索
const resetUserSearch = () => {
  userSearchForm.value = {
    userName: '',
    phonenumber: '',
  };
  searchUsers();
};

// 处理用户选择变化
const handleUserSelectChange = (value: number[]) => {
  selectedUserKeys.value = value;
};

// 添加用户
const handleAddUsers = () => {
  // 打开选择用户对话框
  MessagePlugin.info('打开选择用户对话框');
};

// 批量取消授权
const handleBatchCancelAuth = async () => {
  if (!currentRole.value.roleId || selectedUserKeys.value.length === 0) return;
  try {
    await roleApi.cancelAuthUserAll(currentRole.value.roleId, selectedUserKeys.value);
    MessagePlugin.success('批量取消授权成功');
    searchUsers();
  } catch (error) {
    MessagePlugin.error('批量取消授权失败');
    console.error('批量取消授权失败:', error);
  }
};

// 取消单个用户授权
const cancelAuthUser = async (row: any) => {
  if (!currentRole.value.roleId || !row.userId) return;
  try {
    await roleApi.cancelAuthUser({
      userId: row.userId,
      roleId: currentRole.value.roleId,
    });
    MessagePlugin.success('取消授权成功');
    searchUsers();
  } catch (error) {
    MessagePlugin.error('取消授权失败');
    console.error('取消授权失败:', error);
  }
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
  confirmVisible.value = false;
  deleteIdx.value = -1;
};

// 页面挂载时获取角色列表
onMounted(() => {
  getRoleList();
});
</script>

<style lang="less" scoped>
.role-page {
  padding: 16px;
  background-color: var(--td-bg-color-page);

  .search-card {
    margin-bottom: 16px;

    :deep(.t-card__body) {
      padding: 16px;
    }

    .search-form {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;

      .search-item {
        display: flex;
        align-items: center;
        gap: 8px;

        .search-label {
          font-size: 14px;
          color: var(--td-text-color-primary);
          white-space: nowrap;
        }

        .search-input {
          width: 180px;
        }
      }

      .search-buttons {
        display: flex;
        gap: 8px;
        margin-left: auto;
      }
    }
  }

  .operation-card {
    margin-bottom: 16px;

    :deep(.t-card__body) {
      padding: 12px 16px;
    }

    .operation-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .left-operations {
        display: flex;
        gap: 8px;
      }

      .right-operations {
        display: flex;
        gap: 4px;
      }
    }
  }

  .table-card {
    :deep(.t-card__body) {
      padding: 0;
    }

    .operation-icons {
      display: flex;
      justify-content: center;
      gap: 4px;

      :deep(.t-button) {
        color: var(--td-text-color-secondary);

        &:hover {
          color: var(--td-brand-color);
        }
      }
    }
  }
}

// 分配用户对话框样式
.assign-user-container {
  .assign-user-search {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;

    .search-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .search-label {
        font-size: 14px;
        color: var(--td-text-color-primary);
        white-space: nowrap;
      }
    }
  }

  .assign-user-operations {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;
  }
}

// 菜单权限样式
.menu-permission-item {
  :deep(.t-form__controls) {
    width: 100%;
  }
}

.menu-permission-container {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.menu-permission-header {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  align-items: center;
}

.menu-tree-wrapper {
  border: 1px solid var(--td-border-level-1-color);
  border-radius: 4px;
  padding: 8px 12px;
  max-height: 300px;
  overflow-y: auto;
  background-color: var(--td-bg-color-container);
}
</style>
