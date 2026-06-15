<template>
  <div class="dict-page">
    <div class="dict-container">
      <!-- 左侧字典类型 -->
      <t-card class="dict-type-card" :bordered="false" title="字典管理">
        <div class="dict-type-search">
          <t-input
            v-model="dictTypeSearchForm.dictName"
            placeholder="请输入字典名称"
            clearable
          />
          <t-input
            v-model="dictTypeSearchForm.dictType"
            placeholder="请输入字典类型"
            clearable
          />
          <t-date-range-picker
            v-model="dictTypeSearchForm.createTime"
            placeholder="开始日期 - 结束日期"
          />
          <div class="dict-type-buttons">
            <t-button theme="primary" @click="getDictTypeList">
              <template #icon><t-icon name="search" /></template>
              搜索
            </t-button>
            <t-button variant="outline" @click="resetDictTypeSearch">
              <template #icon><t-icon name="refresh" /></template>
              重置
            </t-button>
          </div>
        </div>
        <div class="dict-type-operations">
          <t-button theme="primary" size="small" @click="handleAddDictType">
            <template #icon><t-icon name="add" /></template>
            新增
          </t-button>
          <t-button theme="warning" size="small" :disabled="!selectedDictTypeKeys.length" @click="handleBatchEditDictType">
            <template #icon><t-icon name="edit" /></template>
            修改
          </t-button>
          <t-button theme="danger" size="small" :disabled="!selectedDictTypeKeys.length" @click="handleBatchDeleteDictType">
            <template #icon><t-icon name="delete" /></template>
            删除
          </t-button>
          <t-button variant="outline" size="small">
            <template #icon><t-icon name="download" /></template>
            导出
          </t-button>
          <t-button variant="outline" size="small" @click="handleRefreshCache">
            <template #icon><t-icon name="refresh" /></template>
            刷新缓存
          </t-button>
        </div>
        <t-table
          :data="dictTypeList"
          :columns="dictTypeColumns"
          :loading="dictTypeLoading"
          :pagination="dictTypePagination"
          row-key="dictId"
          v-model:selected-row-keys="selectedDictTypeKeys"
          @page-change="handleDictTypePageChange"
          @row-click="handleDictTypeClick"
          highlight-current-row
        >
          <template #status="{ row }">
            <t-tag :theme="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </t-tag>
          </template>
          <template #op="{ row }">
            <div class="operation-icons">
              <t-button theme="warning" variant="text" size="small" @click.stop="handleEditDictType(row)">
                <t-icon name="edit" />
              </t-button>
              <t-button theme="danger" variant="text" size="small" @click.stop="handleDeleteDictType(row)">
                <t-icon name="delete" />
              </t-button>
            </div>
          </template>
        </t-table>
      </t-card>

      <!-- 右侧字典数据 -->
      <t-card class="dict-data-card" :bordered="false" :title="currentDictTypeTitle">
        <div class="dict-data-search">
          <t-input
            v-model="dictDataSearchForm.dictLabel"
            placeholder="请输入字典标签"
            clearable
          />
          <div class="dict-data-buttons">
            <t-button theme="primary" @click="getDictDataList">
              <template #icon><t-icon name="search" /></template>
              搜索
            </t-button>
            <t-button variant="outline" @click="resetDictDataSearch">
              <template #icon><t-icon name="refresh" /></template>
              重置
            </t-button>
          </div>
        </div>
        <div class="dict-data-operations">
          <t-button theme="primary" size="small" @click="handleAddDictData">
            <template #icon><t-icon name="add" /></template>
            新增
          </t-button>
          <t-button theme="warning" size="small" :disabled="!selectedDictDataKeys.length" @click="handleBatchEditDictData">
            <template #icon><t-icon name="edit" /></template>
            修改
          </t-button>
          <t-button theme="danger" size="small" :disabled="!selectedDictDataKeys.length" @click="handleBatchDeleteDictData">
            <template #icon><t-icon name="delete" /></template>
            删除
          </t-button>
          <t-button variant="outline" size="small">
            <template #icon><t-icon name="download" /></template>
            导出
          </t-button>
        </div>
        <t-table
          :data="dictDataList"
          :columns="dictDataColumns"
          :loading="dictDataLoading"
          :pagination="dictDataPagination"
          row-key="dictCode"
          v-model:selected-row-keys="selectedDictDataKeys"
          @page-change="handleDictDataPageChange"
        >
          <template #listClass="{ row }">
            <t-tag :theme="getTagTheme(row.listClass)">
              {{ row.dictLabel }}
            </t-tag>
          </template>
          <template #status="{ row }">
            <t-tag :theme="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </t-tag>
          </template>
          <template #op="{ row }">
            <div class="operation-icons">
              <t-button theme="warning" variant="text" size="small" @click="handleEditDictData(row)">
                <t-icon name="edit" />
              </t-button>
              <t-button theme="danger" variant="text" size="small" @click="handleDeleteDictData(row)">
                <t-icon name="delete" />
              </t-button>
            </div>
          </template>
        </t-table>
      </t-card>
    </div>

    <!-- 字典类型对话框 -->
    <t-dialog
      v-model:visible="dictTypeDialogVisible"
      :header="dictTypeForm.dictId ? '修改字典类型' : '新增字典类型'"
      width="500px"
    >
      <t-form
        :data="dictTypeForm"
        :rules="dictTypeFormRules"
        ref="dictTypeFormRef"
        label-width="100px"
      >
        <t-form-item label="字典名称" name="dictName">
          <t-input v-model="dictTypeForm.dictName" placeholder="请输入字典名称"/>
        </t-form-item>
        <t-form-item label="字典类型" name="dictType">
          <t-input v-model="dictTypeForm.dictType" placeholder="请输入字典类型"/>
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-radio-group v-model="dictTypeForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="备注" name="remark">
          <t-textarea v-model="dictTypeForm.remark" placeholder="请输入备注" :rows="3" />
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closeDictTypeDialog">取消</t-button>
        <t-button theme="primary" @click="submitDictTypeForm">确定</t-button>
      </template>
    </t-dialog>

    <!-- 字典数据对话框 -->
    <t-dialog
      v-model:visible="dictDataDialogVisible"
      :header="dictDataForm.dictCode ? '修改字典数据' : '新增字典数据'"
      width="500px"
    >
      <t-form
        :data="dictDataForm"
        :rules="dictDataFormRules"
        ref="dictDataFormRef"
        label-width="100px"
      >
        <t-form-item label="字典类型">
          <t-input v-model="currentDictType.dictType" disabled />
        </t-form-item>
        <t-form-item label="数据标签" name="dictLabel">
          <t-input v-model="dictDataForm.dictLabel" placeholder="请输入数据标签"/>
        </t-form-item>
        <t-form-item label="数据键值" name="dictValue">
          <t-input v-model="dictDataForm.dictValue" placeholder="请输入数据键值"/>
        </t-form-item>
        <t-form-item label="显示排序" name="dictSort">
          <t-input-number v-model="dictDataForm.dictSort" :min="0" />
        </t-form-item>
        <t-form-item label="回显样式" name="listClass">
          <t-select v-model="dictDataForm.listClass" placeholder="请选择回显样式">
            <t-option label="默认" value="default" />
            <t-option label="主要" value="primary" />
            <t-option label="成功" value="success" />
            <t-option label="警告" value="warning" />
            <t-option label="危险" value="danger" />
          </t-select>
        </t-form-item>
        <t-form-item label="是否默认" name="isDefault">
          <t-radio-group v-model="dictDataForm.isDefault">
            <t-radio value="Y">是</t-radio>
            <t-radio value="N">否</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-radio-group v-model="dictDataForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="备注" name="remark">
          <t-textarea v-model="dictDataForm.remark" placeholder="请输入备注" :rows="3" />
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closeDictDataDialog">取消</t-button>
        <t-button theme="primary" @click="submitDictDataForm">确定</t-button>
      </template>
    </t-dialog>

    <!-- 确认删除对话框 -->
    <t-dialog
      v-model:visible="confirmVisible"
      header="确认删除"
    >
      <div>{{ confirmBody }}</div>
      <template #footer>
        <t-button @click="confirmVisible = false">取消</t-button>
        <t-button theme="danger" @click="onConfirmDelete">确认</t-button>
      </template>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { dictTypeApi, dictDataApi, type SysDictType, type SysDictData } from '@/api/system/dict';
