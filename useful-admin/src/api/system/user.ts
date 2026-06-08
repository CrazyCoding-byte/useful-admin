import {request} from '@/utils/request';
import type {SysUser} from '@/api/model/userModel';

/**
 * 用户管理API
 */
export const userApi = {
  /**
   * 获取用户列表
   * @param params 查询参数
   * @returns 用户列表数据
   */
  getUserList: (params: any) => {
    const {pageNum = 1, pageSize = 10, ...userParams} = params;
    return request.post({
      url: `/system/user/list/${pageNum}/${pageSize}`,
      data: userParams,
    });
  },

  /**
   * 获取用户详细信息
   * @param userId 用户ID
   * @returns 用户详细信息
   */
  getUserInfo: (userId: string) => {
    return request.get({
      url: `/system/user/getUserInfo/${userId}`,
    });
  },

  /**
   * 根据二维码获取用户信息
   * @param code 二维码
   * @returns 用户信息
   */
  getUserInfoByQrCode: (code: string) => {
    return request.get({
      url: `/system/user/getUserInfoByQrCode/${code}`,
    });
  },

  /**
   * 删除用户
   * @param userIds 用户ID数组
   * @returns 删除结果
   */
  deleteUser: (userIds: number[]) => {
    return request.delete({
      url: `/system/user/${userIds.join(',')}`,
    });
  },

  /**
   * 重置用户密码
   * @param user 用户信息
   * @returns 重置结果
   */
  resetPassword: (user: { userId: number; password: string }) => {
    return request.put({
      url: '/system/user/resetPwd',
      data: user,
    });
  },

  /**
   * 修改用户状态
   * @param user 用户信息
   * @returns 修改结果
   */
  changeStatus: (user: { userId: number; status: string }) => {
    return request.put({
      url: '/system/user/changeStatus',
      data: user,
    });
  },

  /**
   * 获取用户授权角色
   * @param userId 用户ID
   * @returns 授权角色信息
   */
  getAuthRole: (userId: number) => {
    return request.get({
      url: `/system/user/authRole/${userId}`,
    });
  },

  /**
   * 用户授权角色
   * @param data 授权数据
   * @returns 授权结果
   */
  insertAuthRole: (data: { userId: number; roleIds: number[] }) => {
    return request.put({
      url: '/system/user/authRole',
      data,
    });
  },

  /**
   * 注册用户
   * @param user 注册信息
   * @returns 注册结果
   */
  register: (user: any) => {
    return request.post({
      url: '/system/user/register',
      data: user,
    });
  },

  /**
   * 分销注册
   * @param user 注册信息
   * @param code 邀请码
   * @returns 注册结果
   */
  registerByH5: (user: any, code: string) => {
    return request.post({
      url: '/system/user',
      data: user,
      params: {code},
    });
  },

  /**
   * 新增或修改用户
   * @param user 用户信息
   * @returns 操作结果
   */
  saveUser: (user: any) => {
    return request.post({
      url: '/system/user',
      data: user,
    });
  },
};
