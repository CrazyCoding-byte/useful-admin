<template>
  <div class="post-page">
    <!-- 搜索区域 -->
    <t-card class="search-card" :bordered="false">
      <div class="search-form">
        <div class="search-item">
          <span class="search-label">岗位编码</span>
          <t-input
            v-model="searchForm.postCode"
            placeholder="请输入岗位编码"
            clearable
            class="search-input"
          />
        </div>
        <div class="search-item">
          <span class="search-label">岗位名称</span>
          <t-input
            v-model="searchForm.postName"
            placeholder="请输入岗位名称"
            clearable
            class="search-input"
          />
        </div>
        <div class="search-item">
          <span class="search-label">状态</span>
          <t-select
            v-model="searchForm.status"
            placeholder="岗位状态"
            clearable
            class="search-input"
          >
            <t-option label="正常" value="0" />
            <t-option label="停用" value="1" />
          </t-select>
        </div>
        <div class="search-buttons">
          <t-button theme="primary" @click="getPostList">
            <template #icon><t-icon name="search" /></template>
            搜索
          </t-button>
          <t-button variant="outline" @click="resetSearch">
            <template #icon><t-icon name="refresh" /></template>
            重置
          </t-button>
        </div>
      </div>
    </t-card>

    <!-- 操作按钮区域 -->
    <t-card class="operation-card" :bordered="false">
      <div class="operation-bar">
        <div class="left-operations">
          <t-button theme="primary" @click="handleAddPost">
            <template #icon><t-icon name="add" /></template>
            新增
          </t-button>
          <t-button theme="warning" :disabled="!selectedRowKeys.length" @click="handleBatchEdit">
            <template #icon><t-icon name="edit" /></template>
            修改
          </t-button>
          <t-button theme="danger" :disabled="!selectedRowKeys.length" @click="handleBatchDelete">
            <template #icon><t-icon name="delete" /></template>
            删除
          </t-button>
          <t-button variant="outline">
            <template #icon><t-icon name="download" /></template>
            导出
          </t-button>
        </div>
        <div class="right-operations">
          <t-button variant="text" shape="circle" @click="getPostList">
            <t-icon name="refresh" />
          </t-button>
        </div>
      </div>
    </t-card>

    <!-- 表格区域 -->
    <t-card class="table-card" :bordered="false">
      <t-table
        :data="postList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        row-key="postId"
        v-model:selected-row-keys="selectedRowKeys"
        @page-change="handlePageChange"
      >
        <template #status="{ row }">
          <t-tag :theme="row.status === '0' ? 'success' : 'danger'">
            {{ row.status === '0' ? '正常' : '停用' }}
          </t-tag>
        </template>
        <template #op="{ row }">
          <div class="operation-icons">
            <t-button theme="warning" variant="text" size="small" @click="handleEdit(row)">
              <t-icon name="edit" />
            </t-button>
            <t-button theme="danger" variant="text" size="small" @click="handleDelete(row)">
              <t-icon name="delete" />
            </t-button>
          </div>
        </template>
      </t-table>
    </t-card>

    <!-- 岗位对话框 -->
    <t-dialog
      v-model:visible="postDialogVisible"
      :header="postForm.postId ? '修改岗位' : '新增岗位'"
      width="500px"
    >
      <t-form
        :data="postForm"
        :rules="postFormRules"
        ref="postFormRef"
        label-width="100px"
      >
        <t-form-item label="岗位名称" name="postName">
          <t-input v-model="postForm.postName" placeholder="请输入岗位名称"/>
        </t-form-item>
        <t-form-item label="岗位编码" name="postCode">
          <t-input v-model="postForm.postCode" placeholder="请输入岗位编码"/>
        </t-form-item>
        <t-form-item label="岗位顺序" name="postSort">
          <t-input-number v-model="postForm.postSort" :min="0" />
        </t-form-item>
        <t-form-item label="岗位状态" name="status">
          <t-radio-group v-model="postForm.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="备注" name="remark">
          <t-textarea v-model="postForm.remark" placeholder="请输入备注" :rows="3" />
        </t-form-item>
      </t-form>
      <template #footer>
        <t-button @click="closePostDialog">取消</t-button>
        <t-button theme="primary" @click="submitPostForm">确定</t-button>
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
import { postApi, type SysPost } from '@/api/system/post';
import { MessagePlugin } from 'tdesign-vue-next';
import type { PaginationProps, TableProps } from 'tdesign-vue-next';

// 搜索表单
const searchForm = ref<Partial<SysPost>>({
  postCode: '',
  postName: '',
  status: '',
});

