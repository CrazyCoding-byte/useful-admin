import {request} from '@/utils/request';
import type {Product} from '@/api/model/productModel';

/**
 * 商品管理API
 */
export const productApi = {
  /**
   * 获取商品列表
   * @param params 查询参数
   * @returns 商品列表数据
   */
  getProductList: (params: any) => {
    const {pageNum = 1, pageSize = 10, ...productParams} = params;
    return request.post({
      url: `/product/list/${pageNum}/${pageSize}`,
      data: productParams,
    });
  },

  /**
   * 获取商品详情
   * @param id 商品 ID
   * @returns 商品信息
   */
  getProductDetail: (id: string) => {
    return request.get({
      url: `/product/${id}`,
    });
  },

  /**
   * 新增或修改商品
   * @param product 商品信息
   * @returns 操作结果
   */
  saveProduct: (product: any) => {
    return request.post({
      url: '/product',
      data: product,
    });
  },

  /**
   * 删除商品
   * @param productIds 商品 ID 数组
   * @returns 删除结果
   */
  deleteProduct: (productIds: string[]) => {
    return request.delete({
      url: `/product/${productIds.join(',')}`,
    });
  },

  /**
   * 上架商品
   * @param spuId 商品 ID
   * @returns 操作结果
   */
  upProduct: (spuId: string) => {
    return request.post({
      url: `/product/upPd/${spuId}`,
    });
  },

  /**
   * 下架商品
   * @param spuId 商品 ID
   * @returns 操作结果
   */
  downProduct: (spuId: string) => {
    return request.post({
      url: `/product/downPd/${spuId}`,
    });
  },

  /**
   * 修改sku图片
   */
  updateSkuImage: (skuId: string, imgUrl: string[]) => {
    return request.post({
      url: `/product/updateSkuImage/${skuId}`,
      data: imgUrl
    })
  },
  /**
   * 修改默认图片
   */
  updateDefaultImage: (skuId: string, imgId: string) => {
    return request.post({
      url: `/product/setSkuDefaultImg/${skuId}/${imgId}`,
    })
  },

  /**
   * 获取attr
   *
   */
  getAttrByCateGoryId: (categoryId: number) => {
    return request.get({
      url: `/product/getAttrByCategoryId/${categoryId}`
    })
  }
};
