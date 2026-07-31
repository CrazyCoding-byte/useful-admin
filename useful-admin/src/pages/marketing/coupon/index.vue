<template>
  <div class="coupon-container">
    <!-- 搜索表单 -->
    <t-card class="search-card" :bordered="false">
      <t-form :data="searchForm" label-width="80px" @submit="handleSearch" @reset="handleReset">
        <t-row :gutter="[16, 16]">
          <t-col :span="4">
            <t-form-item label="券名称" name="couponName">
              <t-input v-model="searchForm.couponName" placeholder="请输入优惠券名称" clearable />
            </t-form-item>
          </t-col>
          <t-col :span="4">
            <t-form-item label="券类型" name="couponType">
              <t-select v-model="searchForm.couponType" placeholder="请选择券类型" clearable>
                <t-option value="FULL_REDUCTION" label="满减券" />
                <t-option value="CASH" label="现金券" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="4">
            <t-form-item label="适用范围" name="rangeType">
              <t-select v-model="searchForm.rangeType" placeholder="请选择适用范围" clearable>
                <t-option value="ALL" label="通用" />
                <t-option value="SKU" label="SKU" />
                <t-option value="CATEGORY" label="分类" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="4">
            <t-form-item label="发布状态" name="publishStatus">
              <t-select v-model="searchForm.publishStatus" placeholder="请选择发布状态" clearable>
                <t-option :value="true" label="已发布" />
                <t-option :value="false" label="未发布" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="4" class="search-btns">
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
          新增优惠券
        </t-button>
      </div>

      <t-table
        :data="couponList"
        :columns="columns"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        hover
        stripe
        @page-change="onPageChange"
      >
        <template #couponType="{ row }">
          <t-tag :theme="row.couponType === 'FULL_REDUCTION' ? 'primary' : 'warning'" variant="light">
            {{ couponTypeMap[row.couponType] || row.couponType }}
          </t-tag>
        </template>
        <template #rangeType="{ row }">
          <t-tag theme="default" variant="light">
            {{ rangeTypeMap[row.rangeType] || row.rangeType }}
          </t-tag>
        </template>
        <template #amount="{ row }">
          <span>¥{{ row.amount }}</span>
        </template>
        <template #condition="{ row }">
          <span>满 ¥{{ row.conditionAmount }} 可用</span>
        </template>
        <template #validTime="{ row }">
          <span>{{ row.startTime || '-' }} ~ {{ row.endTime || '-' }}</span>
        </template>
        <template #couponStatus="{ row }">
          <t-tag :theme="getCouponStatus(row).theme" variant="light">
            {{ getCouponStatus(row).label }}
          </t-tag>
        </template>
        <template #publishStatus="{ row }">
          <t-tag :theme="row.publishStatus ? 'success' : 'default'" variant="light">
            {{ row.publishStatus ? '已发布' : '未发布' }}
          </t-tag>
        </template>
        <template #operation="{ row }">
          <t-space>
            <t-link theme="primary" hover="color" @click="handleEdit(row)">编辑</t-link>
            <t-link theme="warning" hover="color" @click="handleRule(row)">规则</t-link>
            <t-link theme="danger" hover="color" @click="handleDelete(row)">删除</t-link>
          </t-space>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/编辑弹窗 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="700px"
      :confirm-btn="{ content: '确定', loading: submitLoading }"
      @confirm="handleSubmit"
      @close="handleDialogClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="110px">
        <t-row :gutter="[16, 0]">
          <t-col :span="6">
            <t-form-item label="优惠券名称" name="couponName">
              <t-input v-model="formData.couponName" placeholder="请输入优惠券名称" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="优惠券类型" name="couponType">
              <t-select v-model="formData.couponType" placeholder="请选择优惠券类型">
                <t-option value="FULL_REDUCTION" label="满减券" />
                <t-option value="CASH" label="现金券" />
              </t-select>
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="[16, 0]">
          <t-col :span="6">
            <t-form-item label="优惠金额" name="amount">
              <t-input-number v-model="formData.amount" placeholder="请输入优惠金额" :min="0" :decimal-places="2" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="使用门槛" name="conditionAmount">
              <t-input-number v-model="formData.conditionAmount" placeholder="满多少可用" :min="0" :decimal-places="2" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-form-item label="有效期" name="timeRange">
          <t-date-range-picker v-model="formData.timeRange" clearable />
        </t-form-item>
        <t-row :gutter="[16, 0]">
          <t-col :span="6">
            <t-form-item label="适用范围" name="rangeType">
              <t-select v-model="formData.rangeType" placeholder="请选择适用范围">
                <t-option value="ALL" label="通用" />
                <t-option value="SKU" label="SKU" />
                <t-option value="CATEGORY" label="分类" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="发布状态" name="publishStatus">
              <t-radio-group v-model="formData.publishStatus">
                <t-radio :value="true">已发布</t-radio>
                <t-radio :value="false">未发布</t-radio>
              </t-radio-group>
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="[16, 0]">
          <t-col :span="6">
            <t-form-item label="发放总量" name="publishCount">
              <t-input-number v-model="formData.publishCount" placeholder="-1 表示不限" :min="-1" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="每人限领" name="perLimit">
              <t-input-number v-model="formData.perLimit" placeholder="请输入每人限领数量" :min="1" />
            </t-form-item>
          </t-col>
        </t-row>
        <t-form-item label="范围描述" name="rangeDesc">
          <t-textarea v-model="formData.rangeDesc" placeholder="请输入范围描述" :rows="2" />
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
import { couponApi } from '@/api/marketing/coupon';
import dayjs from 'dayjs';

