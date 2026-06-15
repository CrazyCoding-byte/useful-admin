package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.system.SysTenantPackage;
import com.yzx.system.mapper.SysTenantPackageMapper;
import com.yzx.system.service.ISysTenantPackageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户套餐 Service 实现
 */
@Service
public class SysTenantPackageServiceImpl extends ServiceImpl<SysTenantPackageMapper, SysTenantPackage>
        implements ISysTenantPackageService {

    @Override
    public List<SysTenantPackage> selectAvailablePackages() {
        LambdaQueryWrapper<SysTenantPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenantPackage::getStatus, "0");
        wrapper.orderByAsc(SysTenantPackage::getPackageId);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<Long> getMenuIdsByPackageId(Long packageId) {
        SysTenantPackage pkg = baseMapper.selectById(packageId);
        if (pkg == null || StringUtils.isBlank(pkg.getMenuIds())) {
            return new ArrayList<>();
        }
        return Arrays.stream(pkg.getMenuIds().split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public boolean checkPackageNameUnique(SysTenantPackage sysTenantPackage) {
        LambdaQueryWrapper<SysTenantPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenantPackage::getPackageName, sysTenantPackage.getPackageName());
        wrapper.ne(sysTenantPackage.getPackageId() != null, SysTenantPackage::getPackageId, sysTenantPackage.getPackageId());
        return baseMapper.selectCount(wrapper) == 0;
    }
}
