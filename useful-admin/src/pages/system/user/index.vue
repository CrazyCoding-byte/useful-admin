<template>
  <div class="user-page">
    <t-card class="user-card-container">
      <t-row justify="space-between" align="middle">
        <div class="left-operation-container">
          <t-button theme="primary" @click="handleAddUser"> 新增用户 </t-button>
          <t-button variant="base" theme="default" :disabled="!selectedRowKeys.length"> 批量删除 </t-button>
          <p v-if="!!selectedRowKeys.length" class="selected-count">已选{{ selectedRowKeys.length }}项</p>
        </div>
        <div class="search-container">
          <t-input
            v-model="searchForm.userName"
            placeholder="用户名"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-input
            v-model="searchForm.phonenumber"
            placeholder="手机号码"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-button theme="primary" @click="getUserList">查询</t-button>
          <t-button @click="resetSearch">重置</t-button>
        </div>
      </t-row>

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
          <t-tag v-if="row.status === '0'" theme="success" variant="light"> 正常 </t-tag>
          <t-tag v-else theme="danger" variant="light"> 禁用 </t-tag>
        </template>
        <template #op="{ row }">
          <a class="t-button-link" @click="editUser(row)">编辑</a>
          <a class="t-button-link" @click="deleteUser(row.userId!)">删除</a>
          <a class="t-button-link" @click="resetUserPassword(row.userId!)">重置密码</a>
        </template>
      </t-table>
    </t-card>

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
          <t-input v-model="addForm.userName" placeholder="请输入账号" />
        </t-form-item>
        <t-form-item label="昵称" name="nickName">
          <t-input v-model="addForm.nickName" placeholder="请输入昵称" />
        </t-form-item>
        <t-form-item label="手机号码" name="phonenumber">
          <t-input v-model="addForm.phonenumber" placeholder="请输入手机号码" />
        </t-form-item>
        <t-form-item label="邮箱" name="email">
          <t-input v-model="addForm.email" placeholder="请输入邮箱" />
        </t-form-item>
        <t-form-item label="密码" name="password">
          <t-input v-model="addForm.password" type="password" placeholder="请输入密码" />
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
import { userApi } from '@/api/system/user';
import type { SysUser } from '@/api/model/userModel';
import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next';
import type { PaginationProps, TableProps } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysUser>>({
  userName: '',
  phonenumber: '',
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
    colKey: 'userId',
    title: '用户ID',
  },
  {
    colKey: 'userName',
    title: '账号',
  },
  {
    colKey: 'nickName',
    title: '昵称',
  },
  {
    colKey: 'phonenumber',
    title: '手机号码',
  },
  {
    colKey: 'email',
    title: '邮箱',
    ellipsis: true,
  },
  {
    colKey: 'status',
    title: '状态',
    render: (h: any, { row }: { row: SysUser }) => {
      if (row.status === '0') {
        return h('t-tag', { props: { theme: 'success', variant: 'light' } }, ' 正常 ');
      } else {
        return h('t-tag', { props: { theme: 'danger', variant: 'light' } }, ' 禁用 ');
      }
    },
  },
  {
    colKey: 'op',
    title: '操作',
    render: (h: any, { row }: { row: SysUser }) => {
      return h('div', [
        h('a', {
          class: 't-button-link',
          on: { click: () => editUser(row) }
        }, '编辑'),
        h('a', {
          class: 't-button-link',
          on: { click: () => deleteUser(row.userId!) }
        }, '删除'),
        h('a', {
          class: 't-button-link',
          on: { click: () => resetUserPassword(row.userId!) }
        }, '重置密码')
      ]);
    },
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
      pageSize: pageSize
    };
    console.log('请求参数:', requestParams);

    // 使用原始的userApi请求
    const response = await userApi.getUserList(requestParams);
    console.log('响应数据:', response);

    // 直接使用响应数据
    userList.value = response.records || [];
    console.log('用户列表:', userList.value);
    console.log('用户列表长度:', userList.value.length);

    pagination.value.total = response.total || 0;
    console.log('总记录数:', pagination.value.total);

  } catch (error) {
    MessagePlugin.error('获取用户列表失败');
    console.error('获取用户列表失败:', error);
    userList.value = [];
    pagination.value.total = 0;
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    userName: '',
    phonenumber: '',
  };
  getUserList();
};

