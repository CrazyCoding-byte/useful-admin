<template>
  <div class="tenant-container">
    <!-- 搜索表单 -->
    <t-card class="search-card" :bordered="false">
      <t-form ref="searchFormRef" :data="searchForm" label-width="80px" @submit="handleSearch" @reset="handleReset">
        <t-row :gutter="[16, 16]">
          <t-col :span="3">
            <t-form-item label="租户名称" name="tenantName">
              <t-input v-model="searchForm.tenantName" placeholder="请输入租户名称" clearable />
            </t-form-item>
          </t-col>
          <t-col :span="3">
            <t-form-item label="联系人" name="contactName">
              <t-input v-model="searchForm.contactName" placeholder="请输入联系人" clearable />
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

    <!-- 表格卡片 -->
    <t-card class="table-card" :bordered="false">
      <div class="table-toolbar">
        <t-button theme="primary" @click="handleAdd">
          <template #icon><add-icon /></template>
          新增租户
        </t-button>
      </div>

      <t-table
        :data="tenantList"
        :columns="columns"
        :loading="loading"
        row-key="tenantId"
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
        <template #isDefault="{ row }">
          <t-tag :theme="row.isDefault === '1' ? 'warning' : 'default'" variant="light">
            {{ row.isDefault === '1' ? '系统默认' : '普通租户' }}
          </t-tag>
        </template>
        <template #expireTime="{ row }">
          <span :class="{ 'expired-text': isExpired(row) }">
            {{ row.expireTime || '永久有效' }}
          </span>
        </template>
        <template #operation="{ row }">
          <t-space>
            <t-link theme="primary" hover="color" @click="handleEdit(row)">编辑</t-link>
            <t-link
              v-if="row.isDefault !== '1'"
              theme="danger"
              hover="color"
              @click="handleDelete(row)"
            >删除</t-link>
          </t-space>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/编辑弹窗 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="520px"
      :confirm-btn="{ content: '确定', loading: submitLoading }"
      @confirm="handleSubmit"
      @close="handleDialogClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="100px">
        <t-form-item label="企业名称" name="tenantName" required>
          <t-input v-model="formData.tenantName" placeholder="请输入企业名称" />
        </t-form-item>

        <t-form-item label="联系人" name="contactName" required>
          <t-input v-model="formData.contactName" placeholder="请输入联系人" />
        </t-form-item>

        <t-form-item label="联系电话" name="contactPhone" required>
          <t-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </t-form-item>

        <t-form-item label="租户套餐" name="packageId">
          <t-select v-model="formData.packageId" placeholder="请选择租户套餐" clearable>
            <t-option
              v-for="pkg in packageOptions"
              :key="pkg.packageId"
              :value="pkg.packageId"
              :label="pkg.packageName"
            />
          </t-select>
        </t-form-item>

        <t-form-item label="过期时间" name="expireTime">
          <t-date-picker
            v-model="formData.expireTime"
            placeholder="请选择过期时间"
            enable-time-picker
            style="width: 100%"
          />
        </t-form-item>

        <t-form-item label="用户数量" name="userCount">
          <t-input v-model="formData.userCount" type="number" placeholder="0" />
        </t-form-item>

        <t-form-item label="绑定域名" name="domain">
          <t-input v-model="formData.domain" placeholder="请输入绑定域名" />
        </t-form-item>

        <t-form-item label="企业地址" name="address">
          <t-input v-model="formData.address" placeholder="请输入企业地址" />
        </t-form-item>

        <t-form-item label="企业代码" name="creditCode">
          <t-input v-model="formData.creditCode" placeholder="请输入统一社会信用代码" />
        </t-form-item>

        <t-form-item label="企业简介" name="remark">
          <t-textarea v-model="formData.remark" placeholder="请输入企业简介" :rows="3" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 删除确认对话框 -->
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
import { tenantApi, tenantPackageApi } from '@/api/system/tenant';

// ==================== 表格 ====================
const columns = [
  { colKey: 'tenantId', title: '租户编号', width: 110 },
  { colKey: 'tenantName', title: '租户名称', width: 160, ellipsis: true },
  { colKey: 'contactName', title: '联系人', width: 100 },
  { colKey: 'contactPhone', title: '联系电话', width: 130 },
  { colKey: 'status', title: '状态', width: 80 },
  { colKey: 'isDefault', title: '租户类型', width: 100 },
  { colKey: 'expireTime', title: '过期时间', width: 170 },
  { colKey: 'createTime', title: '创建时间', width: 170 },
  { colKey: 'operation', title: '操作', width: 130, fixed: 'right' },
];

const tenantList = ref([]);
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
  tenantName: '',
  contactName: '',
  status: '',
});

