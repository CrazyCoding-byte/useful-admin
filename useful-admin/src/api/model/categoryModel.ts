/** 商品分类模型 */
export interface Category {
  catId?: number;
  name?: string;
  parentCid?: number;
  catLevel?: number;
  showStatus?: number;
  sort?: number;
  icon?: string;
  productUnit?: string;
  productCount?: number;
  children?: Category[];
}

/** 分类树节点 */
export interface CategoryTree {
  catId?: number;
  name?: string;
  children?: CategoryTree[];
}
