package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.AjaxResult;
import com.yzx.model.annotation.DataScope;
import com.yzx.model.exception.ServiceException;
import com.yzx.model.system.*;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.TableDataInfo;
import com.yzx.model.ucenter.BaseAuth;
import com.yzx.model.utils.BeanValidators;
import com.yzx.model.utils.SecurityUtils;
import com.yzx.model.utils.SpringUtils;
import com.yzx.system.config.UserRabbitMQConfig;
import com.yzx.system.domain.bo.SysUserBo;
import com.yzx.system.domain.convert.SysUserConvert;
import com.yzx.system.domain.vo.SysRoleVo;
import com.yzx.system.domain.vo.SysUserVo;
import com.yzx.system.mapper.SysRoleMapper;
import com.yzx.system.mapper.SysUserMapper;
import com.yzx.system.mapper.SysUserRoleMapper;
import com.yzx.system.service.BaseAuthService;
import com.yzx.system.service.ISysConfigService;
import com.yzx.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.Validator;
import java.time.LocalDate;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户 业务层处理
 *
 * @author ruoyi
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private BaseAuthService baseAuthService;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    protected Validator validator;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SysUserConvert userConvert;

    // ==================== 新的基于 Bo/Vo 的方法实现 ====================

    /**
     * 根据条件分页查询用户列表
     *
     * @param user      用户信息
     * @param pageQuery 分页查询参数
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public TableDataInfo<SysUserVo> selectPageUserList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUser> page = pageQuery.build();
        LambdaQueryWrapper<SysUser> queryWrapper = buildQueryWrapper(user);

        long total = baseMapper.selectCount(queryWrapper);
        List<SysUser> records = baseMapper.selectList(queryWrapper
                .last("LIMIT " + (page.getCurrent() - 1) * page.getSize() + "," + page.getSize()));

        List<SysUserVo> voList = userConvert.entityListToVoList(records);
        TableDataInfo<SysUserVo> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setTotal((int) total);
        tableDataInfo.setRows(voList);
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("查询成功");
        return tableDataInfo;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SysUser> buildQueryWrapper(SysUserBo user) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (!StringUtils.isEmpty(user.getPhonenumber())) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");

        return queryWrapper;
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user      用户信息
     * @param pageQuery 分页查询参数
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public TableDataInfo<SysUserVo> selectAllocatedList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUser> page = pageQuery.build();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (!StringUtils.isEmpty(user.getPhonenumber())) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");

        long total = baseMapper.selectCount(queryWrapper);
        List<SysUser> records = baseMapper.selectList(queryWrapper
                .last("LIMIT " + (page.getCurrent() - 1) * page.getSize() + "," + page.getSize()));

        List<SysUserVo> voList = userConvert.entityListToVoList(records);
        TableDataInfo<SysUserVo> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setTotal((int) total);
        tableDataInfo.setRows(voList);
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("查询成功");
        return tableDataInfo;
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user      用户信息
     * @param pageQuery 分页查询参数
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public TableDataInfo<SysUserVo> selectUnallocatedList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUser> page = pageQuery.build();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getPhonenumber())) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");

        long total = baseMapper.selectCount(queryWrapper);
        List<SysUser> records = baseMapper.selectList(queryWrapper
                .last("LIMIT " + (page.getCurrent() - 1) * page.getSize() + "," + page.getSize()));

        List<SysUserVo> voList = userConvert.entityListToVoList(records);
        TableDataInfo<SysUserVo> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setTotal((int) total);
        tableDataInfo.setRows(voList);
        tableDataInfo.setCode(200);
        tableDataInfo.setMsg("查询成功");
        return tableDataInfo;
    }

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByUserName(String userName) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, userName);
        SysUser user = baseMapper.selectOne(queryWrapper);
        return userConvert.entityToVo(user);
    }

    /**
     * 通过手机号查询用户
     *
     * @param phonenumber 手机号
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByPhonenumber(String phonenumber) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhonenumber, phonenumber);
        SysUser user = baseMapper.selectOne(queryWrapper);
        return userConvert.entityToVo(user);
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserById(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        SysUserVo vo = userConvert.entityToVo(user);
        // 查询角色信息
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        vo.setRoles(roles != null ? roles.stream()
                .map(role -> {
                    SysRoleVo roleVo = new SysRoleVo();
                    roleVo.setRoleId(role.getRoleId());
                    roleVo.setRoleName(role.getRoleName());
                    roleVo.setRoleKey(role.getRoleKey());
                    roleVo.setRoleSort(role.getRoleSort());
                    roleVo.setStatus(role.getStatus());
                    return roleVo;
                }).collect(Collectors.toList()) : null);
        return vo;
    }

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    @Override
    public List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName,
                        SysUser::getEmail, SysUser::getPhonenumber)
                .eq(SysUser::getStatus, "0")
                .eq(SysUser::getDelFlag, "0");
        if (deptId != null) {
            queryWrapper.eq(SysUser::getDeptId, deptId);
        }
        if (userIds != null && !userIds.isEmpty()) {
            queryWrapper.in(SysUser::getUserId, userIds);
        }
        List<SysUser> list = baseMapper.selectList(queryWrapper);
        return userConvert.entityListToVoList(list);
    }

    /**
     * 根据用户ID查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(Long userId) {
        List<SysRole> list = roleMapper.selectRolesByUserId(userId);
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        return list.stream().map(SysRole::getRoleName).collect(Collectors.joining(","));
    }

    /**
     * 根据用户ID查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(Long userId) {
        // 当前系统没有岗位表，返回空字符串
        return "";
    }

    /**
     * 校验用户账号是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(SysUserBo user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, user.getUserName());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkPhoneUnique(SysUserBo user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhonenumber, user.getPhonenumber());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkEmailUnique(SysUserBo user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getEmail, user.getEmail());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    @Override
    public void checkUserAllowed(Long userId) {
        if (userId != null && (1L == userId || 222L == userId)) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    @Override
    public void checkUserDataScope(Long userId) {
        if (!SysUser.isAdmin(SecurityUtils.getUserId())) {
            SysUser user = new SysUser();
            user.setUserId(userId);
            Page<SysUser> page = new Page<>(1, 1);
            Page<SysUser> result = SpringUtils.getAopProxy(this).selectUserList(user, page);
            if (result.getRecords().isEmpty()) {
                throw new ServiceException("没有权限访问用户数据！");
            }
        }
    }

    /**
     * 新增用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertUser(SysUserBo user) {
        SysUser sysUser = userConvert.boToEntity(user);
        // 新增用户信息
        int rows = baseMapper.insert(sysUser) > 0 ? 1 : 0;
        user.setUserId(sysUser.getUserId());
        // 新增用户与角色管理
        if (rows > 0 && user.getRoleIds() != null) {
            insertUserRole(user.getUserId(), user.getRoleIds());
        }
        return rows;
    }

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public boolean registerUser(SysUserBo user) {
        SysUser sysUser = userConvert.boToEntity(user);
        int rows = baseMapper.insert(sysUser);
        if (rows > 0) {
            BaseAuth baseAuth = new BaseAuth();
            baseAuth.setUserId(sysUser.getUserId());
            baseAuth.setUserName(sysUser.getUserName());
            baseAuth.setPhoneNumber(sysUser.getPhonenumber());
            baseAuth.setEmail(sysUser.getEmail());
            baseAuth.setPassword(new BCryptPasswordEncoder().encode(sysUser.getPassword()));
            baseAuthService.save(baseAuth);
        }
        return rows > 0;
    }

    /**
     * 修改用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateUser(SysUserBo user) {
        Long userId = user.getUserId();
        // 如果提供了角色信息，才更新角色关联
        if (user.getRoleIds() != null) {
            // 删除用户与角色关联
            userRoleMapper.deleteUserRoleByUserId(userId);
            // 新增用户与角色管理
            insertUserRole(userId, user.getRoleIds());
        }
        // 修改用户信息
        SysUser sysUser = userConvert.boToEntity(user);
        return baseMapper.update(sysUser, new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, user.getUserId())) > 0 ? 1 : 0;
    }

    /**
     * 用户授权角色
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     */
    @Override
    @Transactional
    public void insertUserAuth(Long userId, Long[] roleIds) {
        userRoleMapper.deleteUserRoleByUserId(userId);
        insertUserRole(userId, roleIds);
    }

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 账号状态
     * @return 结果
     */
    @Override
    public int updateUserStatus(Long userId, String status) {
        SysUser user = new SysUser();
        user.setStatus(status);
        return baseMapper.update(user, new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, userId)) > 0 ? 1 : 0;
    }

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int updateUserProfile(SysUserBo user) {
        SysUser sysUser = userConvert.boToEntity(user);
        return baseMapper.updateById(sysUser) > 0 ? 1 : 0;
    }

    /**
     * 修改用户头像
     *
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    @Override
    public boolean updateUserAvatar(Long userId, String avatar) {
        SysUser user = new SysUser();
        user.setAvatar(avatar);
        return baseMapper.update(user, new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, userId)) > 0;
    }

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetUserPwd(Long userId, String password) {
        SysUser user = new SysUser();
        user.setPassword(password);
        return baseMapper.update(user, new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, userId)) > 0 ? 1 : 0;
    }

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteUserById(Long userId) {
        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleByUserId(userId);
        // 删除用户
        return baseMapper.deleteById(userId) > 0 ? 1 : 0;
    }

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            checkUserAllowed(userId);
            checkUserDataScope(userId);
        }
        // 删除用户与角色关联
        LambdaQueryWrapper<SysUserRole> sysUserRoleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysUserRoleLambdaQueryWrapper.in(SysUserRole::getUserId, userIds);
        userRoleMapper.delete(sysUserRoleLambdaQueryWrapper);
        // 批量删除用户
        return baseMapper.delete(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, Arrays.asList(userIds))) > 0 ? 1 : 0;
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    @Override
    public String selectUserNameById(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        return user != null ? user.getUserName() : null;
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Override
    public String selectNicknameById(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        return user != null ? user.getNickName() : null;
    }

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    @Override
    public String selectPhonenumberById(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        return user != null ? user.getPhonenumber() : null;
    }

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    @Override
    public String selectEmailById(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        return user != null ? user.getEmail() : null;
    }

    /**
     * 通过部门id查询当前部门所有用户
     *
     * @param deptId 部门id
     * @return 结果
     */
    @Override
    public List<SysUserVo> selectUserListByDept(Long deptId) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getDeptId, deptId);
        queryWrapper.eq(SysUser::getDelFlag, "0");
        queryWrapper.orderByAsc(SysUser::getUserId);
        List<SysUser> list = baseMapper.selectList(queryWrapper);
        return userConvert.entityListToVoList(list);
    }

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    @Override
    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SysUserRole::getRoleId, roleIds);
        List<SysUserRole> userRoles = userRoleMapper.selectList(queryWrapper);
        return userRoles.stream().map(SysUserRole::getUserId).distinct().collect(Collectors.toList());
    }

    // ==================== 兼容旧接口的方法实现 ====================

    /**
     * 根据条件分页查询用户列表（兼容旧方法）
     *
     * @param user 用户信息
     * @param page 分页信息
     * @return 用户信息集合信息
     */
    @DataScope(deptAlias = "d", userAlias = "u")
    @Override
    public Page<SysUser> selectUserList(SysUser user, Page<SysUser> page) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (!StringUtils.isEmpty(user.getPhonenumber())) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }
        if (!StringUtils.isEmpty(user.getEmail())) {
            queryWrapper.like(SysUser::getEmail, user.getEmail());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        if (!StringUtils.isEmpty(user.getDelFlag())) {
            queryWrapper.eq(SysUser::getDelFlag, user.getDelFlag());
        } else {
            // 默认查询未删除的用户
            queryWrapper.eq(SysUser::getDelFlag, "0");
        }

        // 执行分页查询
        long total = baseMapper.selectCount(queryWrapper);
        List<SysUser> records = baseMapper.selectList(queryWrapper
                .last("LIMIT " + (page.getCurrent() - 1) * page.getSize() + "," + page.getSize()));
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    /**
     * 根据条件分页查询用户列表（支持日期范围，兼容旧方法）
     */
    @DataScope(deptAlias = "d", userAlias = "u")
    @Override
    public Page<SysUser> selectUserList(SysUser user, Page<SysUser> page, Map<String, Object> params) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (!StringUtils.isEmpty(user.getPhonenumber())) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }
        if (!StringUtils.isEmpty(user.getEmail())) {
            queryWrapper.like(SysUser::getEmail, user.getEmail());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        if (!StringUtils.isEmpty(user.getDelFlag())) {
            queryWrapper.eq(SysUser::getDelFlag, user.getDelFlag());
        } else {
            // 默认查询未删除的用户
            queryWrapper.eq(SysUser::getDelFlag, "0");
        }

        // 处理日期范围
        Object createTimeObj = params.get("createTime");
        if (createTimeObj != null && createTimeObj instanceof List) {
            List<?> createTimeList = (List<?>) createTimeObj;
            if (createTimeList.size() >= 2) {
                Object startDate = createTimeList.get(0);
                Object endDate = createTimeList.get(1);
                if (startDate != null) {
                    queryWrapper.ge(SysUser::getCreateTime, startDate);
                }
                if (endDate != null) {
                    queryWrapper.le(SysUser::getCreateTime, endDate);
                }
            }
        }

        // 执行分页查询
        long total = baseMapper.selectCount(queryWrapper);
        List<SysUser> records = baseMapper.selectList(queryWrapper
                .last("LIMIT " + (page.getCurrent() - 1) * page.getSize() + "," + page.getSize()));
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    /**
     * 根据条件分页查询已分配用户角色列表（兼容旧方法）
     */
    @DataScope(deptAlias = "d", userAlias = "u")
    @Override
    public List<SysUser> selectAllocatedList(SysUser user) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");

        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 根据条件分页查询未分配用户角色列表（兼容旧方法）
     */
    @DataScope(deptAlias = "d", userAlias = "u")
    @Override
    public List<SysUser> selectUnallocatedList(SysUser user) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        if (!StringUtils.isEmpty(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (!StringUtils.isEmpty(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (!StringUtils.isEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");

        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 校验用户名称是否唯一（兼容旧方法）
     */
    @Override
    public boolean checkUserNameUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, user.getUserName());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验手机号码是否唯一（兼容旧方法）
     */
    @Override
    public boolean checkPhoneUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhonenumber, user.getPhonenumber());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验email是否唯一（兼容旧方法）
     */
    @Override
    public boolean checkEmailUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getEmail, user.getEmail());
        queryWrapper.ne(SysUser::getUserId, userId);
        SysUser info = baseMapper.selectOne(queryWrapper);
        return info == null;
    }

    /**
     * 校验用户是否允许操作（兼容旧方法）
     */
    @Override
    public void checkUserAllowed(SysUser user) {
        if (user.getUserId() != null && user.isAdmin()) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 新增用户信息（兼容旧方法）
     */
    @Transactional
    @Override
    public int insertUser(SysUser user) {
        // 新增用户信息
        int rows = baseMapper.insert(user) > 0 ? 1 : 0;
        // 新增用户与角色管理
        if (rows > 0 && user.getRoleIds() != null) {
            insertUserRole(user);
        }
        return rows;
    }

    /**
     * 注册用户信息（兼容旧方法）
     */
    @Override
    public boolean registerUser(SysUser user) {
        int rows = baseMapper.insert(user);
        BaseAuth baseAuth = new BaseAuth();
        baseAuth.setUserId(user.getUserId());
        baseAuth.setUserName(user.getUserName());
        baseAuth.setPhoneNumber(user.getPhonenumber());
        baseAuth.setEmail(user.getEmail());
        baseAuth.setPassword(new BCryptPasswordEncoder().encode(user.getPassword()));
        log.info("用户注册信息:{}", baseAuth);
        baseAuthService.save(baseAuth);
        return rows > 0;
    }

    /**
     * 修改用户信息（兼容旧方法）
     */
    @Transactional
    @Override
    public int updateUser(SysUser user) {
        Long userId = user.getUserId();
        // 如果提供了角色信息，才更新角色关联
        if (user.getRoleIds() != null) {
            // 删除用户与角色关联
            userRoleMapper.deleteUserRoleByUserId(userId);
            // 新增用户与角色管理
            insertUserRole(user);
        }
        // 修改用户信息
        return baseMapper.update(user, new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, user.getUserId())) > 0 ? 1 : 0;
    }

    /**
     * 修改用户状态（兼容旧方法）
     */
    @Override
    public int updateUserStatus(SysUser user) {
        return baseMapper.updateById(user) > 0 ? 1 : 0;
    }

    /**
     * 修改用户基本信息（兼容旧方法）
     */
    @Override
    public int updateUserProfile(SysUser user) {
        return baseMapper.updateById(user) > 0 ? 1 : 0;
    }

    /**
     * 修改用户头像（兼容旧方法）
     */
    @Override
    public boolean updateUserAvatar(String userName, String avatar) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, userName);
        SysUser user = new SysUser();
        user.setAvatar(avatar);
        return baseMapper.update(user, queryWrapper) > 0;
    }

    /**
     * 重置用户密码（兼容旧方法）
     */
    @Override
    public int resetPwd(SysUser user) {
        return baseMapper.updateById(user) > 0 ? 1 : 0;
    }

    /**
     * 重置用户密码（兼容旧方法）
     */
    @Override
    public int resetUserPwd(String userName, String password) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, userName);
        SysUser user = new SysUser();
        user.setPassword(password);
        return baseMapper.update(user, queryWrapper) > 0 ? 1 : 0;
    }

    /**
     * 导入用户数据（兼容旧方法）
     */
    @Override
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName) {
        if (CollectionUtils.isEmpty(userList)) {
            throw new ServiceException("导入用户数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (SysUser user : userList) {
            try {
                // 验证是否存在这个用户
                LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(SysUser::getUserName, user.getUserName());
                SysUser u = baseMapper.selectOne(queryWrapper);
                if (u == null) {
                    //校验参数
                    BeanValidators.validateWithException(validator, user);
                    String password = configService.selectConfigByKey("sys.user.initPassword");
                    user.setPassword(SecurityUtils.encryptPassword(password));
                    // createBy、createTime 由 MyMetaObjectHandler 自动填充
                    baseMapper.insert(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 导入成功");
                } else if (isUpdateSupport) {
                    BeanValidators.validateWithException(validator, user);
                    checkUserAllowed(u);
                    checkUserDataScope(u.getUserId());
                    user.setUserId(u.getUserId());
                    // updateBy、updateTime 由 MyMetaObjectHandler 自动填充
                    baseMapper.updateById(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 更新成功");
                } else {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、账号 " + user.getUserName() + " 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、账号 " + user.getUserName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    /**
     * 注册（兼容旧方法）
     */
    @Override
    public AjaxResult register(RegisterUserTo user) {
        return registInfo(user, null) ? AjaxResult.success("注冊成功") : AjaxResult.error("注册失败");
    }

    /**
     * H5注册（兼容旧方法）
     */
    @Override
    public AjaxResult registerByH5(RegisterUserTo user, String qrcode) {
        return registInfo(user, qrcode) ? AjaxResult.success("注冊成功") : AjaxResult.error("注册失败");
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 新增用户角色信息
     *
     * @param user 用户对象
     */
    private void insertUserRole(SysUser user) {
        this.insertUserRole(user.getUserId(), user.getRoleIds());
    }

    /**
     * 新增用户角色信息
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     */
    private void insertUserRole(Long userId, Long[] roleIds) {
        if (roleIds != null && roleIds.length > 0) {
            // 新增用户与角色管理
            List<SysUserRole> list = new ArrayList<SysUserRole>(roleIds.length);
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                list.add(ur);
            }
            userRoleMapper.batchUserRole(list);
        }
    }

    @Transactional
    public boolean registInfo(RegisterUserTo user, String qrcode) {
        if (Objects.isNull(user)) return false;
        String code = user.getCode();
        if (isExist(user.getPhone(), user.getUsername())) return false;
        if (org.springframework.util.StringUtils.isEmpty(code)) return false;
        String s = redisTemplate.opsForValue().get("sms_code:" + user.getPhone());
        if (s.equals(code)) {
            SysUser sysUser = new SysUser();
            if (!StringUtils.isEmpty(user.getPassword())) {
                sysUser.setPassword(user.getPassword());
            }
            sysUser.setPhonenumber(user.getPhone());
            if (!StringUtils.isEmpty(user.getUsername())) {
                sysUser.setUserName(user.getUsername());
            }
            sysUser.setQrCode(generateCodeWithDate());
            boolean save = this.save(sysUser);
            //如果当前是分销注册保存成功发送消息给mq
            registerUser(sysUser);
            if (save && !StringUtils.isEmpty(qrcode)) {
                sendUserMessage(sysUser, qrcode);
            }
            return true;
        } else {
            return false;
        }
    }

    private void sendUserMessage(SysUser sysUser, String qrCode) {
        UserRegisteredMessage userRegisteredMessage = new UserRegisteredMessage();
        userRegisteredMessage.setUserId(sysUser.getUserId().toString());
        userRegisteredMessage.setUserName(sysUser.getUserName());
        userRegisteredMessage.setPhoneNumber(sysUser.getPhonenumber());
        userRegisteredMessage.setQrCode(sysUser.getQrCode());
        userRegisteredMessage.setRegisterTime(new Date());

        // 如果有邀请码，先查询邀请人信息
        if (!StringUtils.isEmpty(qrCode)) {
            LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysUser::getQrCode, qrCode);
            SysUser inviter = baseMapper.selectOne(queryWrapper);
            if (inviter != null) {
                InviterInfo inviterInfo = new InviterInfo();
                inviterInfo.setUserId(inviter.getUserId().toString());
                inviterInfo.setUserName(inviter.getUserName());
                inviterInfo.setQrCode(inviter.getQrCode());
                userRegisteredMessage.setInviterInfo(inviterInfo);
            }
        }

        try {
            rabbitTemplate.convertAndSend(UserRabbitMQConfig.USER_EXCHANGE,
                    UserRabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                    userRegisteredMessage);
            log.info("发送用户注册消息成功：{}", userRegisteredMessage);
        } catch (Exception e) {
            log.error("发送用户注册消息失败：{}", e.getMessage());
            //todo 记录日志
        }
    }

    private boolean isExist(String phone, String username) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, username);
        boolean userExists = baseMapper.selectOne(queryWrapper) != null;

        if (!userExists) {
            queryWrapper.clear();
            queryWrapper.eq(SysUser::getPhonenumber, phone);
            userExists = baseMapper.selectOne(queryWrapper) != null;
        }

        return userExists;
    }

    private static String generateCodeWithDate() {
        // 生成6位随机大写字母
        StringBuilder randomCode = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            char randomChar = (char) ('A' + random.nextInt(26));
            randomCode.append(randomChar);
        }
        // 获取当前日期，格式化为8位数字（YYYYMMDD）
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        // 格式化为8位：yyyyMMdd
        String datePart = String.format("%04d%02d%02d", year, month, day);
        // 组合返回结果
        return randomCode.toString() + datePart;
    }
}