import { MessagePlugin } from 'tdesign-vue-next';
import type { PaginationProps, TableProps } from 'tdesign-vue-next';

// ==================== 字典类型 ====================
const dictTypeSearchForm = ref<Partial<SysDictType> & { createTime?: any }>({
  dictName: '',
  dictType: '',
  createTime: [],
});

const dictTypeList = ref<SysDictType[]>([]);
const dictTypeLoading = ref(false);
const selectedDictTypeKeys = ref<number[]>([]);
const currentDictType = ref<Partial<SysDictType>>({});
const currentDictTypePage = ref(1);
const dictTypePageSize = ref(10);
const dictTypeTotal = ref(0);

const dictTypeColumns: TableProps['columns'] = [
  { colKey: 'row-select', type: 'multiple', width: 46 },
  { colKey: 'dictName', title: '字典名称', width: 150 },
  { colKey: 'dictType', title: '字典类型', width: 180 },
  { colKey: 'status', title: '状态', width: 80, align: 'center' },
  { colKey: 'createTime', title: '创建时间', width: 180 },
  { colKey: 'op', title: '操作', width: 100, align: 'center', fixed: 'right' },
];

const dictTypePagination = computed(() => ({
  pageSize: dictTypePageSize.value,
  current: currentDictTypePage.value,
  total: dictTypeTotal.value,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
}));

