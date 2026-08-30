<template>
  <div class="shop-product-list">
    <!-- 搜索表单 -->
    <t-form
      ref="form"
      :data="formData"
      :label-width="80"
      colon
      :style="{ marginBottom: '8px' }"
      @reset="onReset"
      @submit="onSubmit"
    >
      <t-row>
        <t-col :span="10">
          <t-row :gutter="[16, 24]">
            <t-col :span="4">
              <t-form-item label="商品名称" name="spuName">
                <t-input
                  v-model="formData.spuName"
                  class="form-item-content"
                  type="search"
                  placeholder="请输入商品名称"
                  :style="{ minWidth: '134px' }"
                />
              </t-form-item>
            </t-col>
            <t-col :span="4">
              <t-form-item label="商品状态" name="publishStatus">
                <t-select
                  v-model="formData.publishStatus"
                  class="form-item-content"
                  :options="STATUS_OPTIONS"
                  placeholder="请选择商品状态"
                />
              </t-form-item>
            </t-col>
          </t-row>
        </t-col>

        <t-col :span="2" class="operation-container">
          <t-button theme="primary" type="submit">查询</t-button>
          <t-button type="reset" variant="base" theme="default">重置</t-button>
        </t-col>
      </t-row>
    </t-form>

    <!-- 表格操作栏 -->
    <div class="table-operation">
      <t-button theme="primary" @click="handleAdd">
        <add-icon /> 新建商品
      </t-button>
      <t-button
        v-if="selectedRowKeys.length > 0"
        theme="danger"
        variant="outline"
        @click="handleBatchDelete"
      >
        <delete-icon /> 批量删除
      </t-button>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <t-table
        :data="data"
        :columns="COLUMNS"
        :row-key="rowKey"
        :vertical-align="verticalAlign"
        :hover="hover"
        :pagination="pagination"
        :loading="dataLoading"
        :header-affixed-top="headerAffixedTop"
        :selected-row-keys="selectedRowKeys"
        multiple
        @page-change="rehandlePageChange"
        @change="rehandleChange"
        @select-change="onSelectChange"
      >
        <template #status="{ row }">
          <t-tag v-if="row.publishStatus === 0" theme="default" variant="light">下架</t-tag>
          <t-tag v-if="row.publishStatus === 1" theme="success" variant="light">上架</t-tag>
        </template>
        <template #op="slotProps">
          <a class="t-button-link" @click="handleEdit(slotProps)">编辑</a>
          <a class="t-button-link" @click="handleClickDelete(slotProps)">删除</a>
        </template>
      </t-table>
    </div>

    <!-- 新增/编辑对话框 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="600px"
      :footer="true"
      @confirm="handleDialogConfirm"
    >
      <t-form
        ref="productForm"
        :data="productFormData"
        :rules="FORM_RULES"
        label-width="100px"
      >
        <t-form-item label="商品名称" name="spuName">
          <t-input v-model="productFormData.spuName" placeholder="请输入商品名称" />
        </t-form-item>
        <t-form-item label="商品描述" name="spuDescription">
          <t-textarea
            v-model="productFormData.spuDescription"
            placeholder="请输入商品描述"
            :rows="3"
          />
        </t-form-item>
        <t-form-item label="所属分类" name="catalogId">
          <t-input-number
            v-model="productFormData.catalogId"
            :min="0"
            placeholder="请输入分类 ID"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="品牌" name="brandId">
          <t-input-number
            v-model="productFormData.brandId"
            :min="0"
            placeholder="请输入品牌 ID"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="品牌名" name="brandName">
          <t-input v-model="productFormData.brandName" placeholder="请输入品牌名" />
        </t-form-item>
        <t-form-item label="重量" name="weight">
          <t-input-number
            v-model="productFormData.weight"
            :min="0"
            :step="0.1"
            placeholder="请输入商品重量"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="商品状态" name="publishStatus">
          <t-radio-group v-model="productFormData.publishStatus">
            <t-radio :value="0">下架</t-radio>
            <t-radio :value="1">上架</t-radio>
          </t-radio-group>
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 删除确认对话框 -->
    <t-dialog
      v-model:visible="confirmVisible"
      header="确认删除所选商品？"
      :body="confirmBody"
      :on-cancel="onCancel"
      @confirm="onConfirmDelete"
    />
  </div>
