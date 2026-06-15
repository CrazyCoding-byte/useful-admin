import { request } from '@/utils/request';

export interface SysDept {
  deptId?: number;
  parentId?: number;
  ancestors?: string;
  deptName?: string;
  orderNum?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: string;
  createTime?: string;
  children?: SysDept[];
}

export const deptApi = {
  // 查询部门列表
  getDeptList: (params?: Partial<SysDept>) => {
    return request.get({
      url: '/system/dept/list',
      params,
    });
  },

  // 查询部门详情
  getDeptById: (deptId: number) => {
    return request.get({
      url: `/system/dept/${deptId}`,
    });
  },

  // 新增部门
  addDept: (data: Partial<SysDept>) => {
    return request.post({
      url: '/system/dept',
      data,
    });
  },

  // 修改部门
  updateDept: (data: Partial<SysDept>) => {
    return request.put({
      url: '/system/dept',
      data,
    });
  },

  // 删除部门
  deleteDept: (deptId: number) => {
    return request.delete({
      url: `/system/dept/${deptId}`,
    });
  },

  // 获取部门选择框列表
  getDeptOptions: () => {
    return request.get({
      url: '/system/dept/optionselect',
    });
  },

  // 查询部门列表（排除节点）
  getDeptListExclude: (deptId: number) => {
    return request.get({
      url: `/system/dept/list/exclude/${deptId}`,
    });
  },
};
