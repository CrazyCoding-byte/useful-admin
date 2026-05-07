<template>
  <div class="sku-management">
    <!-- 面包屑导航 -->
    <div class="breadcrumb-container">
      <t-breadcrumb>
        <t-breadcrumb-item>SKU规格管理</t-breadcrumb-item>
      </t-breadcrumb>
    </div>

    <!-- 搜索筛选区 -->
    <div class="search-container">
      <t-form :data="formData" :label-width="80" colon @reset="onReset" @submit="onSubmit">
        <t-row>
          <t-col :span="12">
            <t-row :gutter="16">
              <t-col :span="8">
                <t-form-item label="规格组合" name="specCombination">
                  <t-input v-model="formData.specCombination" placeholder="请输入规格组合（如：黑色，256G）"/>
                </t-form-item>
              </t-col>
              <t-col :span="8">
                <t-form-item label="SKU状态" name="status">
                  <t-select v-model="formData.status" :options="STATUS_OPTIONS" placeholder="请选择SKU状态"/>
                </t-form-item>
              </t-col>
            </t-row>
          </t-col>
          <t-col :span="4" class="button-group">
            <t-button theme="primary" type="submit">查询</t-button>
            <t-button type="reset" style="margin-left: 8px">重置</t-button>
          </t-col>
        </t-row>
      </t-form>
    </div>

    <!-- 操作工具栏 -->
    <div class="toolbar-container">
      <t-button theme="primary" @click="handleBatchGenerate">
        <t-icon name="plus"/>
        批量生成SKU
      </t-button>
      <t-button theme="default" @click="handleAdd" style="margin-left: 8px">
        <t-icon name="plus-circle"/>
        新增单个SKU
      </t-button>
      <t-button v-if="selectedRowKeys.length > 0" theme="danger" @click="handleBatchDelete" style="margin-left: 8px">
        <t-icon name="delete"/>
        批量删除
      </t-button>
    </div>

    <!-- SKU数据表格区 -->
    <div class="table-container">
      <t-table
        :data="data"
        :columns="columns"
        :row-key="rowKey"
        :selected-row-keys="selectedRowKeys"
        multiple
        :pagination="pagination"
        :loading="loading"
        @page-change="handlePageChange"
        @select-change="handleSelectChange"
      >
        <template #skuDefaultImg="{ row }">
          <div>
            <t-image
              v-if="row.skuDefaultImg"
              :src="row.skuDefaultImg"
              :preview-src-list="[row.skuDefaultImg]"
              style="width: 80px; height: 80px; object-fit: cover"
              @click="handleImagePreview(row)"
            />
          </div>
        </template>
        <template #skuImage="{ row }">
          <div>
            <t-button
              v-if="row.images && row.images.length > 0"
              theme="primary"
              variant="text"
              size="small"
              @click="handleImagePreview(row)"
            >
              查看{{ row.images.length }}张图片
            </t-button>
            <span v-else style="color: #999">无图</span>
          </div>
        </template>
        <template #status="{ row }">
          <span v-if="row.status === '1'" class="status-tag status-tag-success">上架</span>
          <span v-else class="status-tag status-tag-danger">下架</span>
        </template>
        <template #price="{ row }">
          <span class="price-text">¥{{ (row.price || 0).toFixed(2) }}</span>
        </template>
        <template #specCombination="{ row }">
          <span>{{ row.specCombination }}</span>
        </template>
        <template #op="{ row }">
          <t-button theme="primary" variant="text" @click="handleEdit(row)" style="margin-right: 8px"> 编辑</t-button>
          <t-button
            :theme="row.status === '1' ? 'danger' : 'success'"
            variant="text"
            @click="handleToggleStatus(row)"
            style="margin-right: 8px"
          >
            {{ row.status === '1' ? '下架' : '上架' }}
          </t-button>
          <t-button theme="danger" variant="text" @click="handleDelete(row)"> 删除</t-button>
        </template>
      </t-table>
    </div>

    <!-- 批量生成SKU弹窗 -->
    <t-dialog
      v-model:visible="batchGenerateVisible"
      header="批量生成SKU"
      width="900px"
      :footer="true"
      @confirm="handleBatchGenerateConfirm"
    >
      <div class="batch-generate-container">
        <t-form :data="batchFormData" :rules="batchFormRules">
          <t-form-item label="属性1名称" name="attr1Name">
            <t-input v-model="batchFormData.attr1Name" placeholder="请输入属性名称（如：颜色）"/>
          </t-form-item>
          <t-form-item label="属性1值" name="attr1Values">
            <t-input v-model="batchFormData.attr1Values" placeholder="请输入属性值，用逗号分隔（如：黑色,白色,蓝色）"/>
          </t-form-item>
          <t-form-item label="属性2名称" name="attr2Name">
            <t-input v-model="batchFormData.attr2Name" placeholder="请输入属性名称（如：内存）"/>
          </t-form-item>
          <t-form-item label="属性2值" name="attr2Values">
            <t-input v-model="batchFormData.attr2Values" placeholder="请输入属性值，用逗号分隔（如：128G,256G,512G）"/>
          </t-form-item>
          <t-form-item label="统一价格" name="price">
            <t-input-number
              v-model="batchFormData.price"
              :min="0"
              :step="0.01"
              placeholder="请输入价格"
              style="width: 100%"
            />
          </t-form-item>
          <t-form-item label="统一库存" name="stock">
            <t-input-number v-model="batchFormData.stock" :min="0" placeholder="请输入库存" style="width: 100%"/>
          </t-form-item>
        </t-form>
      </div>
    </t-dialog>

    <!-- 新增/编辑SKU弹窗 -->
    <t-dialog
      v-model:visible="editVisible"
      :header="isEdit ? '编辑SKU' : '新增SKU'"
      width="700px"
      :footer="true"
      @confirm="handleEditConfirm"
    >
      <t-form :data="skuFormData" :rules="skuFormRules">
        <t-form-item label="SKU名称" name="skuName">
          <t-input v-model="skuFormData.skuName" placeholder="请输入SKU名称"/>
        </t-form-item>
        <t-form-item label="SKU编码" name="skuCode">
          <t-input v-model="skuFormData.skuCode" placeholder="请输入SKU编码"/>
        </t-form-item>
        <t-form-item label="销售价格" name="price">
          <t-input-number
            v-model="skuFormData.price"
            :min="0"
            :step="0.01"
            placeholder="请输入销售价格"
            style="width: 100%"
          />
        </t-form-item>
        <t-form-item label="库存" name="stock">
          <t-input-number v-model="skuFormData.stock" :min="0" placeholder="请输入库存" style="width: 100%"/>
        </t-form-item>
        <t-form-item label="默认图片" name="skuDefaultImg">
          <t-input v-model="skuFormData.skuDefaultImg" placeholder="请输入默认图片URL"/>
        </t-form-item>
        <t-form-item label="SKU标题" name="skuTitle">
          <t-textarea v-model="skuFormData.skuTitle" placeholder="请输入SKU标题"/>
        </t-form-item>
        <t-form-item label="SKU副标题" name="skuSubtitle">
          <t-textarea v-model="skuFormData.skuSubtitle" placeholder="请输入SKU标题"/>
        </t-form-item>
        <t-form-item label="SKU描述" name="skuDesc">
          <t-textarea v-model="skuFormData.skuDesc" placeholder="请输入SKU标题"/>
        </t-form-item>
        <t-form-item label="状态" name="publishStatus">
          <t-radio-group v-model="skuFormData.publishStatus">
            <t-radio :value=1>上架</t-radio>
            <t-radio :value=0>下架</t-radio>
          </t-radio-group>
        </t-form-item>
        <t-form-item label="规格组合" name="specCombination">
        <div style="display: flex;flex-direction: column;flex-wrap: nowrap;gap: 10px; align-items: center;">
            <div v-for="(item,index) in attributes" :key="index"
                 style="display: flex;flex-direction: row;gap: 10px; align-items: center;">
              <t-select v-model="item.attrName" :options="attrOptions" size="small"
                        @change="(value) => onFocus(value, index)"/>
              <t-select v-model="item.attrValue" :options="attrValueOptions" size="small"></t-select>
              <t-button @click="removeAttribute(index)" theme="danger">删除</t-button>
            </div>
          <t-button @click="addAttribute">添加</t-button>
        </div>
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 删除确认弹窗 -->
    <t-dialog v-model:visible="deleteVisible" header="确认删除" :footer="true" @confirm="handleDeleteConfirm">
      <div>删除后无法恢复，是否继续？</div>
    </t-dialog>

    <!--图片弹出框 -->
    <t-dialog v-model:visible="showImage" width="900px" @confirm="handleSubmitImage">
      <!--      这里应该显示一个图片-->
      <t-upload
        ref="uploadRef2"
        v-model="imageData"
        action="http://localhost:8781/api/file/upload"
        theme="image"
        accept="image/*"
        :disabled="disabled"
        :auto-upload="autoUpload"
        :upload-all-files-in-one-request="uploadAllFilesInOneRequest"
        :show-image-file-name="showImageFileName"
        multiple
        :max="9"
        :format-response="formatResponse"
        @success="handleUploadSuccess"
        @fail="handleFail"
      ></t-upload>
      <t-button
        v-for="(item,index) in imageData"
        theme="primary"
        @click="handleSetDefault(item)"
      >设置默认图片
      </t-button>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted, h} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {
  MessagePlugin,
  TableRowData,
  PageInfo,
  UploadProps,
  UploadInstanceFunctions,
  ButtonProps,
  SelectProps,
  PrimaryTableCol
} from 'tdesign-vue-next';
import {request} from '@/utils/request';
import {productApi} from '@/api/product';
import {title} from 'process';

