/**
 * 角色模型
 */
export interface SysRole {
  /**
   * 角色ID
   */
  roleId?: number;
  
  /**
   * 角色名称
   */
  roleName?: string;
  
  /**
   * 角色权限
   */
  roleKey?: string;
  
  /**
   * 角色排序
   */
  roleSort?: number;
  
  /**
   * 数据范围
   */
  dataScope?: string;
  
  /**
   * 角色状态
   */
  status?: string;
  
  /**
   * 备注
   */
  remark?: string;
  
  /**
   * 部门ID数组
   */
  deptIds?: number[];
  
  /**
   * 菜单权限
   */
  menuIds?: number[];
}
