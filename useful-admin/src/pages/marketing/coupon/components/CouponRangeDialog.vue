<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { SearchIcon } from "tdesign-icons-vue-next";
import { MessagePlugin } from "tdesign-vue-next";
import { couponApi } from "@/api/marketing/coupon";
import { productApi } from "@/api/product";
import { categoryApi } from '@/api/shop/category';
import { CategoryNode } from '@/api/model/categoryModel'

const props = defineProps<{ modelValue: boolean; couponInfo: Record<string, any>; }>();
const emit = defineEmits<{ (e: "update:modelValue", val: boolean): void; (e: 'saved'): void }>()
const visible = ref(props.modelValue);
watch(() => props.modelValue, (v) => {
  visible.value = v;
});
watch(visible, (v) => emit('update:modelValue', v));
const submitLoading = ref(false);
const skuList = ref<any[]>([]);
const selectedSkuIds = ref<number[]>([]);
const categoryTree = ref<CategoryNode[]>([]);
const selectedCategoryIds = ref<number[]>([]);
//分类树搜索过滤
const filterText = ref('');
const filterByText = ref();
const treeRef = ref<any>();

//加载一级分类
const loadRootCategories = async () => {
  try {
    const res = await categoryApi.getCategoryChildren(0);
    const tree = Array.isArray(res) ? res : res?.data || [];
    categoryTree.value = tree;
  } catch (e: any) {
    console.error("加载一级分类失败:", e);
    MessagePlugin.error(e?.message || '分类加载失败')
  }
}
//点击展开时加载子分类
const loadCategoryChildren = async (node: CategoryNode) => {
  try {
    const res = await categoryApi.getCategoryChildren(node.catId);
    const children = Array.isArray(res) ? res : res?.data || [];
    node.children = children;
  } catch (e: any) {
    console.error(
      `加载分类 ${node.catId} 的子分类失败：`,
      e
    );

    MessagePlugin.error(
      e?.message || '子分类加载失败'
    );
  }
}
//收集树中所有节点id（搜索时展开全部，让匹配结果可见
const collectAllIds = (nodes: any[]): number[] => {
  const result: number[] = [];
  const walk = (list: any[]) => {
    list.forEach((node: any) => {
      result.push(node.catId);
      if (node.children?.length) walk(node.children);
    });
  };
  walk(nodes);
  return result;
};
const onCategoryInput = async (value?: string) => {
  const keyword = (value ?? filterText.value ?? '').trim();
  if (!treeRef.value) return;
  if (!keyword) {
    //清空搜索：恢复只展开第一级
    filterByText.value = undefined;
    return;
  }
  filterByText.value = (node: any) => {
    const name = node.name || '';
    return name.indexOf(keyword) >= 0;
  };
  await nextTick();
  try {
    treeRef.value?.setExpanded(collectAllIds(categoryTree.value));
  } catch (e) {
    MessagePlugin.error("展开分类树失败" + e);
  }
  //搜索时展开所有节点，让匹配结果可见
};

//找到节点到根节点的路径id，回显时展开，让用户能看到已选分类位置
const findPathToRoot = (nodes: any[], targetId: number, path: number[] = []): number[] => {
  for (const node of nodes) {
    const newPath = [...path, node.catId];
    if (node.catId === targetId) return newPath;
    if (node.children?.length) {
      const found = findPathToRoot(node.children, targetId, newPath);
      if (found.length) return found;
    }
  }
  return [];
};

//展开指定id及其祖先链
const expandPaths = async (targetIds: number[]) => {
  const pathIds = new Set<number>();
  targetIds.forEach(id => {
    findPathToRoot(categoryTree.value, id).forEach(x => pathIds.add(x));
  });
  await nextTick();
  if (!treeRef.value || pathIds.size === 0) return;
  try {
    treeRef.value.setExpanded([...pathIds]);
  } catch (e) {
    console.error('展开已选分类失败：', e);
  }
};

//已有的规则回显
const loadExistingRange = async () => {
  if (!props.couponInfo.id) return;
  try {
    const res = await couponApi.getRangeList(props.couponInfo.id);
    console.log("已有规则回显", res);
    const list = Array.isArray(res) ? res : res?.data || [];
    if (props.couponInfo.rangeType === "SKU") {
      selectedSkuIds.value = list.map((item: any) => item.rangeId)
    }
    if (props.couponInfo.rangeType === 'CATEGORY') {
      selectedCategoryIds.value = list.map((item: any) => item.rangeId);
    }
    await expandPaths(selectedCategoryIds.value);
  } catch (e) {
    console.log("已有规则加载失败", e);
    MessagePlugin.error("已有规则加载失败");
  }
}