const autoUpload = ref(true);
const route = useRoute();
const productId = route.params.productId;
const spuName = route.params.spuName;
const showImage = ref(false);
const showImageFileName = ref(true);

/**
 * t-upload组件
 * accept 接受的类型
 * action 后端接口
 * allowUploadDuplicateFile 是否允许重复上传相同文件名的文件
 * beforeAllFilesUpload 	如果是自动上传模式 autoUpload=true，
 * 表示全部文件上传之前的钩子函数，函数参数为上传的文件，函数返回值决定是否继续上传，若返回值为 false 则终止上传。
 * beforeUpload 	如果是自动上传模式 autoUpload=true，表示单个文件上传之前的钩子函数，若函数返回值为 false 则表示不上传当前文件
 * data -	上传请求所需的额外字段，默认字段有 file，表示文件信息。可以添加额外的文件名字段
 * disabled 是否禁用
 * files  已上传文件列表，同 value。
 * defaultFiles 已上传文件列表，同 value
 *
 * formatResponse 	用于格式化文件上传后的接口响应数据，response 便是接口响应的原始数据。action 存在时有效。
 * 示例返回值：{ error, url, status, files }
 * 此函数的返回值 error 会作为错误文本提醒，表示上传失败的原因，如果存在会判定为本次上传失败。
 * 此函数的返回值 url 会作为单个文件上传成功后的链接。
 *
 * imageViewerProps 透传图片预览组件全部属性。
 */

