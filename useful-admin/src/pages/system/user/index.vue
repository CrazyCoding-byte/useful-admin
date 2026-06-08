<template>
  <div class="user-page">
    <!-- 左侧部门树 -->
    <div class="dept-tree-container">
      <t-input
        v-model="deptSearchText"
        placeholder="请输入部门名称"
        clearable
        class="dept-search"
      >
        <template #prefix-icon>
          <t-icon name="search"/>
        </template>
      </t-input>
      <t-tree
        :data="filteredDeptItems"
        activable
        hover
        transition
        :expand-all="true"
        :keys="{ label: 'label', value: 'id', children: 'children' }"
      />
    </div>

    <!-- 右侧内容区 -->
    <div class="user-content">
      <!-- 搜索区域 -->
      <t-card class="search-card" :bordered="false">
        <div class="search-form">
          <div class="search-row">
            <div class="search-item">
              <span class="search-label">用户名称</span>
              <t-input
                v-model="searchForm.userName"
                placeholder="请输入用户名称"
                clearable
                class="search-input"
              />
            </div>
            <div class="search-item">
              <span class="search-label">用户昵称</span>
              <t-input
                v-model="searchForm.nickName"
                placeholder="请输入用户昵称"
                clearable
                class="search-input"
              />
            </div>
            <div class="search-item">
              <span class="search-label">手机号码</span>
              <t-input
                v-model="searchForm.phonenumber"
                placeholder="请输入手机号码"
                clearable
                class="search-input"
              />
            </div>
            <div class="search-item">
              <span class="search-label">状态</span>
              <t-select
                v-model="searchForm.status"
                placeholder="用户状态"
                clearable
                class="search-input"
              >
                <t-option label="正常" value="0"/>
                <t-option label="禁用" value="1"/>
              </t-select>
            </div>
          </div>
          <div class="search-row">
            <div class="search-item">
              <span class="search-label">创建时间</span>
              <t-date-range-picker
                v-model="searchForm.createTime"
                placeholder="开始日期 - 结束日期"
                class="search-input"
              />
            </div>
            <div class="search-buttons">
              <t-button theme="primary" @click="getUserList">
                <template #icon>
                  <t-icon name="search"/>
                </template>
                搜索
              </t-button>
              <t-button variant="outline" @click="resetSearch">
                <template #icon>
                  <t-icon name="refresh"/>
                </template>
                重置
              </t-button>
            </div>
          </div>
        </div>
      </t-card>

      <!-- 操作按钮区域 -->
      <t-card class="operation-card" :bordered="false">
        <div class="operation-bar">
          <div class="left-operations">
            <t-button theme="primary" @click="handleAddUser">
              <template #icon>
                <t-icon name="add"/>
              </template>
              新增
            </t-button>
            <t-button theme="warning" :disabled="!selectedRowKeys.length" @click="handleBatchEdit">
              <template #icon>
                <t-icon name="edit"/>
              </template>
              修改
            </t-button>
            <t-button theme="danger" :disabled="!selectedRowKeys.length" @click="handleBatchDelete">
              <template #icon>
                <t-icon name="delete"/>
              </template>
              删除
            </t-button>
            <t-button variant="outline">
              更多
              <template #suffix-icon>
                <t-icon name="chevron-down"/>
              </template>
            </t-button>
          </div>
          <div class="right-operations">
            <t-button variant="text" shape="circle" @click="getUserList">
              <t-icon name="search"/>
            </t-button>
            <t-button variant="text" shape="circle" @click="getUserList">
              <t-icon name="refresh"/>
            </t-button>
            <t-button variant="text" shape="circle">
              <t-icon name="setting"/>
            </t-button>
          </div>
        </div>
      </t-card>

      <!-- 表格区域 -->
      <t-card class="table-card" :bordered="false">
        <t-table
          :data="userList"
          :loading="loading"
          :columns="columns"
          :row-key="rowKey"
          bordered
          stripe
          :pagination="pagination"
          :selected-row-keys="selectedRowKeys"
          @page-change="onPageChange"
          @select-change="onSelectChange"
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
              <t-button variant="text" shape="square" @click="editUser(row)">
                <t-icon name="edit"/>
              </t-button>
              <t-button variant="text" shape="square" @click="deleteUser(row.userId!)">
                <t-icon name="delete"/>
              </t-button>
              <t-button variant="text" shape="square" @click="resetUserPassword(row.userId!)">
                <t-icon name="lock-on"/>
              </t-button>
              <t-button variant="text" shape="square">
                <t-icon name="more"/>
              </t-button>
            </div>
          </template>
        </t-table>
      </t-card>
    </div>

    <!-- 新增/修改用户对话框 -->
    <t-dialog
      v-model:visible="addDialogVisible"
      :header="addForm.userId ? '修改用户' : '新增用户'"
      width="600px"
    >
      <t-form
        :data="addForm"
        :rules="addFormRules"
        ref="addFormRef"
        label-width="100px"
      >
        <t-form-item label="账号" name="userName">
          <t-input v-model="addForm.userName" placeholder="请输入账号"/>
        </t-form-item>
        <t-form-item label="昵称" name="nickName">
          <t-input v-model="addForm.nickName" placeholder="请输入昵称"/>
        </t-form-item>
        <t-form-item label="手机号码" name="phonenumber">
          <t-input v-model="addForm.phonenumber" placeholder="请输入手机号码"/>
        </t-form-item>
        <t-form-item label="邮箱" name="email">
          <t-input v-model="addForm.email" placeholder="请输入邮箱"/>
        </t-form-item>
        <t-form-item label="密码" name="password">
          <t-input v-model="addForm.password" type="password" placeholder="请输入密码"/>
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-radio-group v-model="addForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">禁用</t-radio>
          </t-radio-group>
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closeAddDialog">取消</t-button>
        <t-button theme="primary" @click="submitAddForm">确认</t-button>
      </template>
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
import {ref, onMounted, computed} from 'vue';
import {userApi} from '@/api/system/user';
import {DeptApi} from '@/api/system/dept'
import type {SysUser} from '@/api/model/userModel';
import {MessagePlugin} from 'tdesign-vue-next';
import type {PaginationProps, TableProps} from 'tdesign-vue-next';

