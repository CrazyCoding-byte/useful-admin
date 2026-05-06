package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.product.PmsAttr;
import com.yzx.product.mapper.PmsAttrMapper;
import com.yzx.product.service.PmsAttrService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @className: AttrServiceImpl
 * @author: yzx
 * @date: 2025/9/18 16:00
 * @Version: 1.0
 * @description:
 */
@Service
public class PmsAttrServiceImpl extends ServiceImpl<PmsAttrMapper, PmsAttr> implements PmsAttrService {
    @Override
    public List<Long> selectByIds(List<Long> attrIds) {
       return baseMapper.selectByids(attrIds);
    }
}
