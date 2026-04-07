/**
 * 商品分类模型
 */
export interface Category {
  /** 分类ID */
  categoryId?: number;
  /** 分类名称 */
  categoryName?: string;
  /** 分类编码 */
  categoryCode?: string;
  /** 父分类ID */
  parentId?: number;
  /** 分类描述 */
  description?: string;
  /** 状态：0正常 1禁用 */
  status?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 分类树节点
 */
export interface CategoryTree {
  /** 分类ID */
  categoryId?: number;
  /** 分类名称 */
  categoryName?: string;
  /** 父分类ID */
  parentId?: number;
  /** 子分类 */
  children?: CategoryTree[];
}