const currentDictTypeTitle = computed(() => {
  if (currentDictType.value.dictType) {
    return `字典数据 / ${currentDictType.value.dictName} / ${currentDictType.value.dictType}`;
  }
  return '字典数据';
});

const getDictTypeList = async () => {
  dictTypeLoading.value = true;
  try {
    const response = await dictTypeApi.getDictTypeList({
      ...dictTypeSearchForm.value,
      pageNum: currentDictTypePage.value,
      pageSize: dictTypePageSize.value,
    });
    const data = response.data || response;
    dictTypeList.value = data.records || [];
    dictTypeTotal.value = data.total || 0;
  } catch (error) {
    MessagePlugin.error('获取字典类型列表失败');
    dictTypeList.value = [];
  } finally {
    dictTypeLoading.value = false;
  }
};

const resetDictTypeSearch = () => {
  dictTypeSearchForm.value = {
    dictName: '',
    dictType: '',
    createTime: [],
  };
  getDictTypeList();
};

const handleDictTypePageChange = (pageInfo: PaginationProps) => {
  currentDictTypePage.value = pageInfo.current || 1;
  dictTypePageSize.value = pageInfo.pageSize || 10;
  getDictTypeList();
};

const handleDictTypeClick = (row: SysDictType) => {
  currentDictType.value = row;
  getDictDataList();
};

// ==================== 字典数据 ====================
const dictDataSearchForm = ref<Partial<SysDictData>>({
  dictLabel: '',
});

const dictDataList = ref<SysDictData[]>([]);
const dictDataLoading = ref(false);
const selectedDictDataKeys = ref<number[]>([]);
const currentDictDataPage = ref(1);
const dictDataPageSize = ref(10);
const dictDataTotal = ref(0);

const dictDataColumns: TableProps['columns'] = [
  { colKey: 'row-select', type: 'multiple', width: 46 },
  { colKey: 'dictLabel', title: '字典标签', width: 120 },
  { colKey: 'dictValue', title: '字典键值', width: 100 },
  { colKey: 'dictSort', title: '字典排序', width: 100, align: 'center' },
  { colKey: 'listClass', title: '回显样式', width: 100 },
  { colKey: 'status', title: '状态', width: 80, align: 'center' },
  { colKey: 'createTime', title: '创建时间', width: 180 },
  { colKey: 'op', title: '操作', width: 100, align: 'center', fixed: 'right' },
];

const dictDataPagination = computed(() => ({
  pageSize: dictDataPageSize.value,
  current: currentDictDataPage.value,
  total: dictDataTotal.value,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
}));