//上传图片
const imageData = ref<UploadProps['value']>([]);

// 状态选项
const STATUS_OPTIONS = [
  {label: '上架', value: '1'},
  {label: '下架', value: '0'},
];

// 搜索表单数据
const formData = ref({
  specCombination: '',
  status: '',
});

// 表格数据
const data = ref<any[]>([]);
const loading = ref(false);
const rowKey = 'skuId';
const selectedRowKeys = ref<string[]>([]);
const showImages = ref<string[]>([]);
// 分页数据
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
});
// 表格列定义
const columns: PrimaryTableCol<any>[] = [
  {type: 'multiple', width: 60, fixed: 'left'},
  {
    title: 'SKU ID',
    colKey: 'skuId',
    width: 100,
    fixed: 'left',
  },
  {
    title: 'SKU名称',
    colKey: 'skuName',
    ellipsis: true,
    width: 200,
    fixed: 'left',
  },
  {
    title: 'SKU标题',
    colKey: 'skuTitle',
    ellipsis: true,
    width: 200,
  },
  {
    title: 'SKU副标题',
    colKey: 'skuSubtitle',
    ellipsis: true,
    width: 200,
  },
  {
    title: '默认图片',
    colKey: 'skuDefaultImg',
    width: 200,
  },
  {
    title: 'SKU描述',
    colKey: 'skuDesc',
    ellipsis: true,
    width: 200,
  },

  {
    title: 'SKU图片',
    colKey: 'skuImage',
    width: 200,
  },
  {
    title: '销售属性',
    colKey: 'saleAttrValues',
    ellipsis: true,
    cell: (h, {row}) => {
      //格式:属性名:属性值/属性名:属性值
      const attrs = row.saleAttrValues?.map((item) => `${item.attrName}:${item.attrValue}`).join('/');
      return h('span', {attrs: {title: attrs}}, attrs || '-');
    },
    width: 200,
  },
  {
    title: '销售数量',
    colKey: 'saleCount',
    width: 200,
  },
  {
    title: '价格',
    colKey: 'price',
    width: 200,
  },
  {
    title: '库存',
    colKey: 'stock',
    width: 200,
  },
  {
    title: '状态',
    colKey: 'status',
    width: 200,
  },
  {
    title: '操作',
    colKey: 'op',
    width: 200,
    fixed: 'right',
  },
];

