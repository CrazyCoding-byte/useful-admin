<template>
  <div class="tenant-container">
    <t-card class="search-card" :bordered="false">
      <t-row justify="space-between">
        <t-col :span="12">
          <t-button theme="primary" @click="handleAdd">
            <template #icon><t-icon name="add" /></template>
            新增租户
          </t-button>
        </t-col>
      </t-row>
    </t-card>

    <t-card class="table-card" :bordered="false">
      <t-table
        :data="tenantList"
        :columns="columns"
        :loading="loading"
        row-key="tenantId"
        :pagination="pagination"
        @page-change="onPageChange"
      >
        <template #status="{ row }">
          <t-tag :theme="row.status === '0' ? 'success' : 'danger'">
            {{ row.status === '0' ? '正常' : '停用' }}
          </t-tag>
        </template>
        <template #isDefault="{ row }">
          <t-tag :theme="row.isDefault === '1' ? 'warning' : 'default'">
            {{ row.isDefault === '1' ? '系统默认' : '普通租户' }}
          </t-tag>
        </template>
        <template #expireTime="{ row }">
          <span :class="{ 'text-danger': row.expired }">
            {{ row.expireTime || '永久有效' }}
          </span>
        </template>
        <template #operation="{ row }">
          <t-space>
            <t-button variant="text" theme="primary" @click="handleEdit(row)">编辑</t-button>
            <t-button
              v-if="row.isDefault !== '1'"
              variant="text"
              theme="danger"
              @click="handleDelete(row)"
            >删除</t-button>
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
      @close="handleClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="100px">
        <t-form-item label="租户名称" name="tenantName">
          <t-input v-model="formData.tenantName" placeholder="请输入租户名称" />
        </t-form-item>
        <t-form-item label="联系人" name="contactName">
          <t-input v-model="formData.contactName" placeholder="请输入联系人" />
        </t-form-item>
        <t-form-item label="联系电话" name="contactPhone">
          <t-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </t-form-item>
        <t-form-item label="联系邮箱" name="contactEmail">
          <t-input v-model="formData.contactEmail" placeholder="请输入联系邮箱" />
        </t-form-item>
        <t-form-item label="域名" name="domain">
          <t-input v-model="formData.domain" placeholder="请输入域名" />
        </t-form-item>
        <t-form-item label="过期时间" name="expireTime">
          <t-date-picker
            v-model="formData.expireTime"
            placeholder="请选择过期时间"
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { MessagePlugin, type FormInstanceFunctions } from 'tdesign-vue-next';
import { tenantApi } from '@/api/system/tenant';

// 表格列定义
const columns = [
  { colKey: 'tenantId', title: '租户编号', width: 120 },
  { colKey: 'tenantName', title: '租户名称', width: 180 },
  { colKey: 'contactName', title: '联系人', width: 120 },
  { colKey: 'contactPhone', title: '联系电话', width: 140 },
  { colKey: 'status', title: '状态', width: 100 },
  { colKey: 'isDefault', title: '类型', width: 120 },
  { colKey: 'expireTime', title: '过期时间', width: 160 },
  { colKey: 'operation', title: '操作', width: 150, fixed: 'right' },
];

// 表格数据
const tenantList = ref([]);
const loading = ref(false);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

// 弹窗相关
const dialogVisible = ref(false);
const dialogTitle = ref('新增租户');
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const isEdit = ref(false);

// 表单数据
const formData = reactive({
  tenantId: '',
  tenantName: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  domain: '',
  expireTime: '',
  status: '0',
  remark: '',
});

// 表单校验规则
const formRules = {
  tenantName: [{ required: true, message: '请输入租户名称', type: 'error' }],
  contactName: [{ required: true, message: '请输入联系人', type: 'error' }],
  contactPhone: [{ required: true, message: '请输入联系电话', type: 'error' }],
};

// 获取租户列表
const getTenantList = async () => {
  loading.value = true;
  try {
    const res = await tenantApi.getList();
    if (res.code === 200) {
      tenantList.value = res.data || [];
      pagination.total = tenantList.value.length;
    }
  } catch (error) {
    console.error('获取租户列表失败:', error);
    MessagePlugin.error('获取租户列表失败');
  } finally {
    loading.value = false;
  }
};

// 分页变化
const onPageChange = (pageInfo: { current: number; pageSize: number }) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
};

// 新增租户
const handleAdd = () => {
  isEdit.value = false;
  dialogTitle.value = '新增租户';
  resetForm();
  dialogVisible.value = true;
};

// 编辑租户
const handleEdit = (row: Record<string, unknown>) => {
  isEdit.value = true;
  dialogTitle.value = '编辑租户';
  Object.assign(formData, row);
  dialogVisible.value = true;
};

// 删除租户
const handleDelete = (row: Record<string, unknown>) => {
  const tenantId = row.tenantId as string;
  const tenantName = row.tenantName as string;

  // 系统默认租户不能删除
  if (row.isDefault === '1') {
    MessagePlugin.warning('系统默认租户不能删除');
    return;
  }

  // 使用浏览器原生确认框
  if (confirm(`确定要删除租户「${tenantName}」吗？此操作不可恢复！`)) {
    tenantApi.delete(tenantId).then((res) => {
      if (res.code === 200) {
        MessagePlugin.success('删除成功');
        getTenantList();
      } else {
        MessagePlugin.error(res.msg || '删除失败');
      }
    }).catch((error) => {
      console.error('删除租户失败:', error);
      MessagePlugin.error('删除失败');
    });
  }
};

// 提交表单
const handleSubmit = () => {
  formRef.value?.validate().then((result) => {
    if (result !== true) return;

    submitLoading.value = true;
    const api = isEdit.value ? tenantApi.update : tenantApi.add;

    api(formData)
      .then((res) => {
        if (res.code === 200) {
          MessagePlugin.success(isEdit.value ? '修改成功' : '创建成功');
          dialogVisible.value = false;
          getTenantList();
        } else {
          MessagePlugin.error(res.msg || '操作失败');
        }
      })
      .catch((error) => {
        console.error('保存租户失败:', error);
        MessagePlugin.error('操作失败');
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
};

// 关闭弹窗
const handleClose = () => {
  dialogVisible.value = false;
  resetForm();
};

// 重置表单
const resetForm = () => {
  formData.tenantId = '';
  formData.tenantName = '';
  formData.contactName = '';
  formData.contactPhone = '';
  formData.contactEmail = '';
  formData.domain = '';
  formData.expireTime = '';
  formData.status = '0';
  formData.remark = '';
  formRef.value?.reset();
};

// 页面加载时获取数据
onMounted(() => {
  getTenantList();
});
</script>

<style scoped lang="less">
.tenant-container {
  padding: 16px;

  .search-card {
    margin-bottom: 16px;
  }

  .text-danger {
    color: #e34d59;
  }
}
</style>
