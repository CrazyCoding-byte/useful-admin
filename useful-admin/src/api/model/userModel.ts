/**
 * 用户模型
 */
export interface SysUser {
  /**
   * 用户ID
   */
  userId?: number;
  
  /**
   * 用户名
   */
  userName?: string;
  
  /**
   * 密码
   */
  password?: string;
  
  /**
   * 昵称
   */
  nickName?: string;
  
  /**
   * 手机号码
   */
  phonenumber?: string;
  
  /**
   * 邮箱
   */
  email?: string;
  
  /**
   * 状态
   */
  status?: string;
  
  /**
   * 角色ID数组
   */
  roleIds?: number[];
  
  /**
   * 部门ID
   */
  deptId?: number;
  
  /**
   * 岗位ID数组
   */
  postIds?: number[];
  
  /**
   * 二维码
   */
  qrCode?: string;
}

/**
 * 注册用户模型
 */
export interface RegisterUserTo {
  /**
   * 用户名
   */
  userName?: string;
  
  /**
   * 密码
   */
  password?: string;
  
  /**
   * 确认密码
   */
  confirmPassword?: string;
  
  /**
   * 手机号码
   */
  phonenumber?: string;
  
  /**
   * 邮箱
   */
  email?: string;
  
  /**
   * 昵称
   */
  nickName?: string;
}