// 部门搜索文本
const deptSearchText = ref('');

// 部门树数据
const deptItems = ref([
  {
    id: '1',
    label: 'XXX科技',
    children: [
      {id: '11', label: 'fgh'},
      {id: '12', label: 'fgn'},
      {id: '13', label: 'fghgf'},
    ],
  },
  {
    id: '2',
    label: '深圳总公司',
    children: [
      {id: '21', label: '研发部门'},
      {id: '22', label: '市场部门'},
      {id: '23', label: '测试部门'},
      {id: '24', label: '财务部门'},
      {id: '25', label: '运维部门'},
    ],
  },
  {
    id: '3',
    label: '长沙分公司',
    children: [
      {id: '31', label: '市场部门'},
      {id: '32', label: '财务部门'},
    ],
  },
]);

// 过滤后的部门树
const filteredDeptItems = computed(() => {
  if (!deptSearchText.value) return deptItems.value;
  const filter = (items: any[]) => {
    return items.filter(item => {
      if (item.label.includes(deptSearchText.value)) return true;
      if (item.children) {
        item.children = filter(item.children);
        return item.children.length > 0;
      }
      return false;
    });
  };
  return filter([...deptItems.value]);
});

// 搜索表单
const searchForm = ref<Partial<SysUser> & { createTime?: any }>({
  userName: '',
  nickName: '',
  phonenumber: '',
  status: '',
  createTime: [],
});

// 用户列表
const userList = ref<SysUser[]>([]);
const loading = ref(false);
const selectedRowKeys = ref<number[]>([]);

