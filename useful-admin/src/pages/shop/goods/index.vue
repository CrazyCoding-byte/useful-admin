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
              <t-form-item label="商品名称" name="productName">
                <t-input
                  v-model="formData.productName"
                  class="form-item-content"
                  type="search"
                  placeholder="请输入商品名称"
                  :style="{ minWidth: '134px' }"
                />
              </t-form-item>
            </t-col>
            <t-col :span="4">
              <t-form-item label="商品编码" name="productCode">
                <t-input
                  v-model="formData.productCode"
                  class="form-item-content"
                  placeholder="请输入商品编码"
                  :style="{ minWidth: '134px' }"
                />
              </t-form-item>
            </t-col>
            <t-col :span="4">
              <t-form-item label="商品状态" name="status">
                <t-select
                  v-model="formData.status"
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
        <add-icon/>
        新建商品
      </t-button>
      <t-button
        v-if="selectedRowKeys.length > 0"
        theme="danger"
        variant="outline"
        @click="handleBatchDelete"
      >
        <delete-icon/>
        批量删除
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
          <t-tag v-if="row.status === '0'" theme="success" variant="light">正常</t-tag>
          <t-tag v-if="row.status === '1'" theme="danger" variant="light">禁用</t-tag>
        </template>
        <template #price="{ row }">
          <span class="price-text">¥{{ (row.price || 0).toFixed(2) }}</span>
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
        <t-form-item label="商品名称" name="productName">
          <t-input v-model="productFormData.productName" placeholder="请输入商品名称"/>
        </t-form-item>
        <t-form-item label="商品编码" name="productCode">
          <t-input v-model="productFormData.productCode" placeholder="请输入商品编码"/>
        </t-form-item>
        <t-form-item label="商品价格" name="price">
          <t-input-number
            v-model="productFormData.price"
            :min="0"
            :max="1000000"
            :step="0.01"
            placeholder="请输入商品价格"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="商品库存" name="stock">
          <t-input-number
            v-model="productFormData.stock"
            :min="0"
            :max="999999"
            placeholder="请输入商品库存"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="商品描述" name="description">
          <t-textarea
            v-model="productFormData.description"
            placeholder="请输入商品描述"
            :rows="3"
          />
        </t-form-item>
        <t-form-item label="商品状态" name="status">
          <t-radio-group v-model="productFormData.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">禁用</t-radio>
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
import {ref, computed, onMounted} from 'vue';
import {MessagePlugin, PrimaryTableCol, TableRowData, PageInfo, FormInstanceFunctions} from 'tdesign-vue-next';
import {AddIcon, DeleteIcon} from 'tdesign-icons-vue-next';
import {productApi} from '@/api/product';
import {useSettingStore} from '@/store';
import {prefix} from '@/config/global';
import type {Product} from '@/api/model/productModel';

const store = useSettingStore();

// 状态选项
const STATUS_OPTIONS = [
  {label: '正常', value: '0'},
  {label: '禁用', value: '1'},
];

