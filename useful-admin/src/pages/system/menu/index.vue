<template>
  <div class="menu-page">
    <t-card :bordered="false">
      <!-- 搜索区域 -->
      <div class="search-area">
        <div class="search-row">
          <div class="search-item">
            <span class="search-label">菜单名称</span>
            <t-input
              v-model="searchForm.menuName"
              placeholder="请输入菜单名称"
              clearable
              class="search-input"
            />
          </div>
          <div class="search-item">
            <span class="search-label">状态</span>
            <t-select
              v-model="searchForm.status"
              placeholder="菜单状态"
              clearable
              class="search-input"
            >
              <t-option value="0" label="正常" />
              <t-option value="1" label="停用" />
            </t-select>
          </div>
          <div class="search-actions">
            <t-button theme="primary" @click="getMenuList">
              <template #icon><t-icon name="search" /></template>
              搜索
            </t-button>
            <t-button variant="outline" @click="resetSearch">
              <template #icon><t-icon name="refresh" /></template>
              重置
            </t-button>
          </div>
        </div>
      </div>

      <!-- 操作按钮区 -->
      <div class="operation-area">
        <t-button theme="primary" @click="handleAdd()">
          <template #icon><t-icon name="add" /></template>
          新增
        </t-button>
      </div>

      <!-- 树形表格 -->
      <t-enhanced-table
        :key="tableKey"
        :data="menuList"
        :columns="columns"
        :loading="loading"
        row-key="menuId"
        default-expand-all
        :tree="{ childrenKey: 'child', treeNodeColumnIndex: 0 }"
        hover
        stripe
      >
        <template #menuName="{ row }">
          <span>{{ row.menuName }}</span>
        </template>

        <template #icon="{ row }">
          <t-icon v-if="row.icon" :name="row.icon" />
          <span v-else>-</span>
        </template>

        <template #orderNum="{ row }">
          <span>{{ row.orderNum }}</span>
        </template>

        <template #perms="{ row }">
          <span>{{ row.perms || '-' }}</span>
        </template>

        <template #component="{ row }">
          <span>{{ row.component || '-' }}</span>
        </template>

        <template #status="{ row }">
          <t-tag :theme="row.status === '0' ? 'success' : 'danger'" variant="light">
            {{ row.status === '0' ? '正常' : '停用' }}
          </t-tag>
        </template>

        <template #operation="{ row }">
          <t-space>
            <t-link theme="primary" @click="handleEdit(row)">修改</t-link>
            <t-link theme="primary" @click="handleAdd(row)">添加</t-link>
            <t-link theme="danger" @click="handleDelete(row)">删除</t-link>
          </t-space>
        </template>
      </t-enhanced-table>
    </t-card>

    <!-- 新增/编辑弹窗 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      width="650px"
      :on-close="handleClose"
      :on-confirm="handleSubmit"
    >
      <t-form ref="formRef" :data="form" :rules="rules" label-width="100px">
        <t-form-item label="上级菜单" name="parentId">
          <t-tree-select
            v-model="form.parentId"
            :data="menuTreeOptions"
            :keys="{ label: 'menuName', value: 'menuId', children: 'child' }"
            placeholder="选择上级菜单"
            clearable
          />
        </t-form-item>

        <t-form-item label="菜单类型" name="menuType">
          <t-radio-group v-model="form.menuType" @change="onMenuTypeChange">
            <t-radio value="M">目录</t-radio>
            <t-radio value="C">菜单</t-radio>
            <t-radio value="F">按钮</t-radio>
          </t-radio-group>
        </t-form-item>

        <t-form-item label="菜单图标" name="icon" v-if="form.menuType !== 'F'">
          <t-input v-model="form.icon" placeholder="点击选择图标">
            <template #prefix-icon>
              <t-icon v-if="form.icon" :name="form.icon" />
            </template>
          </t-input>
        </t-form-item>

        <t-form-item label="菜单名称" name="menuName">
          <t-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </t-form-item>

        <t-form-item label="显示排序" name="orderNum">
          <t-input-number v-model="form.orderNum" :min="0" />
        </t-form-item>

        <!-- ====== 目录(M) 特有字段 ====== -->
        <template v-if="form.menuType === 'M'">
          <t-form-item label="路由地址" name="path">
            <t-input v-model="form.path" placeholder="请输入路由地址，如：system" />
          </t-form-item>
          <t-form-item label="显示状态" name="visible">
            <t-radio-group v-model="form.visible">
              <t-radio value="0">显示</t-radio>
              <t-radio value="1">隐藏</t-radio>
            </t-radio-group>
          </t-form-item>
        </template>

        <!-- ====== 菜单(C) 特有字段 ====== -->
        <template v-if="form.menuType === 'C'">
          <t-form-item label="是否外链" name="isFrame">
            <t-radio-group v-model="form.isFrame">
              <t-radio value="0">是</t-radio>
              <t-radio value="1">否</t-radio>
            </t-radio-group>
          </t-form-item>
          <t-form-item label="路由地址" name="path">
            <t-input v-model="form.path" placeholder="请输入路由地址，如：user" />
          </t-form-item>
          <t-form-item label="组件路径" name="component">
            <t-input v-model="form.component" placeholder="请输入组件路径，如：system/user/index" />
          </t-form-item>
          <t-form-item label="路由参数" name="query">
            <t-input v-model="form.query" placeholder="请输入路由参数" />
          </t-form-item>
          <t-form-item label="权限字符" name="perms">
            <t-input v-model="form.perms" placeholder="请输入权限字符，如：system:user:list" />
          </t-form-item>
          <t-form-item label="是否缓存" name="isCache">
            <t-radio-group v-model="form.isCache">
              <t-radio value="0">缓存</t-radio>
              <t-radio value="1">不缓存</t-radio>
            </t-radio-group>
          </t-form-item>
          <t-form-item label="显示状态" name="visible">
            <t-radio-group v-model="form.visible">
              <t-radio value="0">显示</t-radio>
              <t-radio value="1">隐藏</t-radio>
            </t-radio-group>
          </t-form-item>
        </template>

        <!-- ====== 按钮(F) 特有字段 ====== -->
        <template v-if="form.menuType === 'F'">
          <t-form-item label="权限字符" name="perms">
            <t-input v-model="form.perms" placeholder="请输入权限字符，如：system:user:add" />
          </t-form-item>
        </template>

        <t-form-item label="菜单状态" name="status">
          <t-radio-group v-model="form.status">
            <t-radio value="0">正常</t-radio>
            <t-radio value="1">停用</t-radio>
          </t-radio-group>
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 删除确认 -->
    <t-dialog
      v-model:visible="deleteVisible"
      header="确认删除"
      @confirm="confirmDelete"
    >
      <p>{{ deleteTip }}</p>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { menuApi } from '@/api/system/menu';
