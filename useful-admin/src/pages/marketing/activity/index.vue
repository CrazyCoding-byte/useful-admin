<template>
  <div class="activity-container">
    <!-- 搜索表单 -->
    <t-card class="search-card" :bordered="false">
      <t-form :data="searchForm" label-width="80px" @submit="handleSearch" @reset="handleReset">
        <t-row :gutter="[16, 16]">
          <t-col :span="5">
            <t-form-item label="活动名称" name="activityName">
              <t-input v-model="searchForm.activityName" placeholder="请输入活动名称" clearable />
            </t-form-item>
          </t-col>
          <t-col :span="5">
            <t-form-item label="活动类型" name="activityType">
              <t-select v-model="searchForm.activityType" placeholder="请选择活动类型" clearable>
                <t-option value="FULL_REDUCTION" label="满减" />
                <t-option value="FULL_DISCOUNT" label="满量打折" />
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="8">
            <t-form-item label="活动时间" name="timeRange">
              <t-date-range-picker v-model="searchForm.timeRange" clearable />
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
          新增活动
        </t-button>
      </div>

      <t-table
        :data="activityList"
        :columns="columns"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        hover
        stripe
        @page-change="onPageChange"
      >
        <template #activityType="{ row }">
          <t-tag :theme="row.activityType === 'FULL_REDUCTION' ? 'primary' : 'warning'" variant="light">
            {{ activityTypeMap[row.activityType] || row.activityType }}
          </t-tag>
        </template>
        <template #activityTime="{ row }">
          <span>{{ row.startTime || '-' }} ~ {{ row.endTime || '-' }}</span>
        </template>
        <template #activityStatus="{ row }">
          <t-tag :theme="getActivityStatus(row).theme" variant="light">
            {{ getActivityStatus(row).label }}
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
      width="600px"
      :confirm-btn="{ content: '确定', loading: submitLoading }"
      @confirm="handleSubmit"
      @close="handleDialogClose"
    >
      <t-form ref="formRef" :data="formData" :rules="formRules" label-width="100px">
        <t-form-item label="活动名称" name="activityName">
          <t-input v-model="formData.activityName" placeholder="请输入活动名称" />
        </t-form-item>
        <t-form-item label="活动类型" name="activityType">
          <t-radio-group v-model="formData.activityType">
            <t-radio value="FULL_REDUCTION">满减</t-radio>
            <t-radio value="FULL_DISCOUNT">满量打折</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="活动时间" name="timeRange">
          <t-date-range-picker v-model="formData.timeRange" clearable />
        </t-form-item>
        <t-form-item label="活动描述" name="activityDesc">
          <t-textarea v-model="formData.activityDesc" placeholder="请输入活动描述" :rows="3" />
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
import { activityApi } from '@/api/marketing/activity';
import dayjs from 'dayjs';

// ==================== 枚举映射 ====================
const activityTypeMap: Record<string, string> = {
  FULL_REDUCTION: '满减',
  FULL_DISCOUNT: '满量打折',
};

// ==================== 表格 ====================
const columns = [
  { colKey: 'id', title: '活动ID', width: 80 },
  { colKey: 'activityName', title: '活动名称', width: 180, ellipsis: true },
  { colKey: 'activityType', title: '活动类型', width: 110 },
  { colKey: 'activityTime', title: '活动时间', width: 220 },
  { colKey: 'activityStatus', title: '活动状态', width: 110 },
  { colKey: 'activityDesc', title: '活动描述', width: 200, ellipsis: true },
  { colKey: 'createTime', title: '创建时间', width: 170 },
  { colKey: 'operation', title: '操作', width: 170, fixed: 'right' },
];

const activityList = ref([]);
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
  activityName: '',
  activityType: '',
  timeRange: [] as any[],
});

// ==================== 弹窗 ====================
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstanceFunctions>();
const dialogTitle = computed(() => (isEdit.value ? '编辑活动' : '新增活动'));

// ==================== 表单 ====================
const formData = reactive({
  id: undefined as number | undefined,
  activityName: '',
  activityType: 'FULL_REDUCTION',
  timeRange: [] as any[],
  activityDesc: '',
});

