<script setup lang="ts">
import {ref, watch} from 'vue';
import {MessagePlugin} from "tdesign-vue-next";
import {couponApi} from "@/api/marketing/coupon";
import {productApi} from "@/api/product";
import {categoryApi} from '@/api/shop/category';

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
const categoryTree = ref<any>([]);
const selectedCategoryIds = ref<number[]>([]);
//已有的规则回显
const loadExistingRange = async () => {
  if (!props.couponInfo.id) return;
  try {
    const res = await couponApi.getRangeList(props.couponInfo.id);
    const list = res?.data || [];
    if (props.couponInfo.rangeType === 'SKU') {
      selectedSkuIds.value = list.map((r: any) => r.rangeId);

    } else if (props.couponInfo.rangeType === 'CATEGORY') {
      selectedCategoryIds.value = list.map((r: any) => r.rangeId);
    }
  } catch (e) {
  }
}
const loadSkuList = async () => {
  try {
    const res = await productApi.getProductLists({pageNum: 1, pageSize: 200});
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
    categoryTree.value = res?.data || res || [];

  } catch (e) {
  }
}
watch(() => props.modelValue, (val) => {
  if (!val) return;
  if (props.couponInfo.rangeType === "SKU") loadSkuList();
  else if (props.couponInfo.rangeType === "CATEGORY")
    loadCategoryTree();
  loadExistingRange();
})
const handleSave = async () => {
  submitLoading.value = true;
  try {
    const rangeList = [];
    if (props.couponInfo.rangeType === 'SKU') {
      selectedSkuIds.value.forEach(skuId => {
        rangeList.push({rangeType: "SKU", rangeId: skuId});
      })
    } else if (props.couponInfo.rangeType === "CATEGORY") {
      selectedCategoryIds.value.forEach(catId => {
        rangeList.push({rangeType: 'CATEGORY', rangeId: catId})
      })
    }
    await couponApi.saveRange(props.couponInfo.id, rangeList);
    MessagePlugin.success("规则保存成功");
    emit('saved');
    visible.value = false;
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
</script>

<template>
  <t-dialog v-model:visible="visible" header="配置优惠券规则" width="800px"
            :confirm-btn="{ content: '保存', loading: submitLoading }"
            @confirm="handleSave" @close="handleClose">
    <!--    通用卷提示-->
    <t-alert v-if="couponInfo.rangeType==='ALL'" theme="info" message="通用卷无需配置规则,所有商品都可以使用">
    </t-alert>
    <!--    sku适用范围-->
    <div v-else-if="couponInfo.rangeType==='SKU'" class="range-sku">
      <t-transfer v-model="selectedSkuIds"
                  :data="skuList"
                  :keys="{label:'skuName',value:'id'}"
                  :search="true"
                  :title="['可选商品','已选商品']"
                  style="width:100%"
      ></t-transfer>
    </div>
    <!--    分类适用范围-->
    <div v-else-if="couponInfo.rangeType==='CATEGORY'" class="range-category">
      <t-tree
        v-model="selectedCategoryIds"
        :data="categoryTree"
        checkable
        expand-all
        value-mode="all"
        :keys="{label:'name',value:'id'}"
      >
      </t-tree>
    </div>
  </t-dialog>
</template>

<style scoped lang="less">

</style>