import type { SysMenu } from '@/api/model/menuModel';
import { MessagePlugin } from 'tdesign-vue-next';
import type { FormInstanceFunctions, PrimaryTableCol } from 'tdesign-vue-next';

const tableKey = ref(0);
const loading = ref(false);
const menuList = ref<SysMenu[]>([]);

// 搜索
const searchForm = ref({ menuName: '', status: '' });

// 表格列
const columns: PrimaryTableCol<SysMenu>[] = [
  { colKey: 'menuName', title: '菜单名称', width: 200, ellipsis: true },
  { colKey: 'icon', title: '图标', width: 80, align: 'center' },
  { colKey: 'orderNum', title: '排序', width: 70, align: 'center' },
  { colKey: 'perms', title: '权限标识', width: 180, ellipsis: true },
  { colKey: 'component', title: '组件路径', width: 200, ellipsis: true },
  { colKey: 'status', title: '状态', width: 80, align: 'center' },
  { colKey: 'operation', title: '操作', width: 180, align: 'center', fixed: 'right' },
];

// 弹窗
const dialogVisible = ref(false);
const dialogTitle = ref('新增菜单');
const formRef = ref<FormInstanceFunctions>();
const isEdit = ref(false);

const defaultForm = {
  menuId: undefined as number | undefined,
  parentId: 0,
  menuName: '',
  menuType: 'M' as string,
  icon: '',
  orderNum: 0,
  path: '',
  component: '',
  query: '',
  perms: '',
  isFrame: '1',
  isCache: '0',
  visible: '0',
  status: '0',
};