const getDictDataList = async () => {
  if (!currentDictType.value.dictType) {
    dictDataList.value = [];
    return;
  }
  dictDataLoading.value = true;
  try {
    const response = await dictDataApi.getDictDataList({
      dictType: currentDictType.value.dictType,
      dictLabel: dictDataSearchForm.value.dictLabel,
      pageNum: currentDictDataPage.value,
      pageSize: dictDataPageSize.value,
    });
    const data = response.data || response;
    dictDataList.value = data.records || [];
    dictDataTotal.value = data.total || 0;
  } catch (error) {
    MessagePlugin.error('获取字典数据列表失败');
    dictDataList.value = [];
  } finally {
    dictDataLoading.value = false;
  }
};

const resetDictDataSearch = () => {
  dictDataSearchForm.value = { dictLabel: '' };
  getDictDataList();
};

const handleDictDataPageChange = (pageInfo: PaginationProps) => {
  currentDictDataPage.value = pageInfo.current || 1;
  dictDataPageSize.value = pageInfo.pageSize || 10;
  getDictDataList();
};

const getTagTheme = (listClass?: string) => {
  const themeMap: Record<string, string> = {
    'default': 'default',
    'primary': 'primary',
    'success': 'success',
    'warning': 'warning',
    'danger': 'danger',
  };
  return themeMap[listClass || 'default'] || 'default';
};

// ==================== 字典类型操作 ====================
const dictTypeDialogVisible = ref(false);
const dictTypeFormRef = ref<any>();
const dictTypeForm = ref<Partial<SysDictType>>({
  dictId: undefined,
  dictName: '',
  dictType: '',
  status: '0',
  remark: '',
});

const dictTypeFormRules = ref({
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
});

const handleAddDictType = () => {
  dictTypeForm.value = { dictId: undefined, dictName: '', dictType: '', status: '0', remark: '' };
  dictTypeDialogVisible.value = true;
};

const handleEditDictType = (row: SysDictType) => {
  dictTypeForm.value = { ...row };
  dictTypeDialogVisible.value = true;
};

const handleBatchEditDictType = () => {
  if (selectedDictTypeKeys.value.length === 1) {
    const row = dictTypeList.value.find(d => d.dictId === selectedDictTypeKeys.value[0]);
    if (row) handleEditDictType(row);
  } else {
    MessagePlugin.warning('请选择一条记录进行修改');
  }
};

const closeDictTypeDialog = () => {
  dictTypeDialogVisible.value = false;
};

const submitDictTypeForm = async () => {
  if (!dictTypeFormRef.value) return;
  try {
    await dictTypeFormRef.value.validate();
    if (dictTypeForm.value.dictId) {
      await dictTypeApi.updateDictType(dictTypeForm.value);
      MessagePlugin.success('修改字典类型成功');
    } else {
      await dictTypeApi.addDictType(dictTypeForm.value);
      MessagePlugin.success('新增字典类型成功');
    }
    dictTypeDialogVisible.value = false;
    getDictTypeList();
  } catch (error) {
    console.error(error);
  }
};

const handleRefreshCache = async () => {
  try {
    await dictTypeApi.refreshCache();
    MessagePlugin.success('刷新缓存成功');
  } catch (error) {
    MessagePlugin.error('刷新缓存失败');
  }
};

// ==================== 字典数据操作 ====================
const dictDataDialogVisible = ref(false);
const dictDataFormRef = ref<any>();
const dictDataForm = ref<Partial<SysDictData>>({
  dictCode: undefined,
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  listClass: 'default',
  isDefault: 'N',
  status: '0',
  remark: '',
});

const dictDataFormRules = ref({
  dictLabel: [{ required: true, message: '请输入数据标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入数据键值', trigger: 'blur' }],
  dictSort: [{ required: true, message: '请输入显示排序', trigger: 'blur' }],
});

const handleAddDictData = () => {
  if (!currentDictType.value.dictType) {
    MessagePlugin.warning('请先选择字典类型');
    return;
  }
  dictDataForm.value = {
    dictCode: undefined,
    dictLabel: '',
    dictValue: '',
    dictSort: 0,
    listClass: 'default',
    isDefault: 'N',
    status: '0',
    remark: '',
  };
  dictDataDialogVisible.value = true;
};

