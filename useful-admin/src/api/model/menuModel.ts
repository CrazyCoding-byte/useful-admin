/**
 * 菜单模型
 */
export interface SysMenu {
  /**
   * 菜单ID
   */
  menuId?: number;
  
  /**
   * 菜单名称
   */
  menuName?: string;
  
  /**
   * 父菜单ID
   */
  parentId?: number;
  
  /**
   * 显示顺序
   */
  orderNum?: number;
  
  /**
   * 路由地址
   */
  path?: string;
  
  /**
   * 组件路径
   */
  component?: string;
  
  /**
   * 路由参数
   */
  queryParam?: string;
  
  /**
   * 是否为外链
   */
  isFrame?: string;
  
  /**
   * 是否缓存
   */
  isCache?: string;
  
  /**
   * 类型（M目录 C菜单 F按钮）
   */
  menuType?: string;
  
  /**
   * 显示状态
   */
  visible?: string;
  
  /**
   * 菜单状态
   */
  status?: string;
  
  /**
   * 权限字符串
   */
  perms?: string;
  
  /**
   * 菜单图标
   */
  icon?: string;
  
  /**
   * 备注
   */
  remark?: string;
  
  /**
   * 子菜单
   */
  children?: SysMenu[];
}