// ==================== 枚举映射 ====================
const couponTypeMap: Record<string, string> = {
  FULL_REDUCTION: '满减券',
  CASH: '现金券',
};

const rangeTypeMap: Record<string, string> = {
  ALL: '通用',
  SKU: 'SKU',
  CATEGORY: '分类',
};

// ==================== 表格 ====================
const columns = [
  { colKey: 'id', title: '券ID', width: 80 },
  { colKey: 'couponName', title: '优惠券名称', width: 160, ellipsis: true },
  { colKey: 'couponType', title: '券类型', width: 100 },
  { colKey: 'amount', title: '优惠金额', width: 100 },
  { colKey: 'condition', title: '使用门槛', width: 130 },
  { colKey: 'validTime', title: '有效期', width: 220 },
  { colKey: 'couponStatus', title: '有效状态', width: 100 },
  { colKey: 'rangeType', title: '适用范围', width: 100 },
  { colKey: 'publishCount', title: '发放总量', width: 100 },
  { colKey: 'perLimit', title: '每人限领', width: 100 },
  { colKey: 'publishStatus', title: '发布状态', width: 100 },
  { colKey: 'createTime', title: '创建时间', width: 170 },
  { colKey: 'operation', title: '操作', width: 170, fixed: 'right' },
];

const couponList = ref([]);
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
  couponName: '',
  couponType: '',
  rangeType: '',
  publishStatus: undefined as boolean | undefined,
});

// ==================== 弹窗 ====================
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const dialogTitle = computed(() => (isEdit.value ? '编辑优惠券' : '新增优惠券'));

// ==================== 表单 ====================
const formData = reactive({
  id: undefined as number | undefined,
  couponName: '',
  couponType: 'FULL_REDUCTION',
  amount: undefined as number | undefined,
  conditionAmount: undefined as number | undefined,
  timeRange: [] as any[],
  rangeType: 'ALL',
  rangeDesc: '',
  publishCount: -1,
  perLimit: 1,
  publishStatus: false,
});

const formRules = {
  couponName: [{ required: true, message: '请输入优惠券名称', type: 'error' }],
  couponType: [{ required: true, message: '请选择优惠券类型', type: 'error' }],
  amount: [{ required: true, message: '请输入优惠金额', type: 'error' }],
  conditionAmount: [{ required: true, message: '请输入使用门槛', type: 'error' }],
};

// ==================== 删除 ====================
const deleteVisible = ref(false);
const deleteTarget = ref<Record<string, any>>({});
const deleteBody = computed(() => {
  return `确定要删除优惠券「${deleteTarget.value.couponName}」吗？删除后不可恢复！`;
});

// ==================== 辅助 ====================
const resetFormData = () => {
  formData.id = undefined;
  formData.couponName = '';
  formData.couponType = 'FULL_REDUCTION';
  formData.amount = undefined;
  formData.conditionAmount = undefined;
  formData.timeRange = [];
  formData.rangeType = 'ALL';
  formData.rangeDesc = '';
  formData.publishCount = -1;
  formData.perLimit = 1;
  formData.publishStatus = false;
  formRef.value?.clearValidate();
};

