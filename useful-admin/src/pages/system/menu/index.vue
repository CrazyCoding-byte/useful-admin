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
              <t-option value="0" label="正常"/>
              <t-option value="1" label="停用"/>
            </t-select>
          </div>
          <div class="search-actions">
            <t-button theme="primary" @click="getMenuList">
              <template #icon>
                <t-icon name="search"/>
              </template>
              搜索
            </t-button>
            <t-button variant="outline" @click="resetSearch">
              <template #icon>
                <t-icon name="refresh"/>
              </template>
              重置
            </t-button>
          </div>
        </div>
      </div>

      <!-- 操作按钮区 -->
      <div class="operation-area">
        <t-button theme="primary" @click="handleAdd()">
          <template #icon>
            <t-icon name="add"/>
          </template>
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
        :tree="{ childrenKey: 'children', treeNodeColumnIndex: 0 }"
        hover
        stripe
      >
        <template #menuName="{ row }">
          <span>{{ row.menuName }}</span>
        </template>

        <template #icon="{ row }">
          <t-icon v-if="row.icon" :name="row.icon"/>
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
      width="700px"
      :on-close="handleClose"
      :on-confirm="handleSubmit"
    >
      <t-form ref="formRef" :data="form" :rules="rules" label-width="90px">
        <!-- 上级菜单 - 整行 -->
        <t-form-item label="上级菜单" name="parentId">
          <t-tree-select
            v-model="form.parentId"
            :data="menuTreeOptions"
            :keys="{ label: 'menuName', value: 'menuId', children: 'children' }"
            placeholder="选择上级菜单"
            clearable
          />
        </t-form-item>

        <!-- 菜单类型 - 整行 -->
        <t-form-item label="菜单类型" name="menuType">
          <t-radio-group v-model="form.menuType" @change="onMenuTypeChange">
            <t-radio value="M">目录</t-radio>
            <t-radio value="C">菜单</t-radio>
            <t-radio value="F">按钮</t-radio>
          </t-radio-group>
        </t-form-item>

        <!-- 菜单图标 - 整行 -->
        <t-form-item label="菜单图标" name="icon" v-if="form.menuType !== 'F'">
          <div class="icon-picker">
            <t-popup
              v-model="iconPopupVisible"
              placement="bottom-left"
              trigger="click"
              :overlay-inner-style="{ width: '480px', padding: '12px', maxHeight: '360px', overflow: 'auto' }"
              destroy-on-close
            >
              <div class="icon-trigger">
                <t-icon v-if="form.icon" :name="form.icon" style="font-size: 18px" />
                <span v-if="form.icon" style="margin-left: 8px">{{ form.icon }}</span>
                <span v-else class="icon-placeholder">请选择图标</span>
                <t-icon name="chevron-down" class="icon-arrow" />
              </div>
              <template #content>
                <t-input
                  v-model="iconSearch"
                  placeholder="搜索图标..."
                  clearable
                  style="margin-bottom: 12px"
                />
                <div v-if="filteredIconOptions.length === 0" class="icon-empty">无匹配图标</div>
                <div v-else class="icon-grid">
                  <div
                    v-for="item in filteredIconOptions"
                    :key="item"
                    class="icon-grid-item"
                    :class="{ 'icon-grid-item--active': form.icon === item }"
                    @click="selectIcon(item)"
                  >
                    <t-icon :name="item" style="font-size: 22px" />
                  </div>
                </div>
              </template>
            </t-popup>
          </div>
        </t-form-item>

        <!-- 菜单名称 + 显示排序 - 双列 -->
        <t-row :gutter="16">
          <t-col :span="12">
            <t-form-item label="菜单名称" name="menuName" required>
              <t-input v-model="form.menuName" placeholder="请输入菜单名称"/>
            </t-form-item>
          </t-col>
          <t-col :span="12">
            <t-form-item label="显示排序" name="orderNum" required>
              <t-input-number v-model="form.orderNum" :min="0" style="width: 100%"/>
            </t-form-item>
          </t-col>
        </t-row>

        <!-- ====== 目录(M) 特有字段 ====== -->
        <template v-if="form.menuType === 'M'">
          <!-- 是否外链 + 路由地址 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="isFrame">
                <template #label>
                  <t-tooltip content="选择是外链则路由地址需要以`http(s)://`开头">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  是否外链
                </template>
                <t-radio-group v-model="form.isFrame">
                  <t-radio value="0">是</t-radio>
                  <t-radio value="1">否</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="path" required>
                <template #label>
                  <t-tooltip content="访问的路由地址，如：`user`，如外网地址需内链访问则以`http(s)://`开头">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  路由地址
                </template>
                <t-input v-model="form.path" placeholder="请输入路由地址"/>
              </t-form-item>
            </t-col>
          </t-row>
          <!-- 显示状态 + 菜单状态 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="visible">
                <template #label>
                  <t-tooltip content="选择隐藏则路由将不会出现在侧边栏，但仍然可以访问">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  显示状态
                </template>
                <t-radio-group v-model="form.visible">
                  <t-radio value="0">显示</t-radio>
                  <t-radio value="1">隐藏</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="status">
                <template #label>
                  <t-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  菜单状态
                </template>
                <t-radio-group v-model="form.status">
                  <t-radio value="0">正常</t-radio>
                  <t-radio value="1">停用</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
          </t-row>
        </template>

        <!-- ====== 菜单(C) 特有字段 ====== -->
        <template v-if="form.menuType === 'C'">
          <!-- 是否外链 + 路由地址 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="isFrame">
                <template #label>
                  <t-tooltip content="选择是外链则路由地址需要以`http(s)://`开头">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  是否外链
                </template>
                <t-radio-group v-model="form.isFrame">
                  <t-radio value="0">是</t-radio>
                  <t-radio value="1">否</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="path" required>
                <template #label>
                  <t-tooltip content="访问的路由地址，如：`user`，如外网地址需内链访问则以`http(s)://`开头">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  路由地址
                </template>
                <t-input v-model="form.path" placeholder="请输入路由地址"/>
              </t-form-item>
            </t-col>
          </t-row>
          <!-- 组件路径 + 权限字符 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="component">
                <template #label>
                  <t-tooltip content="访问的组件路径，如：`system/user/index`，默认在`views`目录下">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  组件路径
                </template>
                <t-input v-model="form.component" placeholder="请输入组件路径"/>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="perms">
                <template #label>
                  <t-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasPermi('system:user:list')`)">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  权限字符
                </template>
                <t-input v-model="form.perms" placeholder="请输入权限标识"/>
              </t-form-item>
            </t-col>
          </t-row>
          <!-- 路由参数 + 是否缓存 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="query">
                <template #label>
                  <t-tooltip content='访问路由的默认传递参数，如：`{"id": 1, "name": "ry"}`'>
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  路由参数
                </template>
                <t-input v-model="form.query" placeholder="请输入路由参数"/>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="isCache">
                <template #label>
                  <t-tooltip content="选择是则会被`keep-alive`缓存，需要匹配组件的`name`和地址保持一致">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  是否缓存
                </template>
                <t-radio-group v-model="form.isCache">
                  <t-radio value="0">缓存</t-radio>
                  <t-radio value="1">不缓存</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
          </t-row>
          <!-- 显示状态 + 菜单状态 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="visible">
                <template #label>
                  <t-tooltip content="选择隐藏则路由将不会出现在侧边栏，但仍然可以访问">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  显示状态
                </template>
                <t-radio-group v-model="form.visible">
                  <t-radio value="0">显示</t-radio>
                  <t-radio value="1">隐藏</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="status">
                <template #label>
                  <t-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  菜单状态
                </template>
                <t-radio-group v-model="form.status">
                  <t-radio value="0">正常</t-radio>
                  <t-radio value="1">停用</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
          </t-row>
        </template>

        <!-- ====== 按钮(F) 特有字段 ====== -->
        <template v-if="form.menuType === 'F'">
          <!-- 权限字符 + 菜单状态 - 双列 -->
          <t-row :gutter="16">
            <t-col :span="12">
              <t-form-item name="perms">
                <template #label>
                  <t-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasPermi('system:user:list')`)">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  权限字符
                </template>
                <t-input v-model="form.perms" placeholder="请输入权限标识"/>
              </t-form-item>
            </t-col>
            <t-col :span="12">
              <t-form-item name="status">
                <template #label>
                  <t-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问">
                    <t-icon name="help-circle" style="margin-right: 4px;"/>
                  </t-tooltip>
                  菜单状态
                </template>
                <t-radio-group v-model="form.status">
                  <t-radio value="0">正常</t-radio>
                  <t-radio value="1">停用</t-radio>
                </t-radio-group>
              </t-form-item>
            </t-col>
          </t-row>
        </template>
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
import {ref, reactive, onMounted, computed} from 'vue';
import {menuApi} from '@/api/system/menu';
import type {SysMenu} from '@/api/model/menuModel';
import {MessagePlugin} from 'tdesign-vue-next';
import type {FormInstanceFunctions, PrimaryTableCol} from 'tdesign-vue-next';
import {manifest} from 'tdesign-icons-vue-next';