// 表格列配置
const columns: TableProps['columns'] = [
  {
    colKey: 'row-select',
    type: 'multiple',
    width: 46,
  },
  {
    colKey: 'userName',
    title: '用户名称',
    width: 120,
  },
  {
    colKey: 'nickName',
    title: '用户昵称',
    width: 150,
  },
  {
    colKey: 'deptName',
    title: '部门',
    width: 120,
  },
  {
    colKey: 'phonenumber',
    title: '手机号码',
    width: 140,
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
const pagination = ref<TableProps['pagination']>({
  pageSize: 10,
  current: 1,
  total: 0,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
});

// 行键
const rowKey: TableProps['rowKey'] = 'userId';

// 获取用户列表
const getUserList = async (pageInfo?: PaginationProps) => {
  loading.value = true;
  try {
    const current = pageInfo?.current || pagination.value.current || 1;
    const pageSize = pageInfo?.pageSize || pagination.value.pageSize || 10;

    const requestParams = {
      ...searchForm.value,
      pageNum: current,
      pageSize: pageSize,
    };
    console.log("拿到的数据", requestParams)
    const response = await userApi.getUserList(requestParams);
    console.log("获取响应的数据111", response)
    // 适配后端返回格式：{ code: 200, data: { records: [], total: 0 } }
    const data = response.data || response;
    userList.value = data.records || [];
    pagination.value.total = data.total || 0;
  } catch (error) {
    MessagePlugin.error('获取用户列表失败');
    console.error('获取用户列表失败:', error);
    userList.value = [];
    pagination.value.total = 0;
  } finally {
    loading.value = false;
  }
};

//获取部门列表
const getDeptList = async () => {
  try {
    const response = await DeptApi.getDeptList();
    console.log("获取部门响应数据", response);
    // 后端已返回带children的树结构，只需做字段名映射
    const deptTree = convertToTree(response || []);
    deptItems.value = deptTree;
  } catch (error) {
    console.error('获取部门列表失败:', error);
    deptItems.value = [];
  }
};

// 递归转换部门树结构字段名
const convertToTree = (data: any[]): any[] => {
  if (!data || data.length === 0) return [];
  return data.map(item => ({
    id: item.deptId,
    label: item.deptName,
    parentId: item.parentId,
    children: item.children && item.children.length > 0 ? convertToTree(item.children) : []
  }));
};
// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    userName: '',
    nickName: '',
    phonenumber: '',
    status: '',
    createTime: [],
  };
  getUserList();
};

// 处理分页变化
const onPageChange: TableProps['onPageChange'] = async (pageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  await getUserList(pageInfo);
};

// 处理选择变化
const onSelectChange: TableProps['onSelectChange'] = (value, params) => {
  selectedRowKeys.value = value as number[];
  console.log(value, params);
};

// 批量修改
const handleBatchEdit = () => {
  if (selectedRowKeys.value.length === 1) {
    const user = userList.value.find(u => u.userId === selectedRowKeys.value[0]);
    if (user) editUser(user);
  } else {
    MessagePlugin.warning('请选择一条记录进行修改');
  }
};

// 批量删除
const handleBatchDelete = () => {
  if (selectedRowKeys.value.length > 0) {
    confirmVisible.value = true;
  }
};

// 新增用户对话框
const addDialogVisible = ref(false);
const addFormRef = ref<any>();
const addForm = ref({
  userId: null as number | null,
  userName: '',
  nickName: '',
  phonenumber: '',
  email: '',
  password: '',
  status: '0',
});

