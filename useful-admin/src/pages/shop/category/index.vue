<template>
  <div class="category-page">
    <t-card class="category-card-container">
      <t-row justify="space-between" align="middle">
        <div class="left-operation-container">
          <t-button theme="primary" @click="handleAddCategory"> 新增分类</t-button>
          <t-button variant="base" theme="default" :disabled="!selectedRowKeys.length"> 批量删除</t-button>
          <p v-if="!!selectedRowKeys.length" class="selected-count">已选{{ selectedRowKeys.length }}项</p>
        </div>
        <div class="search-container">
          <t-input
            v-model="searchForm.name"
            placeholder="分类名称"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-button theme="primary" @click="handleSearch">查询</t-button>
          <t-button @click="resetSearch">重置</t-button>
        </div>
      </t-row>

      <t-enhanced-table
        :data="categoryList"
        :loading="loading"
        :columns="columns"
        :row-key="rowKey"
        :tree="{childrenKey:'children',defaultExpandAll:true}"
        bordered
        stripe
        :pagination="pagination"
        :selected-row-keys="selectedRowKeys"
        @page-change="onPageChange"
        @select-change="onSelectChange"
      >
        <template #status="{ row }">
          <t-tag v-if="row.status === '0'" theme="success" variant="light"> 正常</t-tag>
          <t-tag v-else theme="danger" variant="light"> 禁用</t-tag>
        </template>
        <template #op="{ row }">
          <t-space>
            <t-link theme="primary" @click="editCategory(row)">编辑</t-link>
            <t-link theme="danger" @click="deleteCategory(row.catId!)">删除</t-link>
          </t-space>
        </template>
      </t-enhanced-table>
    </t-card>

    <!-- 新增/修改分类对话框 -->
    <t-dialog
      v-model:visible="addDialogVisible"
      :header="addForm.catId ? '修改分类' : '新增分类'"
      width="600px"
    >
      <t-form
        :data="addForm"
        :rules="addFormRules"
        ref="addFormRef"
        label-width="100px"
      >
        <t-form-item label="分类名称" name="categoryName">
          <t-input v-model="addForm.name" placeholder="请输入分类名称"/>
        </t-form-item>
        <t-form-item label="父分类" name="parentCid">
          <t-input v-model="addForm.parentCid" placeholder="请输入分类编码"/>
        </t-form-item>
        <t-form-item label="层级" name="catLevel">
          <t-input-number v-model="addForm.catLevel" placeholder="请选择层级" :min="1" :max="3">
          </t-input-number>
        </t-form-item>
        <t-form-item label="排序" name="sort">
          <t-input-number v-model="addForm.sort" :min="0"/>
        </t-form-item>
        <t-form-item label="状态" name="showStatus">
          <t-radio-group v-model="addForm.showStatus">
            <t-radio :value="1">显示</t-radio>
            <t-radio :value="0">隐藏</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="图标" name="icon">
          <t-input v-model="addForm.icon" placeholder="图标地址"/>
        </t-form-item>
        <t-form-item label="计量单位" name="productUnit">
          <t-input v-model="addForm.productUnit" placeholder="如：个、件、箱"/>
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
import {ref, onMounted, computed} from 'vue';
import {MessagePlugin} from 'tdesign-vue-next';
import type {PaginationProps, TableProps} from 'tdesign-vue-next';
import {categoryApi} from '@/api/shop/category';
import type {Category, CategoryTree} from '@/api/model/categoryModel';

// 搜索表单
const searchForm = ref<Partial<Category>>({
  name: '',
});

// 分类列表
const categoryList = ref<Category[]>([]);
const loading = ref(false);
const selectedRowKeys = ref<number[]>([]);

// 表格列配置
const columns: any = [
  {
    colKey: 'row-select',
    type: 'multiple',
    width: 46,
  },
  {
    colKey: 'catId',
    title: '分类ID',
  },
  {
    colKey: 'name',
    title: '分类名称',
  },
  {
    colKey: 'parentCid',
    title: '父分类ID',
  },
  {
    colKey: 'catLevel',
    title: '层级',
  },
  {
    colKey: 'sort',
    title: '排序',
  },
  {
    colKey: 'showStatus',
    title: '状态',
    cell: (h: any, {row}: any) =>
      h('t-tag', {props: {theme: row.showStatus === 1 ? 'success' : 'danger', variant: 'light'}},
        row.showStatus === 1 ? '显示' : '隐藏'),
  },
  {
    colKey: 'op',
    title: '操作',
    width: 150,
  },
];


// 行键
const rowKey = 'catId';