const formRules = {
  activityName: [{ required: true, message: '请输入活动名称', type: 'error' }],
  activityType: [{ required: true, message: '请选择活动类型', type: 'error' }],
};

// ==================== 删除 ====================
const deleteVisible = ref(false);
const deleteTarget = ref<Record<string, any>>({});
const deleteBody = computed(() => {
  return `确定要删除活动「${deleteTarget.value.activityName}」吗？删除后不可恢复！`;
});

// ==================== 辅助 ====================
const resetFormData = () => {
  formData.id = undefined;
  formData.activityName = '';
  formData.activityType = 'FULL_REDUCTION';
  formData.timeRange = [];
  formData.activityDesc = '';
  formRef.value?.clearValidate();
};

const parseTimeRange = (timeRange: any[]) => {
  if (!timeRange || timeRange.length < 2) return { startTime: undefined, endTime: undefined };
  return {
    startTime: timeRange[0] ? dayjs(timeRange[0]).format('YYYY-MM-DD') : undefined,
    endTime: timeRange[1] ? dayjs(timeRange[1]).format('YYYY-MM-DD') : undefined,
  };
};

const getActivityStatus = (row: Record<string, any>) => {
  const now = dayjs();
  const start = row.startTime ? dayjs(row.startTime) : null;
  const end = row.endTime ? dayjs(row.endTime) : null;

  if (!start || !end) return { label: '未知', theme: 'default' };
  if (now.isBefore(start)) return { label: '未开始', theme: 'primary' };
  if (now.isAfter(end)) return { label: '已结束', theme: 'default' };
  return { label: '进行中', theme: 'success' };
};

// ==================== 数据加载 ====================
const buildParams = () => {
  const { startTime, endTime } = parseTimeRange(searchForm.timeRange);
  return {
    activityName: searchForm.activityName,
    activityType: searchForm.activityType,
    startTimeBegin: startTime,
    startTimeEnd: endTime,
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
  };
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await activityApi.getList(buildParams());
    if (res?.list && Array.isArray(res.list)) {
      activityList.value = res.list;
      pagination.total = res.total ?? res.list.length;
    } else if (Array.isArray(res)) {
      activityList.value = res;
      pagination.total = res.length;
    } else if (res?.data && Array.isArray(res.data)) {
      activityList.value = res.data;
      pagination.total = res.total ?? res.data.length;
    } else {
      activityList.value = [];
      pagination.total = 0;
    }
  } catch (error) {
    console.error('获取活动列表失败:', error);
    MessagePlugin.error('获取活动列表失败');
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
  searchForm.activityName = '';
  searchForm.activityType = '';
  searchForm.timeRange = [];
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
  formData.activityName = row.activityName || '';
  formData.activityType = row.activityType || 'FULL_REDUCTION';
  formData.activityDesc = row.activityDesc || '';
  if (row.startTime && row.endTime) {
    formData.timeRange = [row.startTime, row.endTime];
  }
  dialogVisible.value = true;
};

const handleRule = (row: Record<string, any>) => {
  MessagePlugin.info(`活动规则配置暂未实现，活动ID：${row.id}`);
};

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row;
  deleteVisible.value = true;
};

const handleDeleteConfirm = async () => {
  try {
    const res = await activityApi.delete(deleteTarget.value.id);
    if (res.code === 200) {
      MessagePlugin.success('删除成功');
      deleteVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '删除失败');
    }
  } catch (error) {
    console.error('删除活动失败:', error);
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
      activityName: formData.activityName,
      activityType: formData.activityType,
      startTime,
      endTime,
      activityDesc: formData.activityDesc,
    };

    const api = isEdit.value ? activityApi.update : activityApi.add;
    const res = await api(submitData);
    if (res.code === 200) {
      MessagePlugin.success(isEdit.value ? '修改成功' : '创建成功');
      dialogVisible.value = false;
      fetchData();
    } else {
      MessagePlugin.error(res.msg || '操作失败');
    }
  } catch (error) {
    console.error('保存活动失败:', error);
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
.activity-container {
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