// 处理分页变化
const onPageChange: TableProps['onPageChange'] = async (pageInfo) => {
  console.log('page-change', pageInfo);
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  await getUserList(pageInfo);
};

// 处理选择变化
const onSelectChange: TableProps['onSelectChange'] = (value, params) => {
  selectedRowKeys.value = value;
  console.log(value, params);
};

// 新增用户对话框
const addDialogVisible = ref(false);
const addFormRef = ref<any>();
const addForm = ref({
  userId: null,
  userName: '',
  nickName: '',
  phonenumber: '',
  email: '',
  password: '',
  status: '0'
});

const addFormRules = ref({
  userName: [
    { required: true, message: '请输入用户名', trigger: ['blur', 'change'] },
    { min: 2, max: 30, message: '用户名长度应在2-30个字符之间', trigger: ['blur', 'change'] }
  ],
  nickName: [
    { required: true, message: '请输入昵称', trigger: ['blur', 'change'] },
    { min: 2, max: 30, message: '昵称长度应在2-30个字符之间', trigger: ['blur', 'change'] }
  ],
  phonenumber: [
    { required: true, message: '请输入手机号码', trigger: ['blur', 'change'] },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: ['blur', 'change'] }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: ['blur', 'change'] },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'change'] },
    { min: 6, max: 20, message: '密码长度应在6-20个字符之间', trigger: ['blur', 'change'] }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: ['blur', 'change'] }
  ]
});

// 新增用户
const handleAddUser = () => {
  console.log('新增用户');
  // 重置表单
  addForm.value = {
    userId: null,
    userName: '',
    nickName: '',
    phonenumber: '',
    email: '',
    password: '',
    status: '0'
  };
  // 打开对话框
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
    
    // 准备提交数据
    const submitData = { ...addForm.value };
    
    // 对于编辑用户，如果密码为空，不发送password字段
    if (submitData.userId && !submitData.password) {
      delete submitData.password;
    }
    
    console.log('提交表单:', submitData);
    
    // 使用同一个API接口处理新增和修改
    await userApi.saveUser(submitData);
    MessagePlugin.success(addForm.value.userId ? '修改用户成功' : '新增用户成功');
    
    // 关闭对话框
    addDialogVisible.value = false;
    // 刷新用户列表
    getUserList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 编辑用户
const editUser = (user: SysUser) => {
  console.log('编辑用户:', user);
  try {
    // 直接使用传入的用户数据填充表单
    addForm.value = {
      userId: user.userId || null,
      userName: user.userName || '',
      nickName: user.nickName || '',
      phonenumber: user.phonenumber || '',
      email: user.email || '',
      password: '', // 编辑时密码为空，不修改密码
      status: user.status || '0'
    };
    console.log('填充表单数据:', addForm.value);
    // 打开对话框
    addDialogVisible.value = true;
  } catch (error) {
    MessagePlugin.error('编辑用户失败');
    console.error('编辑用户失败:', error);
  }
};

// 删除用户
const deleteUser = (userId: number) => {
  deleteIdx.value = userId;
  confirmVisible.value = true;
};

// 重置用户密码
const resetUserPassword = async (userId: number) => {
  try {
    await userApi.resetPassword({ userId, password: '123456' });
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
  deleteIdx.value = -1;
};

// 页面挂载时获取用户列表
onMounted(async () => {
  await getUserList({
    current: pagination.value.current || 1,
    pageSize: pagination.value.pageSize || 10,
  });
});
</script>

<style lang="less" scoped>
.user-card-container {
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

.t-button-link {
  margin-right: 10px;
  cursor: pointer;
}
</style>