// 表格列定义
const COLUMNS: PrimaryTableCol<TableRowData>[] = [
  {type: 'multiple', width: 50, fixed: 'left'},
  {
    title: '商品 ID',
    colKey: 'productId',
    width: 100,
    fixed: 'left',
  },
  {
    title: '商品名称',
    colKey: 'productName',
    width: 200,
    ellipsis: true,
    align: 'left',
  },
  {
    title: '商品编码',
    colKey: 'productCode',
    width: 150,
    ellipsis: true,
  },
  {
    title: '商品价格',
    colKey: 'price',
    width: 120,
  },
  {
    title: '商品库存',
    colKey: 'stock',
    width: 120,
  },
  {
    title: '商品状态',
    colKey: 'status',
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
  productCode: '',
  status: '',
};

const formData = ref({...searchForm});
const rowKey = 'productId';
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
const selectedRowKeys = ref<string[]>([]);

// 表单数据
const INITIAL_PRODUCT_DATA = {
  productId: undefined,
  productName: '',
  productCode: '',
  price: 0,
  stock: 0,
  description: '',
  status: '0',
};

const productFormData = ref({...INITIAL_PRODUCT_DATA});
const productForm = ref<FormInstanceFunctions>(null);

// 表单验证规则
const FORM_RULES = {
  productName: [{required: true, message: '请输入商品名称', trigger: 'blur'}],
  productCode: [{required: true, message: '请输入商品编码', trigger: 'blur'}],
  price: [{required: true, message: '请输入商品价格', trigger: 'blur'}],
  stock: [{required: true, message: '请输入商品库存', trigger: 'blur'}],
};

// 确认删除提示
const confirmBody = computed(() => {
  console.log("当前要删除 deleteidx", deleteIdx.value)
  if (deleteIdx.value) {
    const product = data.value.find(item => item.productId === deleteIdx.value);
    if (product) {
      const {productName} = product;
      return `删除后，${productName}的所有商品信息将被清空，且无法恢复`;
    }
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
    const params = {
      pageNum: pagination.value.defaultCurrent,
      pageSize: pagination.value.defaultPageSize,
      ...formData.value,
    };
    const res = await productApi.getProductList(params);
    if (res && res.list) {
      // 转换字段名，将接口返回的字段名映射到前端期望的字段名
      data.value = res.list.map((item: any) => ({
        productId: item.id,  // 已经是字符串类型
        productName: item.spuName,
        productCode: item.productCode || item.spuName,  // 优先使用 productCode，没有则用 spuName
        price: item.price || 0,  // 假设有 price 字段
        stock: item.stock || 0,  // 假设有 stock 字段
        status: item.publishStatus?.toString() || '0',
        createTime: item.createTime,
        updateTime: item.updateTime,
        description: item.spuDescription,
        catalogId: item.catalogId,
        brandId: item.brandId,
        brandName: item.brandName,
        weight: item.weight,
        publishStatus: item.publishStatus,
      }));
      pagination.value = {
        ...pagination.value,
        total: res.total || 0,
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
  productFormData.value = {...INITIAL_PRODUCT_DATA};
  productForm.value?.reset();
};

// 查询
const onSubmit = () => {
  pagination.value.defaultCurrent = 1;
  fetchData();
};

// 重置
const onReset = () => {
  formData.value = {...searchForm};
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
const onSelectChange = (value: string[]) => {
  selectedRowKeys.value = value;
};

// 新建商品
const handleAdd = () => {
  isEdit.value = false;
  resetForm();
  dialogVisible.value = true;
};

// 编辑商品
const handleEdit = ({row}) => {
  console.log('编辑的原始 row 数据:', row);
  isEdit.value = true;
  productFormData.value = {
    productId: row.productId,
    productName: row.productName,
    productCode: row.productCode,
    price: row.price,
    stock: row.stock,
    description: row.description,
    status: row.status,
    catalogId: row.catalogId,
    brandId: row.brandId,
    brandName: row.brandName,
    weight: row.weight,
  };
  console.log('编辑表单数据:', productFormData.value);
  dialogVisible.value = true;
};
const handleClickDelete = ({row}) => {
  console.log("当前选中删除的数据",row.productId);
  deleteIdx.value = row.productId;
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

    // 将前端字段映射到后端字段
    const backendData = {
      id: productFormData.value.productId ? Number(productFormData.value.productId) : null,
      spuName: productFormData.value.productName,
      spuDescription: productFormData.value.description,
      catalogId: productFormData.value.catalogId ? Number(productFormData.value.catalogId) : null,
      brandId: productFormData.value.brandId ? Number(productFormData.value.brandId) : null,
      brandName: productFormData.value.brandName || '',
      weight: productFormData.value.weight ? Number(productFormData.value.weight) : null,
      publishStatus: parseInt(productFormData.value.status) || 0,
    };

    console.log('提交到后端的数据:', backendData);

    if (isEdit.value) {
      await productApi.saveProduct(backendData);
      MessagePlugin.success('修改成功');
    } else {
      await productApi.saveProduct(backendData);
      MessagePlugin.success('新建成功');
    }

    dialogVisible.value = false;
    // 注意：先关闭对话框，再重置表单，避免重置影响提交
    setTimeout(() => {
      resetForm();
    }, 200);
    fetchData();
  } catch (error) {
    console.error(error);
    MessagePlugin.error('操作失败：' + (error as any).message || '未知错误');
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
    } else if (deleteIdx.value) {
      // 单个删除
      await productApi.deleteProduct([deleteIdx.value]);
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
