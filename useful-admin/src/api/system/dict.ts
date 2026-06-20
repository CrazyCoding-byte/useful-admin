import { request } from '@/utils/request';

export interface SysDictType {
  dictId?: number;
  dictName?: string;
  dictType?: string;
  status?: string;
  remark?: string;
  createTime?: string;
}

export interface SysDictData {
  dictCode?: number;
  dictSort?: number;
  dictLabel?: string;
  dictValue?: string;
  dictType?: string;
  cssClass?: string;
  listClass?: string;
  isDefault?: string;
  status?: string;
  remark?: string;
  createTime?: string;
}

export const dictTypeApi = {
  // 查询字典类型列表
  getDictTypeList: (params?: Partial<SysDictType>) => {
    return request.get({
      url: '/system/dict/type/list',
      params,
    });
  },

  // 查询字典类型详情
  getDictTypeById: (dictId: number) => {
    return request.get({
      url: `/system/dict/type/${dictId}`,
    });
  },

  // 新增字典类型
  addDictType: (data: Partial<SysDictType>) => {
    return request.post({
      url: '/system/dict/type',
      data,
    });
  },

  // 修改字典类型
  updateDictType: (data: Partial<SysDictType>) => {
    return request.put({
      url: '/system/dict/type',
      data,
    });
  },

  // 删除字典类型
  deleteDictType: (dictIds: number[]) => {
    return request.delete({
      url: `/system/dict/type/${dictIds.join(',')}`,
    });
  },

  // 刷新字典缓存
  refreshCache: () => {
    return request.delete({
      url: '/system/dict/type/refreshCache',
    });
  },

  // 获取字典选择框列表
  getDictTypeOptions: () => {
    return request.get({
      url: '/system/dict/type/optionselect',
    });
  },
};

export const dictDataApi = {
  // 查询字典数据列表
  getDictDataList: (params?: Partial<SysDictData>) => {
    return request.get({
      url: '/system/dict/data/list',
      params,
    });
  },

  // 查询字典数据详情
  getDictDataById: (dictCode: number) => {
    return request.get({
      url: `/system/dict/data/${dictCode}`,
    });
  },

  // 根据字典类型查询字典数据
  getDictDataByType: (dictType: string) => {
    return request.get({
      url: `/system/dict/data/type/${dictType}`,
    });
  },

  // 新增字典数据
  addDictData: (data: Partial<SysDictData>) => {
    return request.post({
      url: '/system/dict/data',
      data,
    });
  },

  // 修改字典数据
  updateDictData: (data: Partial<SysDictData>) => {
    return request.put({
      url: '/system/dict/data',
      data,
    });
  },

  // 删除字典数据
  deleteDictData: (dictCodes: number[]) => {
    return request.delete({
      url: `/system/dict/data/${dictCodes.join(',')}`,
    });
  },
};