// 岗位列表
const postList = ref<SysPost[]>([]);
const loading = ref(false);
const selectedRowKeys = ref<number[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 表格列配置
const columns: TableProps['columns'] = [
  {
    colKey: 'row-select',
    type: 'multiple',
    width: 46,
  },
  {
    colKey: 'postId',
    title: '岗位编号',
    width: 100,
    align: 'center',
  },
  {
    colKey: 'postCode',
    title: '岗位编码',
    width: 150,
  },
  {
    colKey: 'postName',
    title: '岗位名称',
    width: 150,
  },
  {
    colKey: 'postSort',
    title: '岗位排序',
    width: 100,
    align: 'center',
  },
  {
    colKey: 'status',
    title: '状态',
    width: 80,
    align: 'center',
  },
  {
    colKey: 'createTime',
    title: '创建时间',
    width: 180,
  },
  {
    colKey: 'op',
    title: '操作',
    width: 120,
    align: 'center',
    fixed: 'right',
  },
];

// 分页配置
const pagination = computed(() => ({
  pageSize: pageSize.value,
  current: currentPage.value,
  total: total.value,
  showJumper: true,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
}));

// 获取岗位列表
const getPostList = async () => {
  loading.value = true;
  try {
    const response = await postApi.getPostList({
      ...searchForm.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    });
    const data = response.data || response;
    postList.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    MessagePlugin.error('获取岗位列表失败');
    console.error('获取岗位列表失败:', error);
    postList.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

// 重置搜索
const resetSearch = () => {
  searchForm.value = {
    postCode: '',
    postName: '',
    status: '',
  };
  getPostList();
};

// 处理分页变化
const handlePageChange = (pageInfo: PaginationProps) => {
  currentPage.value = pageInfo.current || 1;
  pageSize.value = pageInfo.pageSize || 10;
  getPostList();
};

// 岗位对话框
const postDialogVisible = ref(false);
const postFormRef = ref<any>();
const postForm = ref<Partial<SysPost>>({
  postId: undefined,
  postCode: '',
  postName: '',
  postSort: 0,
  status: '0',
  remark: '',
});

const postFormRules = ref({
  postName: [
    { required: true, message: '请输入岗位名称', trigger: ['blur', 'change'] },
  ],
  postCode: [
    { required: true, message: '请输入岗位编码', trigger: ['blur', 'change'] },
  ],
  postSort: [
    { required: true, message: '请输入岗位顺序', trigger: ['blur', 'change'] },
  ],
});

// 新增岗位
const handleAddPost = () => {
  postForm.value = {
    postId: undefined,
    postCode: '',
    postName: '',
    postSort: 0,
    status: '0',
    remark: '',
  };
  postDialogVisible.value = true;
};

// 编辑岗位
const handleEdit = (row: SysPost) => {
  postForm.value = { ...row };
  postDialogVisible.value = true;
};

// 批量修改
const handleBatchEdit = () => {
  if (selectedRowKeys.value.length === 1) {
    const post = postList.value.find(p => p.postId === selectedRowKeys.value[0]);
    if (post) handleEdit(post);
  } else {
    MessagePlugin.warning('请选择一条记录进行修改');
  }
};

// 关闭岗位对话框
const closePostDialog = () => {
  postDialogVisible.value = false;
};

// 提交岗位表单
const submitPostForm = async () => {
  if (!postFormRef.value) return;
  try {
    await postFormRef.value.validate();
    if (postForm.value.postId) {
      await postApi.updatePost(postForm.value);
      MessagePlugin.success('修改岗位成功');
    } else {
      await postApi.addPost(postForm.value);
      MessagePlugin.success('新增岗位成功');
    }
    postDialogVisible.value = false;
    getPostList();
  } catch (error) {
    console.error('表单验证失败:', error);
  }
};

// 删除岗位
const confirmVisible = ref(false);
const deletePostIds = ref<number[]>([]);
const confirmBody = computed(() => {
  return `确定要删除选中的 ${deletePostIds.value.length} 个岗位吗？删除后无法恢复！`;
});

const handleDelete = (row: SysPost) => {
  deletePostIds.value = [row.postId!];
  confirmVisible.value = true;
};

const handleBatchDelete = () => {
  if (selectedRowKeys.value.length > 0) {
    deletePostIds.value = [...selectedRowKeys.value];
    confirmVisible.value = true;
  }
};

const onConfirmDelete = async () => {
  try {
    await postApi.deletePost(deletePostIds.value);
    MessagePlugin.success('删除成功');
    confirmVisible.value = false;
    selectedRowKeys.value = [];
    getPostList();
  } catch (error) {
    MessagePlugin.error('删除失败');
    console.error('删除失败:', error);
  }
};

// 页面挂载时获取岗位列表
onMounted(() => {
  getPostList();
});
</script>

<style lang="less" scoped>
.post-page {
  padding: 16px;
  background-color: var(--td-bg-color-page);

  .search-card {
    margin-bottom: 16px;

    :deep(.t-card__body) {
      padding: 16px;
    }

    .search-form {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;

      .search-item {
        display: flex;
        align-items: center;
        gap: 8px;

        .search-label {
          font-size: 14px;
          color: var(--td-text-color-primary);
          white-space: nowrap;
        }

        .search-input {
          width: 180px;
        }
      }

      .search-buttons {
        display: flex;
        gap: 8px;
        margin-left: auto;
      }
    }
  }

  .operation-card {
    margin-bottom: 16px;

    :deep(.t-card__body) {
      padding: 12px 16px;
    }

    .operation-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .left-operations {
        display: flex;
        gap: 8px;
      }

      .right-operations {
        display: flex;
        gap: 4px;
      }
    }
  }

  .table-card {
    :deep(.t-card__body) {
      padding: 0;
    }

    .operation-icons {
      display: flex;
      justify-content: center;
      gap: 8px;
    }
  }
}
</style>