const loadSkuList = async () => {
  try {
    const res = await productApi.getProductList({ pageNum: 1, pageSize: 200 });
    skuList.value = (res?.list || res?.data?.list || []).map((item: any) => ({
      id: item.id,
      skuName: item.skuName || item.spuName || `商品${item.id}`
    }))

  } catch (e) {

  }
}

//加载分类树
const loadCategoryTree = async () => {
  try {
    const res = await categoryApi.getCategoryTree();
    console.log('分类接口返回结果：', res);
    const tree = Array.isArray(res) ? res : res?.data || [];
    if (!Array.isArray(tree)) {
      throw new Error('分类接口返回的数据不是数组');
    }
    categoryTree.value = tree;
  } catch (e: any) {
    console.error('分类树请求失败：', e);
    console.error('响应数据：', e?.response?.data);
    console.error('错误信息：', e?.message);

    MessagePlugin.error(
      e?.message || '分类加载失败'
    );
  }
}

watch(() => props.modelValue, async (val) => {
  console.log("当前props是什么", props)
  if (!val) return;
  resetSelectRange();
  if (props.couponInfo.rangeType === 'SKU') {
    await loadSkuList();
  }
  if (props.couponInfo.rangeType === 'CATEGORY') {
    await loadCategoryTree();
  }
  await nextTick();
  await loadExistingRange();
})
const handleSave = async () => {
  submitLoading.value = true;
  try {
    const rangeList: Array<{
      rangeType: string;
      rangeId: number;
    }> = [];
    if (props.couponInfo.rangeType === 'SKU') {
      selectedSkuIds.value.forEach((skuId) => {
        rangeList.push({
          rangeType: 'SKU',
          rangeId: skuId,
        });
      })
    }
    if (props.couponInfo.rangeType === 'CATEGORY') {
      selectedCategoryIds.value.forEach((categoryId) => {
        rangeList.push({
          rangeType: 'CATEGORY',
          rangeId: categoryId,
        })
      })
    }
    await couponApi.saveRange(props.couponInfo.id,
      rangeList
    );
    MessagePlugin.success("规则保存成功");
    emit('saved');
    visible.value = false
  } catch (e) {
    MessagePlugin.error("规则保存失败");
  } finally {
    submitLoading.value = false;
  }
}
const handleClose = () => {
  selectedSkuIds.value = [];
  selectedCategoryIds.value = [];
}
const resetSelectRange = () => {
  selectedSkuIds.value = [];
  selectedCategoryIds.value = [];
  filterText.value = '';
  filterByText.value = undefined;
}
</script>

<template>
  <t-dialog v-model:visible="visible" header="配置优惠券规则" width="800px"
    :confirm-btn="{ content: '保存', loading: submitLoading }" @confirm="handleSave" @close="handleClose">
    <!--    通用卷提示-->
    <t-alert v-if="couponInfo.rangeType === 'ALL'" theme="info" message="通用卷无需配置规则,所有商品都可以使用">
    </t-alert>
    <!--    sku适用范围-->
    <div v-else-if="couponInfo.rangeType === 'SKU'" class="range-sku">
      <t-transfer v-model="selectedSkuIds" :data="skuList" :keys="{ label: 'skuName', value: 'id' }" :search="true"
        :title="['可选商品', '已选商品']" style="width:100%"></t-transfer>
    </div>
    <!--    分类适用范围-->
    <div v-else-if="couponInfo.rangeType === 'CATEGORY'" class="range-category">
      <t-input v-model="filterText" placeholder="请输入分类名称搜索" clearable @change="onCategoryInput">
        <template #suffix-icon>
          <search-icon size="20px" />
        </template>
      </t-input>
      <t-tree ref="treeRef" v-model="selectedCategoryIds" :data="categoryTree" checkable expand-on-click-node
        value-mode="only" :filter="filterByText" :keys="{ label: 'name', value: 'catId' ,children:'children'}" :load="loadCategoryChildren">
      </t-tree>
    </div>
  </t-dialog>
</template>

<style scoped lang="less">
.range-category {
  :deep(.t-input) {
    margin-bottom: 12px;
  }
}
</style>