const form = reactive({ ...defaultForm });

const rules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入显示排序', trigger: 'blur' }],
  path: [
    {
      required: true,
      message: '请输入路由地址',
      trigger: 'blur',
      validator: () => form.menuType !== 'F',
    },
  ],
  component: [
    {
      required: true,
      message: '请输入组件路径',
      trigger: 'blur',
      validator: () => form.menuType === 'C',
    },
  ],
};

// 菜单树选项
const menuTreeOptions = computed(() => {
  return [{ menuId: 0, menuName: '主类目', child: menuList.value }];
});

// 删除
const deleteVisible = ref(false);
const deleteRow = ref<SysMenu | null>(null);
const deleteTip = computed(() => {
  const name = deleteRow.value?.menuName || '';
  const childCount = deleteRow.value?.child?.length || 0;
  return childCount > 0
    ? `确定要删除菜单【${name}】吗？其下 ${childCount} 个子菜单将一并删除！`
    : `确定要删除菜单【${name}】吗？`;
});

// ====== 数据加载 ======
const getMenuList = async () => {
  loading.value = true;
  try {
    const res = await menuApi.getMenuList(searchForm.value);
    console.log('[Menu] 收到数据:', res?.length, '条根节点, 第一条:', res?.[0]?.menuName, 'child:', res?.[0]?.child?.length);
    menuList.value = res || [];
    tableKey.value++;
  } catch (e) {
    MessagePlugin.error('获取菜单列表失败');
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const resetSearch = () => {
  searchForm.value = { menuName: '', status: '' };
  getMenuList();
};

// ====== 新增/编辑 ======
const onMenuTypeChange = () => {
  // 切换类型时清空类型相关字段
  form.path = '';
  form.component = '';
  form.perms = '';
  form.isFrame = '1';
  form.isCache = '0';
  form.icon = '';
  form.visible = '0';
};

const handleAdd = (row?: SysMenu) => {
  isEdit.value = false;
  dialogTitle.value = '新增菜单';
  Object.assign(form, defaultForm);
  form.parentId = row?.menuId ?? 0;
  dialogVisible.value = true;
};

const handleEdit = (row: SysMenu) => {
  isEdit.value = true;
  dialogTitle.value = '修改菜单';
  Object.assign(form, { ...row });
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate();
  if (valid !== true) return;

  try {
    const data = { ...form } as SysMenu;
    if (isEdit.value) {
      await menuApi.updateMenu(data);
      MessagePlugin.success('修改成功');
    } else {
      await menuApi.addMenu(data);
      MessagePlugin.success('新增成功');
    }
    dialogVisible.value = false;
    getMenuList();
  } catch (e) {
    MessagePlugin.error(isEdit.value ? '修改失败' : '新增失败');
    console.error(e);
  }
};

const handleClose = () => {
  dialogVisible.value = false;
};

// ====== 删除 ======
const handleDelete = (row: SysMenu) => {
  deleteRow.value = row;
  deleteVisible.value = true;
};

const confirmDelete = async () => {
  if (!deleteRow.value?.menuId) return;
  try {
    await menuApi.deleteMenu(deleteRow.value.menuId);
    MessagePlugin.success('删除成功');
    deleteVisible.value = false;
    getMenuList();
  } catch (e) {
    MessagePlugin.error('删除失败');
    console.error(e);
  }
};

onMounted(() => {
  getMenuList();
});
</script>

<style lang="less" scoped>
.menu-page {
  padding: 20px;
}

.search-area {
  margin-bottom: 16px;

  .search-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
  }

  .search-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .search-label {
      white-space: nowrap;
      color: var(--td-text-color-secondary);
    }

    .search-input {
      width: 200px;
    }
  }

  .search-actions {
    display: flex;
    gap: 8px;
  }
}

.operation-area {
  margin-bottom: 16px;
}
</style>
