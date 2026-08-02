import {request} from '@/utils/request';

// 分类接口
export const categoryApi = {
  /**
   * 获取分类列表
   * @param params 查询参数
   * @returns 分类列表
   */
  getCategoryList: (params: any) => {
    return request.post({
      url: `/product/CategoryList`,
      data: params
    });
  },

  /**
   * 保存分类（新增或修改）
   * @param data 分类数据
   * @returns 操作结果
   */
  saveCategory: (data: any) => {
    return request.post({
      url: '/product/category/save',
      data: data
    });
  },

  /**
   * 删除分类
   * @param ids 分类ID数组
   * @returns 操作结果
   */
  deleteCategory: (ids: number[]) => {
    return request.post({
      url: '/product/category/delete',
      data: ids
    });
  },

  /**
   * 获取分类树
   * @returns 分类树
   */
  getCategoryTree: (catId: number) => {
    return request.get({
      url: `/product/category/parentTree/${catId}`
    });
  }
};
