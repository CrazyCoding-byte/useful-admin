package cn.poile.ucs.auth.security;

import cn.poile.ucs.auth.mapper.BaseUserMapper;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.model.AjaxResult;
import com.yzx.model.StringUtils;
import com.yzx.model.ucenter.BaseAuth;
import com.yzx.model.ucenter.BaseUser;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.AESEncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.common.exceptions.InvalidClientException;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @className: BaseUserDetails
 * @author: yzx
 * @date: 2025/8/21 6:24
 * @Version: 1.0
 * @description:
 */
@Slf4j
public abstract class BaseUserDetails implements UserDetailsService {

    @Autowired
    @Qualifier("ClientDetailsService")
    private ClientDetailsService client;

    @Autowired
    BaseUserMapper baseUserMapper;

    @Autowired
    private AESEncryptUtil aesEncryptUtil;


    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //取出身份，如果身份为空说明没有认证
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //没有认证统一采用http basic认证，http basic中存储了client_id和client_secret，开始认证client_id和client_secret
        if (authentication == null) {
            //获取客户端详情，此处用的 JdbcClientDetailsService进行查询操作，通过 查看源码可以知道，该表的名称为"oauth_client_details"
            //此处客户端详情service有两种实现方式，一种内存，一种数据库，Client
            ClientDetails clientDetails = client.loadClientByClientId(s);
            log.debug("client details is :" + clientDetails);
            log.debug("******************* basic auth *******************");
            if (clientDetails != null) {
                //密码，此处是从数据库中获取客户端密码
                String clientSecret = clientDetails.getClientSecret();
                log.debug("client details username is :" + s);
                String encode = new BCryptPasswordEncoder().encode(clientSecret);
                return new User(s, encode, AuthorityUtils.commaSeparatedStringToAuthorityList(""));
            } else {
                throw new InvalidClientException("No client details presented");
            }
        }
        if (StringUtils.isEmpty(s)) {
            //返回null表示用户不存在，Spring Security会抛出异常
            return null;
        }
        // 解密用户名（JWT中存储的是加密后的用户名）
        String decryptedUsername = s;
        try {
            decryptedUsername = aesEncryptUtil.decrypt(s);
            log.debug("解密后的用户名: {}", decryptedUsername);
        } catch (Exception e) {
            // 解密失败，可能已经是明文，直接使用
            log.debug("用户名解密失败，使用原值: {}", s);
        }
        BaseAuth baseAuth = getBaseAuth(decryptedUsername);
        if (StringUtils.isEmpty(baseAuth)) {
            throw new UsernameNotFoundException("用户不存在");
        }
        BaseUser baseUser = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>().eq(BaseUser::getUserId, baseAuth.getUserId()));
        // 注意：登录时暂时不获取权限，等登录成功后再通过令牌获取
        StringBuilder userPermissStr = new StringBuilder();
        Set<String> menuPermissionByUserId1 = new HashSet<>();
        // 创建User对象时，使用baseAuth.getPassword()作为密码，DaoAuthenticationProvider会自动使用passwordEncoder.matches方法比较密码
        User user = new User(baseAuth.getUserName(), baseAuth.getPassword(), AuthorityUtils.commaSeparatedStringToAuthorityList(userPermissStr.toString()));
        // 添加空值检查，确保即使baseUser为null，也能正常创建BaseUserDetail对象
        BaseUserDetail baseUserDetail = new BaseUserDetail(baseAuth, baseUser, user);
        baseUserDetail.setPermissions(menuPermissionByUserId1 == null ? new HashSet<>() : menuPermissionByUserId1);
        return baseUserDetail;
    }

    protected abstract BaseAuth getBaseAuth(String s);
}