// 批量生成SKU表单数据
const batchGenerateVisible = ref(false);
const batchFormData = ref({
  attr1Name: '',
  attr1Values: '',
  attr2Name: '',
  attr2Values: '',
  price: 0,
  stock: 0,
});

const batchFormRules = {
  attr1Name: [{required: true, message: '请输入属性1名称', trigger: 'blur'}],
  attr1Values: [{required: true, message: '请输入属性1值', trigger: 'blur'}],
  attr2Name: [{required: true, message: '请输入属性2名称', trigger: 'blur'}],
  attr2Values: [{required: true, message: '请输入属性2值', trigger: 'blur'}],
  price: [{required: true, message: '请输入统一价格', trigger: 'blur'}],
  stock: [{required: true, message: '请输入统一库存', trigger: 'blur'}],
};

const editVisible = ref(false);
const isEdit = ref(false);
const currentSku = ref<any>(null);
const skuFormRef = ref();
const skuFormData = ref({
  skuId: undefined,
  spuId: '',
  skuName: '',
  skuCode: '',
  price: 0,
  stock: 0,
  skuDefaultImg: '',
  skuTitle: '',
  skuSubtitle: '',
  skuDesc: '',
  publishStatus: '1',
  specCombination: [],
});

const skuFormRules = {
  skuName: [{required: true, message: '请输入 SKU 名称', trigger: 'blur'}],
  skuCode: [{required: true, message: '请输入 SKU 编码', trigger: 'blur'}],
  price: [{required: true, message: '请输入销售价格', trigger: 'blur'}],
  stock: [{required: true, message: '请输入库存', trigger: 'blur'}],
};
// 删除确认弹窗
const deleteVisible = ref(false);
const deleteIds = ref<string[]>([]);
const selectSkuId = ref<string>('');
const dialogMode = ref(''); //'view'=查看图片
const attributes = ref([{
  attrName: '', attrValue: ''
}])
//规格select选择框
const attrOptions = ref<SelectProps['options']>([]);
const attrValueOptions = ref<SelectProps['options']>([]);
const value1 = ref('');
const value2 = ref('');
const onFocus = (value, index) => {
  attrValueOptions.value = []
  attrOptions.value.forEach(item => {
    if (value == item.value) {
      item.valueSelect.forEach(obj => {
        attrValueOptions.value.push({label: obj, value: obj})
      })
      console.log("attrValueOptions", attrValueOptions.value)
    }
  })
};

//*********************************************方法**************************************/
// 页面加载时获取数据
onMounted(() => {
  fetchSkuList();
});
const clearTable = () => {
  data.value = []; //清空数据
  selectedRowKeys.value = [];//清空选中
  pagination.value.current = 1;//清空分页
  pagination.value.total = 0;//清空分页
}
// 获取SKU列表
const fetchSkuList = async () => {
  clearTable();
  loading.value = true;
  try {
    const res = await request.post({
      url: `/product/getSkuInfoBySpuId/${productId}/${pagination.value.current}/${pagination.value.pageSize}`,
    });
    data.value = res.records;
    pagination.value.total = res.total || 0;
    console.log('获取到的数据2212312321321', data.value);
  } catch (error) {
    console.error('获取SKU列表失败:', error);
    MessagePlugin.error('获取SKU列表失败');
  } finally {
    loading.value = false;
  }
};
//获取attr列表
const fetchAttr = (categoryId: number) => {
  /**
   *   {
   *     label: '人工智能',
   *     value: '5',
   *   },
   */
  if (!categoryId) {
    return;
  }
  productApi.getAttrByCateGoryId(categoryId).then(resp => {
    console.log("获取到的attr列表", resp)
    attrOptions.value = resp.map((attr) => ({
      label: attr.attrName,
      value: attr.attrId,
      valueSelect: attr.valueSelect.split(';'),
    }))
    console.log("attrOptions", attrOptions.value)
  })
}
//新增attr选择下拉框
const addAttribute = () => {
  attributes.value.push({attrName: '', attrValue: ''})
}
//删除attr选择下拉框
const removeAttribute = (index) => {
  attributes.value.splice(index, 1)
}
//上传图片成功回调
const handleUploadSuccess: UploadProps['onSuccess'] = (params) => {
  console.log(params);
};
//上传图片之后的回显
const formatResponse: UploadProps['formatResponse'] = (response) => {
  console.log('后端返回:', response); // 调试用
  // 根据Postman返回格式解析
  if (response.code === 200) {
    const filePath = response.data.filePath;
    const previewUrl = `http://localhost:8781/api/file/preview?filePath=${encodeURIComponent(filePath)}`;
    console.log('预览URL:', previewUrl);
    return {
      url: previewUrl,
      name: response.data.fileName,
      filePath: filePath,
    };
  }

  // 上传失败
  return {
    error: response.msg || '上传失败',
  };
};
//确定修改图片状态
const handleSubmitImage = () => {
  alert('确认保存图片被触发');
  console.log('ref2图片上传被触发', imageData.value);
  const filePath = [];
  imageData.value.forEach((file) => {
    filePath.push(file.url);
  });
  productApi.updateSkuImage(selectSkuId.value, filePath).then((resp) => {
    console.log('响应的结果', resp);
    fetchSkuList();
  });
};
// 查询
const onSubmit = () => {
  pagination.value.current = 1;
  fetchSkuList();
};

