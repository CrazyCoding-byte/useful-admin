import {request} from '@/utils/request';

/**
 * 优惠券管理 API
 */
export const couponApi = {
    /**
     * 获取优惠券列表（分页）
     */
    getList: (params?: Record<string, unknown>) => {
      return request.get({
        url: '/coupon/couponInfo/list',
        params,
      });
    },

    /**
     * 根据ID获取优惠券详情
     */
    getById: (id: number) => {
      return request.get({
        url: `/coupon/couponInfo/${id}`,
      });
    },

    /**
     * 新增优惠券
     */
    add: (data: Record<string, unknown>) => {
      return request.post({
        url: '/coupon/couponInfo',
        data,
      });
    },

    /**
     * 修改优惠券
     */
    update: (data: Record<string, unknown>) => {
      return request.put({
        url: '/coupon/couponInfo',
        data,
      });
    },

    /**
     * 删除优惠券
     */
    delete: (id: number) => {
      return request.delete({
        url: `/coupon/couponInfo/${id}`,
      });
    },
    getRangeList: (couponId: number) => request.get({url: `/coupon/couponInfo/${couponId}/range`}),
    saveRange: (couponId: number, rangeList: any[]) => request.post({
      url: `/coupon/couponInfo/${couponId}/range`,
      data: rangeList
    })
  }
;
