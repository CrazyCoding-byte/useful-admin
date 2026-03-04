package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.constant.UserConstants;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.SysConfig;
import com.yzx.system.mapper.SysConfigMapper;
import com.yzx.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 配置 业务层处理
 *
 * @author ruoyi
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements ISysConfigService {
    
    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysConfig selectConfigById(Long configId) {
        return baseMapper.selectById(configId);
    }

    /**
     * 根据键名查询参数配置
     *
     * @param configKey 参数键名
     * @return 参数键值
     */
    @Override
    public String selectConfigByKey(String configKey) {
        LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig config = baseMapper.selectOne(queryWrapper);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 获取验证码开关
     *
     * @return true开启，false关闭
     */
    @Override
    public boolean selectCaptchaEnabled() {
        String captchaEnabled = selectConfigByKey("sys.account.captchaEnabled");
        return "true".equals(captchaEnabled);
    }

    /**
     * 查询参数配置列表
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfig> selectConfigList(SysConfig config) {
        LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.isNotBlank(config.getConfigName())) {
            queryWrapper.like(SysConfig::getConfigName, config.getConfigName());
        }
        if (StringUtils.isNotBlank(config.getConfigKey())) {
            queryWrapper.like(SysConfig::getConfigKey, config.getConfigKey());
        }
        if (StringUtils.isNotBlank(config.getConfigType())) {
            queryWrapper.eq(SysConfig::getConfigType, config.getConfigType());
        }

        
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 新增参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int insertConfig(SysConfig config) {
        // 校验参数键名是否唯一
        if (!checkConfigKeyUnique(config)) {
            throw new ServiceException("新增参数" + config.getConfigName() + "失败，参数键名已存在");
        }
        return baseMapper.insert(config) > 0 ? 1 : 0;
    }

    /**
     * 修改参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int updateConfig(SysConfig config) {
        // 校验参数键名是否唯一
        if (!checkConfigKeyUnique(config)) {
            throw new ServiceException("修改参数" + config.getConfigName() + "失败，参数键名已存在");
        }
        return baseMapper.updateById(config) > 0 ? 1 : 0;
    }

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteConfigByIds(Long[] configIds) {
        baseMapper.deleteBatchIds(java.util.Arrays.asList(configIds));
    }

    /**
     * 加载参数缓存数据
     */
    @Override
    public void loadingConfigCache() {
        // 这里简化处理，实际应该从数据库查询所有配置并加载到缓存
        // 假设我们有一个缓存机制，如 Redis 或内存缓存
        List<SysConfig> configs = baseMapper.selectList(null);
        for (SysConfig config : configs) {
            // 这里应该将配置存入缓存
            // 例如：redisTemplate.opsForValue().set("config:" + config.getConfigKey(), config.getConfigValue());
        }
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearConfigCache() {
        // 这里简化处理，实际应该清空缓存中的所有配置
        // 假设我们有一个缓存机制，如 Redis 或内存缓存
        // 例如：redisTemplate.delete(redisTemplate.keys("config:*"));
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetConfigCache() {
        clearConfigCache();
        loadingConfigCache();
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkConfigKeyUnique(SysConfig config) {
        Long configId = config.getConfigId() == null ? -1L : config.getConfigId();
        LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysConfig::getConfigKey, config.getConfigKey());
        queryWrapper.ne(SysConfig::getConfigId, configId);
        return baseMapper.selectOne(queryWrapper) == null;
    }
}
