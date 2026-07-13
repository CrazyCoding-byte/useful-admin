<template>
  <div class="tenant-container">
    <t-card class="search-card" :bordered="false">
      <t-form ref="searchFormRef" :data="searchForm" label-width="80px" @submit="handleSearch" @reset="handleReset">
        <t-row :gutter="[16, 16]">
          <t-col :span="3">
            <t-form-item label="企业名称" name="companyName">
              <t-input v-model="searchForm.companyName" placeholder="请输入企业名称" clearable />
            </t-form-item>
          </t-col>
          <t-col :span="3">
            <t-form-item label="联系人" name="contactUserName">
              <t-input v-model="searchForm.contactUserName" placeholder="请输入联系人" clearable />
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
        row-key="id"
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
        <template #expireTime="{ row }">
          <span :class="{ 'expired-text': isExpired(row) }">
            {{ row.expireTime || '永久有效' }}
          </span>
        </template>
        <template #operation="{ row }">
          <t-space>
            <t-link theme="primary" hover="color" @click="handleEdit(row)">编辑</t-link>
            <t-link
              theme="danger"
              hover="color"
              @click="handleDelete(row)"
            >删除</t-link>
          </t-space>
        </template>
      </t-table>
    </t-card>

    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="520px"
      :confirm-btn="{ content: '确定', loading: submitLoading }"
      @confirm="handleSubmit"
      @close="handleDialogClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="100px">
        <t-form-item label="企业名称" name="companyName" required>
          <t-input v-model="formData.companyName" placeholder="请输入企业名称" />
        </t-form-item>

        <t-form-item label="联系人" name="contactUserName" required>
          <t-input v-model="formData.contactUserName" placeholder="请输入联系人" />
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

        <t-form-item label="用户数量" name="accountCount">
          <t-input v-model="formData.accountCount" type="number" placeholder="-1表示不限制" />
        </t-form-item>

        <t-form-item label="绑定域名" name="domain">
          <t-input v-model="formData.domain" placeholder="请输入绑定域名" />
        </t-form-item>

        <t-form-item label="企业地址" name="address">
          <t-input v-model="formData.address" placeholder="请输入企业地址" />
        </t-form-item>

        <t-form-item label="企业代码" name="licenseNumber">
          <t-input v-model="formData.licenseNumber" placeholder="请输入统一社会信用代码" />
        </t-form-item>

        <t-form-item label="企业简介" name="intro">
          <t-textarea v-model="formData.intro" placeholder="请输入企业简介" :rows="3" />
        </t-form-item>

        <t-form-item label="备注" name="remark">
          <t-textarea v-model="formData.remark" placeholder="请输入备注" :rows="2" />
        </t-form-item>
      </t-form>
    </t-dialog>

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

const columns = [
  { colKey: 'tenantId', title: '租户编号', width: 110 },
  { colKey: 'companyName', title: '企业名称', width: 160, ellipsis: true },
  { colKey: 'contactUserName', title: '联系人', width: 100 },
  { colKey: 'contactPhone', title: '联系电话', width: 130 },
  { colKey: 'status', title: '状态', width: 80 },
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

const searchForm = reactive({
  companyName: '',
  contactUserName: '',
  status: '',
});

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const dialogTitle = computed(() => (isEdit.value ? '编辑租户' : '新增租户'));

const formData = reactive<Record<string, any>>({
  id: null,
  tenantId: '',
  companyName: '',
  contactUserName: '',
  contactPhone: '',
  packageId: null,
  expireTime: '',
  accountCount: -1,
  domain: '',
  address: '',
  licenseNumber: '',
  intro: '',
  remark: '',
});

const formRules = {
  companyName: [{ required: true, message: '请输入企业名称', type: 'error' }],
  contactUserName: [{ required: true, message: '请输入联系人', type: 'error' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', type: 'error' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', type: 'error' },
  ],
};

const packageOptions = ref<{ packageId: number; packageName: string }[]>([]);

const deleteVisible = ref(false);
const deleteTarget = ref<Record<string, any>>({});
const deleteBody = computed(() => {
  return `确定要删除租户「${deleteTarget.value.companyName}」吗？该租户下的所有数据将被清空，且无法恢复！`;
});

const isExpired = (row: Record<string, any>) => {
  if (!row.expireTime) return false;
  return new Date(row.expireTime) < new Date();
};

const resetFormData = () => {
  formData.id = null;
  formData.tenantId = '';
  formData.companyName = '';
  formData.contactUserName = '';
  formData.contactPhone = '';
  formData.packageId = null;
  formData.expireTime = '';
  formData.accountCount = -1;
  formData.domain = '';
  formData.address = '';
  formData.licenseNumber = '';
  formData.intro = '';
  formData.remark = '';
  formRef.value?.clearValidate();
};

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

const handleSearch = () => {
  pagination.current = 1;
  fetchData();
};

const handleReset = () => {
  searchForm.companyName = '';
  searchForm.contactUserName = '';
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
  formData.id = row.id || null;
  formData.tenantId = row.tenantId || '';
  formData.companyName = row.companyName || '';
  formData.contactUserName = row.contactUserName || '';
  formData.contactPhone = row.contactPhone || '';
  formData.packageId = row.packageId || null;
  formData.expireTime = row.expireTime || '';
  formData.accountCount = row.accountCount ?? -1;
  formData.domain = row.domain || '';
  formData.address = row.address || '';
  formData.licenseNumber = row.licenseNumber || '';
  formData.intro = row.intro || '';
  formData.remark = row.remark || '';
  dialogVisible.value = true;
};

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row;
  deleteVisible.value = true;
};

const handleDeleteConfirm = async () => {
  try {
    const res = await tenantApi.delete(deleteTarget.value.id);
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
