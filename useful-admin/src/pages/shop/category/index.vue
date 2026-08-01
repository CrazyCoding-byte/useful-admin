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
            v-model="searchForm.categoryName"
            placeholder="分类名称"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-input
            v-model="searchForm.categoryCode"
            placeholder="分类编码"
            style="width: 200px; margin-right: 10px;"
            clearable
          />
          <t-button theme="primary" @click="handleSearch">查询</t-button>
          <t-button @click="resetSearch">重置</t-button>
        </div>
      </t-row>

      <t-table
        :data="categoryList"
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
          <t-tag v-if="row.status === '0'" theme="success" variant="light"> 正常</t-tag>
          <t-tag v-else theme="danger" variant="light"> 禁用</t-tag>
        </template>
        <template #op="{ row }">
          <a class="t-button-link" @click="editCategory(row)">编辑</a>
          <a class="t-button-link" @click="deleteCategory(row.categoryId!)">删除</a>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/修改分类对话框 -->
    <t-dialog
      v-model:visible="addDialogVisible"
      :header="addForm.categoryId ? '修改分类' : '新增分类'"
      width="600px"
    >
      <t-form
        :data="addForm"
        :rules="addFormRules"
        ref="addFormRef"
        label-width="100px"
      >
        <t-form-item label="分类名称" name="categoryName">
          <t-input v-model="addForm.categoryName" placeholder="请输入分类名称"/>
        </t-form-item>
        <t-form-item label="分类编码" name="categoryCode">
          <t-input v-model="addForm.categoryCode" placeholder="请输入分类编码"/>
        </t-form-item>
        <t-form-item label="父分类" name="parentId">
          <t-select v-model="addForm.parentId" placeholder="请选择父分类">
            <t-option value="0" label="顶级分类"/>
            <!-- 这里可以动态加载分类树 -->
          </t-select>
        </t-form-item>
        <t-form-item label="分类描述" name="description">
          <t-textarea v-model="addForm.description" placeholder="请输入分类描述"/>
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
import {ref, onMounted, computed} from 'vue';
import {MessagePlugin} from 'tdesign-vue-next';
import type {PaginationProps, TableProps} from 'tdesign-vue-next';
import {categoryApi} from '@/api/shop/category';
import type {Category} from '@/api/model/categoryModel';

// 搜索表单
const searchForm = ref<Partial<Category>>({
  categoryName: '',
  categoryCode: '',
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
    colKey: 'categoryId',
    title: '分类ID',
  },
  {
    colKey: 'categoryName',
    title: '分类名称',
  },
  {
    colKey: 'categoryCode',
    title: '分类编码',
  },
  {
    colKey: 'parentId',
    title: '父分类ID',
  },
  {
    colKey: 'description',
    title: '分类描述',
    ellipsis: true,
  },
  {
    colKey: 'status',
    title: '状态',
    cell: (h: any, {row}: any) => {
      if (row.status === '0') {
        return h('t-tag', {props: {theme: 'success', variant: 'light'}}, ' 正常 ');
      } else {
        return h('t-tag', {props: {theme: 'danger', variant: 'light'}}, ' 禁用 ');
      }
    },
  },
  {
    colKey: 'op',
    title: '操作',
    cell: (h: any, {row}: any) => {
      return h('div', [
        h('a', {
          class: 't-button-link',
          on: {click: () => editCategory(row as Category)}
        }, '编辑'),
        h('a', {
          class: 't-button-link',
          on: {click: () => deleteCategory((row as Category).categoryId!)}
        }, '删除')
      ]);
    },
  },
];

// 分页配置
const pagination = ref({
  pageSize: 10,
  current: 1,
  total: 0,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: [10, 20, 50, 100],
});

// 行键
const rowKey = 'categoryId';

// 获取分类列表
const getCategoryList = async (pageInfo?: any) => {
  loading.value = true;
  try {
    const current = pageInfo?.current || pagination.value.current || 1;
    const pageSize = pageInfo?.pageSize || pagination.value.pageSize || 10;

    const requestParams = {
      ...searchForm.value,
    };
    console.log('请求参数:', requestParams);

    // 使用分类API请求
    const response = await categoryApi.getCategoryList(requestParams, current, pageSize);
    console.log('响应数据:', response);

    // 直接使用响应数据
    categoryList.value = response.records || [];
    console.log('分类列表:', categoryList.value);
    console.log('分类列表长度:', categoryList.value.length);

    pagination.value.total = response.total || 0;
    console.log('总记录数:', pagination.value.total);

  } catch (error) {
    MessagePlugin.error('获取分类列表失败');
    console.error('获取分类列表失败:', error);
    categoryList.value = [];
    pagination.value.total = 0;
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    categoryName: '',
    categoryCode: '',
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
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
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
  categoryId: null,
  categoryName: '',
  categoryCode: '',
  parentId: 0,
  description: '',
  status: '0'
});

const addFormRules = ref<any>({
  categoryName: [
    {required: true, message: '请输入分类名称', trigger: ['blur', 'change']},
    {min: 2, max: 50, message: '分类名称长度应在2-50个字符之间', trigger: ['blur', 'change']}
  ],
  categoryCode: [
    {required: true, message: '请输入分类编码', trigger: ['blur', 'change']},
    {min: 2, max: 30, message: '分类编码长度应在2-30个字符之间', trigger: ['blur', 'change']}
  ],
  parentId: [
    {required: true, message: '请选择父分类', trigger: ['blur', 'change']}
  ],
  description: [
    {required: false, message: '请输入分类描述', trigger: ['blur', 'change']},
    {max: 200, message: '分类描述长度不能超过200个字符', trigger: ['blur', 'change']}
  ],
  status: [
    {required: true, message: '请选择状态', trigger: ['blur', 'change']}
  ]
});

// 新增分类
const handleAddCategory = () => {
  console.log('新增分类');
  // 重置表单
  addForm.value = {
    categoryId: null,
    categoryName: '',
    categoryCode: '',
    parentId: 0,
    description: '',
    status: '0'
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
    if (submitData.categoryId) {
      Object.keys(submitData).forEach(key => {
        if (submitData[key] === null || submitData[key] === undefined || submitData[key] === '') {
          delete submitData[key];
        }
      });
    }

    console.log('提交表单:', submitData);

    // 使用同一个API接口处理新增和修改
    await categoryApi.saveCategory(submitData);
    MessagePlugin.success(addForm.value.categoryId ? '修改分类成功' : '新增分类成功');

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
      categoryId: category.categoryId || null,
      categoryName: category.categoryName || '',
      categoryCode: category.categoryCode || '',
      parentId: category.parentId || 0,
      description: category.description || '',
      status: category.status || '0'
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
  await getCategoryList({
    current: pagination.value.current || 1,
    pageSize: pagination.value.pageSize || 10,
  });
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