const tableKey = ref(0);
const loading = ref(false);
const menuList = ref<SysMenu[]>([]);

// 图标列表
const iconOptions = manifest.map((item: { stem: string }) => item.stem);
// 图标弹出面板
const iconPopupVisible = ref(false);
const iconSearch = ref('');

const filteredIconOptions = computed(() => {
  if (!iconSearch.value) return iconOptions;
  const keyword = iconSearch.value.toLowerCase();
  return iconOptions.filter((name: string) => name.toLowerCase().includes(keyword));
});

const selectIcon = (name: string) => {
  form.icon = name;
  iconPopupVisible.value = false;
  iconSearch.value = '';
};

// 搜索
const searchForm = ref({menuName: '', status: ''});

// 表格列
const columns: PrimaryTableCol<SysMenu>[] = [
  {colKey: 'menuName', title: '菜单名称', width: 200, ellipsis: true},
  {colKey: 'icon', title: '图标', width: 80, align: 'center'},
  {colKey: 'orderNum', title: '排序', width: 70, align: 'center'},
  {colKey: 'perms', title: '权限标识', width: 180, ellipsis: true},
  {colKey: 'component', title: '组件路径', width: 200, ellipsis: true},
  {colKey: 'status', title: '状态', width: 80, align: 'center'},
  {colKey: 'operation', title: '操作', width: 180, align: 'center', fixed: 'right'},
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

const form = reactive({...defaultForm});

const rules = {
  menuName: [{required: true, message: '请输入菜单名称', trigger: 'blur'}],
  orderNum: [{required: true, message: '请输入显示排序', trigger: 'blur'}],
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
  return [{menuId: 0, menuName: '主类目', children: menuList.value}];
});

