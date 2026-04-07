import { request } from '@/utils/request';
import type { SysMenu } from '@/api/model/menuModel';

/**
 * 菜单管理API
 */
export const menuApi = {
  /**
   * 获取菜单列表
   * @param params 查询参数
   * @returns 菜单列表数据
   */
  getMenuList: (params: Partial<SysMenu>) => {
    return request.get({
      url: '/system/menu/list',
      params,
    });
  },

  /**
   * 获取菜单详细信息
   * @param menuId 菜单ID
   * @returns 菜单详细信息
   */
  getMenuInfo: (menuId: number) => {
    return request.get({
      url: `/system/menu/${menuId}`,
    });
  },

  /**
   * 获取菜单下拉树列表
   * @param params 查询参数
   * @returns 菜单下拉树列表
   */
  getMenuTreeselect: (params?: Partial<SysMenu>) => {
    return request.get({
      url: '/system/menu/treeselect',
      params,
    });
  },

  /**
   * 加载对应角色菜单列表树
   * @param roleId 角色ID
   * @returns 角色菜单列表树
   */
  getRoleMenuTreeselect: (roleId: number) => {
    return request.get({
      url: `/system/menu/roleMenuTreeselect/${roleId}`,
    });
  },

  /**
   * 根据用户ID获取菜单树
   * @param userId 用户ID
   * @returns 用户菜单树
   */
  getMenusTreeByUserId: (userId: number) => {
    return request.get({
      url: `/system/menu/getMenusTreeByUserId/${userId}`,
    });
  },

  /**
   * 新增菜单
   * @param menu 菜单信息
   * @returns 新增结果
   */
  addMenu: (menu: SysMenu) => {
    return request.post({
      url: '/system/menu',
      data: menu,
    });
  },

  /**
   * 修改菜单
   * @param menu 菜单信息
   * @returns 修改结果
   */
  updateMenu: (menu: SysMenu) => {
    return request.put({
      url: '/system/menu',
      data: menu,
    });
  },

  /**
   * 删除菜单
   * @param menuId 菜单ID
   * @returns 删除结果
   */
  deleteMenu: (menuId: number) => {
    return request.delete({
      url: `/system/menu/${menuId}`,
    });
  },
};