const handleEditDictData = (row: SysDictData) => {
  dictDataForm.value = { ...row };
  dictDataDialogVisible.value = true;
};

const handleBatchEditDictData = () => {
  if (selectedDictDataKeys.value.length === 1) {
    const row = dictDataList.value.find(d => d.dictCode === selectedDictDataKeys.value[0]);
    if (row) handleEditDictData(row);
  } else {
    MessagePlugin.warning('请选择一条记录进行修改');
  }
};

const closeDictDataDialog = () => {
  dictDataDialogVisible.value = false;
};

const submitDictDataForm = async () => {
  if (!dictDataFormRef.value) return;
  try {
    await dictDataFormRef.value.validate();
    const data = { ...dictDataForm.value, dictType: currentDictType.value.dictType };
    if (dictDataForm.value.dictCode) {
      await dictDataApi.updateDictData(data);
      MessagePlugin.success('修改字典数据成功');
    } else {
      await dictDataApi.addDictData(data);
      MessagePlugin.success('新增字典数据成功');
    }
    dictDataDialogVisible.value = false;
    getDictDataList();
  } catch (error) {
    console.error(error);
  }
};

// ==================== 删除操作 ====================
const confirmVisible = ref(false);
const deleteType = ref<'type' | 'data'>('type');
const deleteIds = ref<number[]>([]);
const confirmBody = computed(() => {
  const typeName = deleteType.value === 'type' ? '字典类型' : '字典数据';
  return `确定要删除选中的 ${deleteIds.value.length} 个${typeName}吗？删除后无法恢复！`;
});

const handleDeleteDictType = (row: SysDictType) => {
  deleteType.value = 'type';
  deleteIds.value = [row.dictId!];
  confirmVisible.value = true;
};

const handleBatchDeleteDictType = () => {
  if (selectedDictTypeKeys.value.length > 0) {
    deleteType.value = 'type';
    deleteIds.value = [...selectedDictTypeKeys.value];
    confirmVisible.value = true;
  }
};

const handleDeleteDictData = (row: SysDictData) => {
  deleteType.value = 'data';
  deleteIds.value = [row.dictCode!];
  confirmVisible.value = true;
};

const handleBatchDeleteDictData = () => {
  if (selectedDictDataKeys.value.length > 0) {
    deleteType.value = 'data';
    deleteIds.value = [...selectedDictDataKeys.value];
    confirmVisible.value = true;
  }
};

const onConfirmDelete = async () => {
  try {
    if (deleteType.value === 'type') {
      await dictTypeApi.deleteDictType(deleteIds.value);
      selectedDictTypeKeys.value = [];
      getDictTypeList();
    } else {
      await dictDataApi.deleteDictData(deleteIds.value);
      selectedDictDataKeys.value = [];
      getDictDataList();
    }
    MessagePlugin.success('删除成功');
    confirmVisible.value = false;
  } catch (error) {
    MessagePlugin.error('删除失败');
  }
};

onMounted(() => {
  getDictTypeList();
});
</script>

<style lang="less" scoped>
.dict-page {
  padding: 16px;
  background-color: var(--td-bg-color-page);
  height: calc(100vh - 64px);

  .dict-container {
    display: flex;
    gap: 16px;
    height: 100%;

    .dict-type-card {
      flex: 1;
      display: flex;
      flex-direction: column;

      :deep(.t-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        padding: 16px;
      }

      .dict-type-search {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 16px;

        .dict-type-buttons {
          display: flex;
          gap: 8px;
        }
      }

      .dict-type-operations {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
      }

      :deep(.t-table) {
        flex: 1;
      }
    }

    .dict-data-card {
      flex: 1;
      display: flex;
      flex-direction: column;

      :deep(.t-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        padding: 16px;
      }

      .dict-data-search {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;

        .dict-data-buttons {
          display: flex;
          gap: 8px;
        }
      }

      .dict-data-operations {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
      }

      :deep(.t-table) {
        flex: 1;
      }
    }
  }

  .operation-icons {
    display: flex;
    justify-content: center;
    gap: 8px;
  }
}
</style>