// 获取分类列表
const getCategoryList = async (pageInfo?: any) => {
  loading.value = true;
  try {
    const requestParams = {
      ...searchForm.value,
    };
    console.log('请求参数:', requestParams);

    // 使用分类API请求
    const response = await categoryApi.getCategoryList(requestParams);
    console.log('响应数据:', response);
    const data = response;
    categoryList.value = Array.isArray(data) ? data : (data.records || data?.data || []);
    // 直接使用响应数据
    console.log('分类列表:', categoryList.value);
    console.log('分类列表长度:', categoryList.value.length);


  } catch (error) {
    MessagePlugin.error('获取分类列表失败');
    console.error('获取分类列表失败:', error);
    categoryList.value = [];
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    name: '',

  };
  getCategoryList();
};

// 处理搜索按钮点击
const handleSearch = () => {
  getCategoryList();
};

// 处理分页变化
const onPageChange = async (pageInfo: any) => {
  console.log('page-change', pageInfo);
  await getCategoryList(pageInfo);
};

// 处理选择变化
const onSelectChange = (value: any, params: any) => {
  selectedRowKeys.value = value as number[];
  console.log(value, params);
};

// 新增分类对话框
const addDialogVisible = ref(false);
const addFormRef = ref<any>();
const addForm = ref({
  catId: undefined as number | undefined,
  name: '',
  parentCid: 0,
  catLevel: 1,
  showStatus: 0,
  sort: 0,
  icon: '',
  productUnit: '',
  productCount: 0,
});

const addFormRules = ref<any>({
  name: [
    {required: true, message: '请输入分类名称', trigger: ['blur', 'change']},
    {min: 2, max: 50, message: '分类名称长度应在2-50个字符之间', trigger: ['blur', 'change']}
  ],
});

// 新增分类
const handleAddCategory = () => {
  console.log('新增分类');
  // 重置表单
  addForm.value = {
    catId: undefined,
    name: '',
    parentCid: 0,
    catLevel: 1,
    showStatus: 1,
    sort: 0,
    icon: '',
    productUnit: '',
    productCount: 0,
  };
  // 打开对话框
  addDialogVisible.value = true;
};

// 关闭新增分类对话框
const closeAddDialog = () => {
  addDialogVisible.value = false;
};

// 提交分类表单（新增或修改）
const submitAddForm = async () => {
  if (!addFormRef.value) return;
  try {
    await addFormRef.value.validate();

    // 准备提交数据
    const submitData = {...addForm.value};

    // 对于编辑分类，如果某些字段为空，不发送
    if (submitData.catId) {
      Object.keys(submitData).forEach(key => {
        if (submitData[key] === null || submitData[key] === undefined || submitData[key] === '') {
          delete submitData[key];
        }
      });
    }

    console.log('提交表单:', submitData);

    // 使用同一个API接口处理新增和修改
    await categoryApi.saveCategory(submitData);
    MessagePlugin.success(addForm.value.catId ? '修改分类成功' : '新增分类成功');

    // 关闭对话框
    addDialogVisible.value = false;
    // 刷新分类列表
    getCategoryList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 编辑分类
const editCategory = (category: Category) => {
  console.log('编辑分类:', category);
  try {
    // 直接使用传入的分类数据填充表单
    addForm.value = {
      catId: category.catId,
      name: category.name || '',
      parentCid: category.parentCid || 0,
      catLevel: category.catLevel || 1,
      showStatus: category.showStatus ?? 1,
      sort: category.sort || 0,
      icon: category.icon || '',
      productUnit: category.productUnit || '',
      productCount: category.productCount || 0,
    };
    console.log('填充表单数据:', addForm.value);
    // 打开对话框
    addDialogVisible.value = true;
  } catch (error) {
    MessagePlugin.error('编辑分类失败');
    console.error('编辑分类失败:', error);
  }
};

// 删除分类
const deleteCategory = (categoryId: number) => {
  deleteIdx.value = categoryId;
  confirmVisible.value = true;
};

// 确认对话框
const confirmVisible = ref(false);
const deleteIdx = ref<number>(-1);
const confirmBody = computed(() => {
  return '删除后，分类的所有信息将被清空，且无法恢复';
});

// 确认删除
const onConfirmDelete = async () => {
  try {
    await categoryApi.deleteCategory([deleteIdx.value]);
    MessagePlugin.success('删除成功');
    getCategoryList();
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

// 页面挂载时获取分类列表
onMounted(async () => {
  await getCategoryList({});
});
</script>

<style lang="less" scoped>
.category-card-container {
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
