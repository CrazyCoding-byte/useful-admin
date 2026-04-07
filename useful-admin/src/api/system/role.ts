import { request } from '@/utils/request';
import type { SysRole } from '@/api/model/roleModel';

/**
 * 角色管理API
 */
export const roleApi = {
  /**
   * 获取角色列表
   * @param params 查询参数
   * @returns 角色列表数据
   */
  getRoleList: (params: Partial<SysRole>) => {
    return request.get({
      url: '/system/role/list',
      params,
    });
  },

  /**
   * 获取角色详细信息
   * @param roleId 角色ID
   * @returns 角色详细信息
   */
  getRoleInfo: (roleId: number) => {
    return request.get({
      url: `/system/role/${roleId}`,
    });
  },

  /**
   * 新增角色
   * @param role 角色信息
   * @returns 新增结果
   */
  addRole: (role: SysRole) => {
    return request.post({
      url: '/system/role',
      data: role,
    });
  },

  /**
   * 修改保存数据权限
   * @param role 角色信息
   * @returns 修改结果
   */
  updateDataScope: (role: { roleId: number; dataScope: string; deptIds?: number[] }) => {
    return request.put({
      url: '/system/role/dataScope',
      data: role,
    });
  },

  /**
   * 修改角色状态
   * @param role 角色信息
   * @returns 修改结果
   */
  changeStatus: (role: { roleId: number; status: string }) => {
    return request.put({
      url: '/system/role/changeStatus',
      data: role,
    });
  },

  /**
   * 删除角色
   * @param roleIds 角色ID数组
   * @returns 删除结果
   */
  deleteRole: (roleIds: number[]) => {
    return request.delete({
      url: `/system/role/${roleIds.join(',')}`,
    });
  },

  /**
   * 获取角色选择框列表
   * @returns 角色选择框列表
   */
  getRoleOptionselect: () => {
    return request.get({
      url: '/system/role/optionselect',
    });
  },

  /**
   * 查询已分配用户角色列表
   * @param params 查询参数
   * @returns 已分配用户角色列表
   */
  getAllocatedList: (params: any) => {
    return request.get({
      url: '/system/role/authUser/allocatedList',
      params,
    });
  },

  /**
   * 查询未分配用户角色列表
   * @param params 查询参数
   * @returns 未分配用户角色列表
   */
  getUnallocatedList: (params: any) => {
    return request.get({
      url: '/system/role/authUser/unallocatedList',
      params,
    });
  },

  /**
   * 取消授权用户
   * @param userRole 用户角色关系
   * @returns 取消授权结果
   */
  cancelAuthUser: (userRole: { userId: number; roleId: number }) => {
    return request.put({
      url: '/system/role/authUser/cancel',
      data: userRole,
    });
  },

  /**
   * 批量取消授权用户
   * @param roleId 角色ID
   * @param userIds 用户ID数组
   * @returns 取消授权结果
   */
  cancelAuthUserAll: (roleId: number, userIds: number[]) => {
    return request.put({
      url: '/system/role/authUser/cancelAll',
      params: { roleId, userIds: userIds.join(',') },
    });
  },

  /**
   * 批量选择用户授权
   * @param roleId 角色ID
   * @param userIds 用户ID数组
   * @returns 授权结果
   */
  selectAuthUserAll: (roleId: number, userIds: number[]) => {
    return request.put({
      url: '/system/role/authUser/selectAll',
      params: { roleId, userIds: userIds.join(',') },
    });
  },
};