const addFormRules = ref({
  userName: [
    {required: true, message: '请输入用户名', trigger: ['blur', 'change']},
    {min: 2, max: 30, message: '用户名长度应在2-30个字符之间', trigger: ['blur', 'change']}
  ],
  nickName: [
    {required: true, message: '请输入昵称', trigger: ['blur', 'change']},
    {min: 2, max: 30, message: '昵称长度应在2-30个字符之间', trigger: ['blur', 'change']}
  ],
  phonenumber: [
    {required: true, message: '请输入手机号码', trigger: ['blur', 'change']},
    {pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: ['blur', 'change']}
  ],
  email: [
    {required: true, message: '请输入邮箱', trigger: ['blur', 'change']},
    {type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change']}
  ],
  password: [
    {required: true, message: '请输入密码', trigger: ['blur', 'change']},
    {min: 6, max: 20, message: '密码长度应在6-20个字符之间', trigger: ['blur', 'change']}
  ],
  status: [
    {required: true, message: '请选择状态', trigger: ['blur', 'change']}
  ]
});

// 新增用户
const handleAddUser = () => {
  addForm.value = {
    userId: null,
    userName: '',
    nickName: '',
    phonenumber: '',
    email: '',
    password: '',
    status: '0',
  };
  addDialogVisible.value = true;
};

// 关闭新增用户对话框
const closeAddDialog = () => {
  addDialogVisible.value = false;
};

// 提交用户表单（新增或修改）
const submitAddForm = async () => {
  if (!addFormRef.value) return;
  try {
    await addFormRef.value.validate();
    const submitData = {...addForm.value};
    if (submitData.userId && !submitData.password) {
      delete submitData.password;
    }
    await userApi.saveUser(submitData);
    MessagePlugin.success(addForm.value.userId ? '修改用户成功' : '新增用户成功');
    addDialogVisible.value = false;
    getUserList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 编辑用户
const editUser = (user: SysUser) => {
  addForm.value = {
    userId: user.userId || null,
    userName: user.userName || '',
    nickName: user.nickName || '',
    phonenumber: user.phonenumber || '',
    email: user.email || '',
    password: '',
    status: user.status || '0',
  };
  addDialogVisible.value = true;
};

// 删除用户
const deleteUser = (userId: number) => {
  deleteIdx.value = userId;
  confirmVisible.value = true;
};

// 重置用户密码
const resetUserPassword = async (userId: number) => {
  try {
    await userApi.resetPassword({userId, password: '123456'});
    MessagePlugin.success('密码重置成功，新密码为：123456');
  } catch (error) {
    MessagePlugin.error('密码重置失败');
    console.error('密码重置失败:', error);
  }
};

// 确认对话框
const confirmVisible = ref(false);
const deleteIdx = ref<number>(-1);
const confirmBody = computed(() => {
  return '删除后，用户的所有信息将被清空，且无法恢复';
});

// 确认删除
const onConfirmDelete = async () => {
  try {
    await userApi.deleteUser([deleteIdx.value]);
    MessagePlugin.success('删除成功');
    getUserList();
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

// 页面挂载时获取用户列表
onMounted(async () => {
  await getUserList({
    current: pagination.value.current || 1,
    pageSize: pagination.value.pageSize || 10,
  })
  await getDeptList()
});
</script>

<style lang="less" scoped>
.user-page {
  display: flex;
  height: calc(100vh - 100px);
  background-color: var(--td-bg-color-container);

  .dept-tree-container {
    width: 260px;
    padding: 16px;
    border-right: 1px solid var(--td-border-level-1-color);
    background-color: var(--td-bg-color-container);

    .dept-search {
      margin-bottom: 12px;
    }
  }

  .user-content {
    flex: 1;
    padding: 16px;
    overflow: auto;
    background-color: var(--td-bg-color-page);

    .search-card {
      margin-bottom: 16px;

      :deep(.t-card__body) {
        padding: 16px;
      }

      .search-form {
        display: flex;
        flex-direction: column;
        gap: 16px;

        .search-row {
          display: flex;
          align-items: center;
          gap: 24px;
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
              width: 200px;
            }
          }

          .search-buttons {
            display: flex;
            gap: 8px;
            margin-left: auto;
          }
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
}
</style>
