package com.yzx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.system.SysTenantPackage;
import java.util.List;

/**
 * 租户套餐 Service 接口
 */
public interface ISysTenantPackageService extends IService<SysTenantPackage> {

    /**
     * 获取所有可用套餐
     */
    List<SysTenantPackage> selectAvailablePackages();

    /**
     * 根据套餐ID获取菜单ID列表
     */
    List<Long> getMenuIdsByPackageId(Long packageId);

    /**
     * 检查套餐名称是否唯一
     */
    boolean checkPackageNameUnique(SysTenantPackage sysTenantPackage);
}