// 删除
const deleteVisible = ref(false);
const deleteRow = ref<SysMenu | null>(null);
const deleteTip = computed(() => {
  const name = deleteRow.value?.menuName || '';
  const childCount = deleteRow.value?.children?.length || 0;
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
  searchForm.value = {menuName: '', status: ''};
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
  Object.assign(form, {...row});
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate();
  if (valid !== true) return;

  try {
    const data = {...form} as SysMenu;
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

// 图标选择器样式
.icon-picker {
  width: 100%;

  .icon-trigger {
    display: flex;
    align-items: center;
    height: 32px;
    padding: 0 8px;
    border: 1px solid var(--td-border-level-1-color);
    border-radius: 3px;
    cursor: pointer;
    user-select: none;

    &:hover {
      border-color: var(--td-brand-color);
    }

    .icon-placeholder {
      color: var(--td-text-color-placeholder);
    }

    .icon-arrow {
      margin-left: auto;
      color: var(--td-text-color-placeholder);
    }
  }
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
}

.icon-grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--td-text-color-secondary);
  transition: all 0.2s;

  &:hover {
    background-color: var(--td-brand-color-light);
    color: var(--td-brand-color);
  }

  &--active {
    background-color: var(--td-brand-color-light);
    color: var(--td-brand-color);
  }
}

.icon-empty {
  text-align: center;
  color: var(--td-text-color-placeholder);
  padding: 24px 0;
}

// 表单双列布局样式
:deep(.t-form) {
  .t-row {
    display: flex;
    flex-wrap: nowrap;
    margin-left: 0 !important;
    margin-right: 0 !important;

    .t-col {
      flex: 0 0 50%;
      max-width: 50%;
      padding-left: 8px;
      padding-right: 8px;
      box-sizing: border-box;

      &:first-child {
        padding-left: 0;
      }

      &:last-child {
        padding-right: 0;
      }

      .t-form__item {
        margin-bottom: 16px;
      }
    }
  }
}
</style>