// 重置
const onReset = () => {
  formData.value = {
    specCombination: '',
    status: '',
  };
  pagination.value.current = 1;
  fetchSkuList();
};
const handleImagePreview = (row) => {
  selectSkuId.value = row.skuId;
  imageData.value = [];
  imageData.value =
    row.images
      ?.filter((img) => img.imgUrl)
      ?.map((img) => ({
        url: img.imgUrl,
        imgId: img.id,
        skuId: img.skuId,
        name: img.skuDefaultImg,
        isDefault: img.skuDefaultImg
      })) || [];
  showImage.value = true;
  console.log("被点击的row", row)
  console.log('图片数据', imageData.value)
};
//设置默认图片接口
const handleSetDefault = (item) => {
  console.log("设置修改的item", item)
  productApi.updateDefaultImage(item.skuId, item.imgId).then(resp => {
    console.log('设置默认图片接口返回结果', resp)
    if (resp && resp.code == 200) {
      fetchSkuList()
      showImage.value = false;
    } else {
      MessagePlugin.error('设置默认图片失败')
    }
  })
}
// 分页变化
const handlePageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  fetchSkuList();
};

// 选择变化
const handleSelectChange = (value: string[]) => {
  selectedRowKeys.value = value;
};

// 批量生成SKU
const handleBatchGenerate = () => {
  batchGenerateVisible.value = true;
};

// 批量生成SKU确认
const handleBatchGenerateConfirm = async () => {
  try {
    // 生成属性组合的笛卡尔积
    const attr1Values = batchFormData.value.attr1Values.split(',').map((v) => v.trim());
    const attr2Values = batchFormData.value.attr2Values.split(',').map((v) => v.trim());
    const combinations = [];
    for (const attr1 of attr1Values) {
      for (const attr2 of attr2Values) {
        combinations.push({
          specCombination: `${batchFormData.value.attr1Name}：${attr1}，${batchFormData.value.attr2Name}：${attr2}`,
          skuName: `${attr1} ${attr2}`,
          skuCode: `${attr1}-${attr2}`,
          price: batchFormData.value.price,
          stock: batchFormData.value.stock,
          status: '1',
        });
      }
    }

    await request.post({
      url: '/product/sku/batchCreate',
      data: {
        skus: combinations,
      },
    });

    MessagePlugin.success('批量生成SKU成功');
    batchGenerateVisible.value = false;
    // 重置表单
    batchFormData.value = {
      attr1Name: '',
      attr1Values: '',
      attr2Name: '',
      attr2Values: '',
      price: 0,
      stock: 0,
    };
    // 刷新列表
    fetchSkuList();
  } catch (error) {
    console.error('批量生成SKU失败:', error);
    MessagePlugin.error('批量生成SKU失败');
  }
};

// 新增单个SKU
const handleAdd = () => {
  isEdit.value = false;
  currentSku.value = null;
  skuFormData.value = {
    skuId: undefined,
    spuId: '',
    skuName: '',
    skuCode: '',
    price: 0,
    stock: 0,
    skuDefaultImg: '',
    skuTitle: '',
    skuSubtitle: '',
    skuDesc: '',
    publishStatus: '1',
    specCombination: '',
  };
  editVisible.value = true;
};