const parseTimeRange = (timeRange: any[]) => {
  if (!timeRange || timeRange.length < 2) return { startTime: undefined, endTime: undefined };
  return {
    startTime: timeRange[0] ? dayjs(timeRange[0]).format('YYYY-MM-DD') : undefined,
    endTime: timeRange[1] ? dayjs(timeRange[1]).format('YYYY-MM-DD') : undefined,
  };
};

const getCouponStatus = (row: Record<string, any>) => {
  const now = dayjs();
  const start = row.startTime ? dayjs(row.startTime) : null;
  const end = row.endTime ? dayjs(row.endTime) : null;

  if (!start || !end) return { label: '未知', theme: 'default' };
  if (now.isBefore(start)) return { label: '未开始', theme: 'primary' };
  if (now.isAfter(end)) return { label: '已过期', theme: 'danger' };
  return { label: '有效', theme: 'success' };
};

// ==================== 数据加载 ====================
const buildParams = () => {
  return {
    couponName: searchForm.couponName,
    couponType: searchForm.couponType,
    rangeType: searchForm.rangeType,
    publishStatus: searchForm.publishStatus,
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
  };
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await couponApi.getList(buildParams());
    if (res?.list && Array.isArray(res.list)) {
      couponList.value = res.list;
      pagination.total = res.total ?? res.list.length;
    } else if (Array.isArray(res)) {
      couponList.value = res;
      pagination.total = res.length;
    } else if (res?.data && Array.isArray(res.data)) {
      couponList.value = res.data;
      pagination.total = res.total ?? res.data.length;
    } else {
      couponList.value = [];
      pagination.total = 0;
    }
  } catch (error) {
    console.error('获取优惠券列表失败:', error);
    MessagePlugin.error('获取优惠券列表失败');
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
  searchForm.couponName = '';
  searchForm.couponType = '';
  searchForm.rangeType = '';
  searchForm.publishStatus = undefined;
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
  formData.id = row.id;
  formData.couponName = row.couponName || '';
  formData.couponType = row.couponType || 'FULL_REDUCTION';
  formData.amount = row.amount;
  formData.conditionAmount = row.conditionAmount;
  formData.rangeType = row.rangeType || 'ALL';
  formData.rangeDesc = row.rangeDesc || '';
  formData.publishCount = row.publishCount ?? -1;
  formData.perLimit = row.perLimit ?? 1;
  formData.publishStatus = !!row.publishStatus;
  if (row.startTime && row.endTime) {
    formData.timeRange = [row.startTime, row.endTime];
  }
  dialogVisible.value = true;
};

const handleRule = (row: Record<string, any>) => {
  MessagePlugin.info(`优惠券规则配置暂未实现，券ID：${row.id}`);
};

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row;
  deleteVisible.value = true;
};

const handleDeleteConfirm = async () => {
  try {
    const res = await couponApi.delete(deleteTarget.value.id);
    if (res.code === 200) {
      MessagePlugin.success('删除成功');
      deleteVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '删除失败');
    }
  } catch (error) {
    console.error('删除优惠券失败:', error);
    MessagePlugin.error('删除失败');
  }
};

const handleSubmit = async () => {
  const validateResult = await formRef.value?.validate();
  if (validateResult !== true) return;

  submitLoading.value = true;
  try {
    const { startTime, endTime } = parseTimeRange(formData.timeRange);
    const submitData = {
      id: formData.id,
      couponName: formData.couponName,
      couponType: formData.couponType,
      amount: formData.amount,
      conditionAmount: formData.conditionAmount,
      startTime,
      endTime,
      rangeType: formData.rangeType,
      rangeDesc: formData.rangeDesc,
      publishCount: formData.publishCount,
      perLimit: formData.perLimit,
      publishStatus: formData.publishStatus,
    };

    const api = isEdit.value ? couponApi.update : couponApi.add;
    const res = await api(submitData);
    if (res.code === 200) {
      MessagePlugin.success(isEdit.value ? '修改成功' : '创建成功');
      dialogVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '操作失败');
    }
  } catch (error) {
    console.error('保存优惠券失败:', error);
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
  fetchData();
});
</script>

<style scoped lang="less">
.coupon-container {
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
}
</style>