</template>

<script lang="ts">
export default {
  name: 'ShopProductList',
};
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { MessagePlugin, PrimaryTableCol, TableRowData, PageInfo, FormInstanceFunctions } from 'tdesign-vue-next';
import { AddIcon, DeleteIcon } from 'tdesign-icons-vue-next';
import { productApi } from '@/api/product';
import { useSettingStore } from '@/store';
import { prefix } from '@/config/global';
import type { Product } from '@/api/model/productModel';

const store = useSettingStore();

// 状态选项
const STATUS_OPTIONS = [
  { label: '下架', value: '0' },
  { label: '上架', value: '1' },
];

// 表格列定义
const COLUMNS: PrimaryTableCol<TableRowData>[] = [
  { type: 'multiple', width: 50, fixed: 'left' },
  {
    title: '商品 ID',
    colKey: 'id',
    width: 100,
    fixed: 'left',
  },
  {
    title: '商品名称',
    colKey: 'spuName',
    width: 200,
    ellipsis: true,
    align: 'left',
  },
  {
    title: '商品描述',
    colKey: 'spuDescription',
    width: 250,
    ellipsis: true,
  },
  {
    title: '品牌',
    colKey: 'brandName',
    width: 120,
  },
  {
    title: '重量',
    colKey: 'weight',
    width: 100,
  },
  {
    title: '商品状态',
    colKey: 'publishStatus',
    width: 100,
  },
  {
    title: '创建时间',
    colKey: 'createTime',
    width: 180,
    ellipsis: true,
  },
  {
    align: 'left',
    fixed: 'right',
    width: 200,
    colKey: 'op',
    title: '操作',
  },
];

// 搜索表单数据
const searchForm = {
  productName: '',
  status: '',
};

const formData = ref({ ...searchForm });
const rowKey = 'id';
const verticalAlign = 'top' as const;
const hover = true;

const pagination = ref({
  defaultPageSize: 10,
  total: 0,
  defaultCurrent: 1,
});

const data = ref<Product[]>([]);
const dataLoading = ref(false);

// 对话框相关
const dialogVisible = ref(false);
const confirmVisible = ref(false);
const dialogTitle = computed(() => (isEdit.value ? '编辑商品' : '新建商品'));
const isEdit = ref(false);
const deleteIdx = ref(-1);
const selectedRowKeys = ref<number[]>([]);

// 表单数据
const INITIAL_PRODUCT_DATA = {
  id: undefined,
  spuName: '',
  spuDescription: '',
  catalogId: undefined,
  brandId: undefined,
  brandName: '',
  weight: 0,
  publishStatus: 0,
};

const productFormData = ref({ ...INITIAL_PRODUCT_DATA });
const productForm = ref<FormInstanceFunctions>(null);

// 表单验证规则
const FORM_RULES = {
  spuName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  catalogId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
};

// 确认删除提示
const confirmBody = computed(() => {
  if (deleteIdx.value > -1) {
    const { spuName } = data.value[deleteIdx.value];
    return `删除后，${spuName}的所有商品信息将被清空，且无法恢复`;
  }
  if (selectedRowKeys.value.length > 0) {
    return `将删除 ${selectedRowKeys.value.length} 个商品，且无法恢复`;
  }
  return '';
});