// ==================== 弹窗 ====================
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const dialogTitle = computed(() => (isEdit.value ? '编辑租户' : '新增租户'));

// ==================== 表单 ====================
const formData = reactive<Record<string, any>>({
  tenantId: '',
  tenantName: '',
  contactName: '',
  contactPhone: '',
  packageId: null,
  expireTime: '',
  userCount: 0,
  domain: '',
  address: '',
  creditCode: '',
  remark: '',
});

const formRules = {
  tenantName: [{ required: true, message: '请输入企业名称', type: 'error' }],
  contactName: [{ required: true, message: '请输入联系人', type: 'error' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', type: 'error' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', type: 'error' },
  ],
};

// 套餐选项
const packageOptions = ref<{ packageId: number; packageName: string }[]>([]);

// ==================== 删除确认 ====================
const deleteVisible = ref(false);
const deleteTarget = ref<Record<string, any>>({});
const deleteBody = computed(() => {
  return `确定要删除租户「${deleteTarget.value.tenantName}」吗？该租户下的所有数据将被清空，且无法恢复！`;
});

// ==================== 辅助方法 ====================
const isExpired = (row: Record<string, any>) => {
  if (!row.expireTime) return false;
  return new Date(row.expireTime) < new Date();
};

const resetFormData = () => {
  formData.tenantId = '';
  formData.tenantName = '';
  formData.contactName = '';
  formData.contactPhone = '';
  formData.packageId = null;
  formData.expireTime = '';
  formData.userCount = 0;
  formData.domain = '';
  formData.address = '';
  formData.creditCode = '';
  formData.remark = '';
  formRef.value?.clearValidate();
};

// ==================== 数据加载 ====================
const loadPackageOptions = async () => {
  try {
    const res = await tenantPackageApi.getOptions();
    packageOptions.value = Array.isArray(res) ? res : (res?.data || []);
  } catch (error) {
    console.error('加载套餐选项失败:', error);
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
    const res = await tenantApi.getList(params);
    if (res?.list && Array.isArray(res.list)) {
      tenantList.value = res.list;
      pagination.total = res.total ?? res.list.length;
    } else if (Array.isArray(res)) {
      tenantList.value = res;
      pagination.total = res.length;
    } else if (res?.data && Array.isArray(res.data)) {
      tenantList.value = res.data;
      pagination.total = res.total ?? res.data.length;
    } else if (res?.rows) {
      tenantList.value = res.rows;
      pagination.total = res.total ?? 0;
    } else {
      tenantList.value = [];
      pagination.total = 0;
    }
  } catch (error) {
    console.error('获取租户列表失败:', error);
    MessagePlugin.error('获取租户列表失败');
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
  searchForm.tenantName = '';
  searchForm.contactName = '';
  searchForm.status = '';
  pagination.current = 1;
  fetchData();
};

const onPageChange = (pageInfo: PageInfo) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

const handleAdd = () => {
  isEdit.value = false;
  resetFormData();
  dialogVisible.value = true;
};

const handleEdit = (row: Record<string, any>) => {
  isEdit.value = true;
  resetFormData();
  formData.tenantId = row.tenantId || '';
  formData.tenantName = row.tenantName || '';
  formData.contactName = row.contactName || '';
  formData.contactPhone = row.contactPhone || '';
  formData.packageId = row.packageId || null;
  formData.expireTime = row.expireTime || '';
  formData.domain = row.domain || '';
  formData.address = row.address || '';
  formData.creditCode = row.creditCode || '';
  formData.remark = row.remark || '';
  dialogVisible.value = true;
};

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row;
  deleteVisible.value = true;
};

const handleDeleteConfirm = async () => {
  try {
    const res = await tenantApi.delete(deleteTarget.value.tenantId);
    if (res.code === 200) {
      MessagePlugin.success('删除成功');
      deleteVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '删除失败');
    }
  } catch (error) {
    console.error('删除租户失败:', error);
    MessagePlugin.error('删除失败');
  }
};

const handleSubmit = async () => {
  const validateResult = await formRef.value?.validate();
  if (validateResult !== true) return;

  const submitData = { ...formData };
  if (submitData.expireTime && submitData.expireTime instanceof Date) {
    const date = submitData.expireTime as Date;
    submitData.expireTime = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
  }

  submitLoading.value = true;
  try {
    const api = isEdit.value ? tenantApi.update : tenantApi.add;
    const res = await api(submitData);
    if (res.code === 200) {
      MessagePlugin.success(isEdit.value ? '修改成功' : '创建成功');
      dialogVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '操作失败');
    }
  } catch (error) {
    console.error('保存租户失败:', error);
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
  loadPackageOptions();
  fetchData();
});
</script>

<style scoped lang="less">
.tenant-container {
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

  .expired-text {
    color: #e34d59;
  }
}
</style>
