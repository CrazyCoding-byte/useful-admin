/**
 * 商品模型
 */
export interface Product {
  /** 商品 ID */
  productId?: number;
  /** 商品名称 */
  productName?: string;
  /** 商品描述 */
  description?: string;
  /** 所属分类 ID */
  catalogId?: number;
  /** 品牌 ID */
  brandId?: number;
  /** 品牌名 */
  brandName?: string;
  /** 重量 */
  weight?: number;
  /** 上架状态 [0 - 下架，1 - 上架] */
  publishStatus?: number;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}