// 编辑SKU
const handleEdit = (row: any) => {
  console.log("需要修改的row", row)
  isEdit.value = true;
  currentSku.value = row;
  skuFormData.value = {
    skuId: row.skuId,
    spuId: row.spuId,
    skuName: row.skuName,
    skuCode: row.skuCode,
    price: row.price,
    stock: row.stock,
    skuDefaultImg: row.skuDefaultImg,
    skuTitle: row.skuTitle,
    skuSubtitle: row.skuSubtitle,
    skuDesc: row.skuDesc,
    publishStatus: row.publishStatus,
    specCombination: row.specCombination,
  };
  editVisible.value = true;
  attrOptions.value = []
  fetchAttr(row.catalogId)
};

// 编辑 SKU 确认
const handleEditConfirm = async () => {
  // 1. 手动校验规格组合
  if (!attributes.value || attributes.value.length === 0) {
    MessagePlugin.warning('请添加至少一个规格组合');
    return;
  }
  const hasEmptyAttr = attributes.value.some(item => !item.attrName || !item.attrValue);
  if (hasEmptyAttr) {
    MessagePlugin.warning('请完整填写所有规格项');
    return;
  }

  // 2. 触发表单其他字段校验
  const result = await skuFormRef.value.validate();
  if (result !== true) return;

  try {
    // 3. 将 attributes 数组转换为规格组合字符串
    const specCombinationStr = attributes.value
      .map(item => {
        const attrLabel = attrOptions.value.find(o => o.value === item.attrName)?.label || item.attrName;
        return `${attrLabel}:${item.attrValue}`;
      })
      .join('/');

    skuFormData.value.specCombination = specCombinationStr;

    if (isEdit.value) {
      await request.put({
        url: `/product/sku/${skuFormData.value.skuId}`,
        data: skuFormData.value,
      });
      MessagePlugin.success('编辑 SKU 成功');
    } else {
      await request.post({
        url: '/product/sku',
        data: skuFormData.value,
      });
      MessagePlugin.success('新增 SKU 成功');
    }
    editVisible.value = false;
    fetchSkuList();
  } catch (error) {
    console.error('保存 SKU 失败:', error);
    MessagePlugin.error('保存 SKU 失败');
  }
};

// 切换SKU状态
const handleToggleStatus = async (row: any) => {
  try {
    await request.put({
      url: `/product/sku/${row.skuId}/status`,
      data: {
        status: row.status === '1' ? '0' : '1',
      },
    });
    MessagePlugin.success(`SKU${row.status === '1' ? '下架' : '上架'}成功`);
    // 刷新列表
    fetchSkuList();
  } catch (error) {
    console.error('切换SKU状态失败:', error);
    MessagePlugin.error('切换SKU状态失败');
  }
};

// 删除SKU
const handleDelete = (row: any) => {
  deleteIds.value = [row.skuId];
  deleteVisible.value = true;
};

// 批量删除SKU
const handleBatchDelete = () => {
  if (selectedRowKeys.value.length === 0) {
    MessagePlugin.warning('请选择要删除的SKU');
    return;
  }
  deleteIds.value = selectedRowKeys.value;
  deleteVisible.value = true;
};

// 删除确认
const handleDeleteConfirm = async () => {
  try {
    await request.delete({
      url: `/product/sku`,
      data: deleteIds.value,
    });
    MessagePlugin.success('删除SKU成功');
    deleteVisible.value = false;
    // 刷新列表
    fetchSkuList();
  } catch (error) {
    console.error('删除SKU失败:', error);
    MessagePlugin.error('删除SKU失败');
  }
};
</script>

<style lang="less" scoped>
.sku-management {
  padding: 24px;
  background-color: #f5f5f5;

  .breadcrumb-container {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
  }

  .search-container {
    background-color: white;
    padding: 24px;
    border-radius: 8px;
    margin-bottom: 16px;

    .button-group {
      display: flex;
      justify-content: flex-end;
      align-items: center;
    }
  }

  .toolbar-container {
    background-color: white;
    padding: 16px 24px;
    border-radius: 8px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
  }

  .table-container {
    background-color: white;
    border-radius: 8px;
    padding: 16px;
  }

  .no-image {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f0f0f0;
    border-radius: 4px;
    color: #999;
  }

  .status-tag {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
  }

  .status-tag-success {
    background-color: #e6f7ee;
    color: #00b42a;
    border: 1px solid #b7eb8f;
  }

  .status-tag-danger {
    background-color: #fff2f0;
    color: #f5222d;
    border: 1px solid #ffccc7;
  }

  .price-text {
    color: #f5222d;
    font-weight: 600;
  }

  .batch-generate-container {
    padding: 16px;
  }
}
</style>