// 获取数据
const fetchData = async () => {
  dataLoading.value = true;
  try {
    const params: any = {
      pageNum: pagination.value.defaultCurrent,
      pageSize: pagination.value.defaultPageSize,
    };
    
    // 添加搜索条件
    if (formData.value.spuName) {
      params.productName = formData.value.spuName;
    }
    if (formData.value.publishStatus !== '' && formData.value.publishStatus !== undefined) {
      params.status = String(formData.value.publishStatus);
    }
    
    const res = await productApi.getProductList(params);
    if (res && res.data) {
      data.value = res.data.list || [];
      pagination.value = {
        ...pagination.value,
        total: res.data.total || 0,
      };
    }
  } catch (e) {
    console.error(e);
    MessagePlugin.error('获取数据失败');
  } finally {
    dataLoading.value = false;
  }
};

const resetIdx = () => {
  deleteIdx.value = -1;
};

// 重置表单
const resetForm = () => {
  productFormData.value = { ...INITIAL_PRODUCT_DATA };
  productForm.value?.reset();
};

// 查询
const onSubmit = () => {
  pagination.value.defaultCurrent = 1;
  fetchData();
};

// 重置
const onReset = () => {
  formData.value = { ...searchForm };
  pagination.value.defaultCurrent = 1;
  fetchData();
};

// 分页变化
const rehandlePageChange = (pageInfo: PageInfo, newDataSource: TableRowData[]) => {
  pagination.value.defaultCurrent = pageInfo.current;
  pagination.value.defaultPageSize = pageInfo.pageSize;
  fetchData();
};

// 表格变化
const rehandleChange = (changeParams, triggerAndData) => {
  console.log('统一 Change', changeParams, triggerAndData);
};

// 选择变化
const onSelectChange = (value: number[]) => {
  selectedRowKeys.value = value;
};

// 新建商品
const handleAdd = () => {
  isEdit.value = false;
  resetForm();
  dialogVisible.value = true;
};

// 编辑商品
const handleEdit = ({ row }) => {
  isEdit.value = true;
  productFormData.value = { ...row };
  dialogVisible.value = true;
};

// 删除商品
const handleClickDelete = ({ row }) => {
  deleteIdx.value = row.rowIndex;
  confirmVisible.value = true;
};

// 批量删除
const handleBatchDelete = () => {
  confirmVisible.value = true;
};

// 对话框确认
const handleDialogConfirm = async () => {
  try {
    await productForm.value?.validate();
    
    if (isEdit.value) {
      await productApi.saveProduct(productFormData.value);
      MessagePlugin.success('修改成功');
    } else {
      await productApi.saveProduct(productFormData.value);
      MessagePlugin.success('新建成功');
    }
    
    dialogVisible.value = false;
    resetForm();
    fetchData();
  } catch (error) {
    console.error(error);
  }
};

// 确认删除
const onConfirmDelete = async () => {
  try {
    if (selectedRowKeys.value.length > 0) {
      // 批量删除
      await productApi.deleteProduct(selectedRowKeys.value);
      MessagePlugin.success('批量删除成功');
      selectedRowKeys.value = [];
    } else {
      // 单个删除
      data.value.splice(deleteIdx.value, 1);
      pagination.value.total = data.value.length;
      MessagePlugin.success('删除成功');
    }
    
    confirmVisible.value = false;
    resetIdx();
    fetchData();
  } catch (error) {
    console.error(error);
    MessagePlugin.error('删除失败');
  }
};

// 取消删除
const onCancel = () => {
  resetIdx();
  selectedRowKeys.value = [];
};

const headerAffixedTop = computed(
  () =>
    ({
      offsetTop: store.isUseTabsRouter ? 48 : 0,
      container: `.${prefix}-layout`,
    } as any),
);

onMounted(() => {
  fetchData();
});
</script>

<style lang="less" scoped>
.shop-product-list {
  background-color: var(--td-bg-color-container);
  padding: 30px 32px;
  border-radius: var(--td-radius-default);

  .table-operation {
    margin-bottom: 16px;
    display: flex;
    gap: 12px;
  }

  .table-container {
    margin-top: 16px;
  }

  .form-item-content {
    width: 100%;
  }

  .operation-container {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
  }

  .price-text {
    color: #e34d59;
    font-weight: 600;
  }
}
</style>
