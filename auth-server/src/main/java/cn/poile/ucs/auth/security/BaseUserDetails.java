package cn.poile.ucs.auth.security;

import cn.poile.ucs.auth.mapper.BaseUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.model.StringUtils;
import com.yzx.model.ucenter.BaseAuth;
import com.yzx.model.ucenter.BaseUser;
import com.yzx.model.ucenter.BaseUserDetail;
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

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //取出身份，如果身份为空说明没有认证
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //没有认证统一采用http basic认证，http basic中存储了client_id和client_secret，开始认证client_id和client_secret
        if (authentication == null)
        {
            //获取客户端详情，此处用的 JdbcClientDetailsService进行查询操作，通过 查看源码可以知道，该表的名称为"oauth_client_details"
            //此处客户端详情service有两种实现方式，一种内存，一种数据库，Client
            ClientDetails clientDetails = client.loadClientByClientId(s);
            log.debug("client details is :" + clientDetails);
            log.debug("******************* basic auth *******************");
            if (clientDetails != null)
            {
                //密码，此处是从数据库中获取客户端密码
                String clientSecret = clientDetails.getClientSecret();
                log.debug("client details username is :" + s);
                String encode = new BCryptPasswordEncoder().encode(clientSecret);
                return new User(s, encode, AuthorityUtils.commaSeparatedStringToAuthorityList(""));
            }
            else
            {
                throw new InvalidClientException("No client details presented");
            }
        }
        if (StringUtils.isEmpty(s)) {
            //返回null表示用户不存在，Spring Security会抛出异常
            return null;
        }
        BaseAuth baseAuth = getBaseAuth(s);
        if (StringUtils.isEmpty(baseAuth)) {
            throw new UsernameNotFoundException("用户不存在");
        }
        BaseUser baseUser = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>().eq(BaseUser::getUserId, baseAuth.getUserId()));
        // 注意：这里需要通过其他方式获取SystemApi实例，暂时注释掉相关代码
        // Set<String> menuPermissionByUserId = systemApi.getMenuPermissionByUserId(baseUser.getUserId());
        // String userPermissStr = StringUtils.join(menuPermissionByUserId.toArray(), ",");
        String userPermissStr = "";
        User user = new User(baseAuth.getUserName(), baseAuth.getPassword(), AuthorityUtils.commaSeparatedStringToAuthorityList(userPermissStr));
        BaseUserDetail baseUserDetail = new BaseUserDetail(baseAuth, baseUser, (User) user);
        // baseUserDetail.setPermissions(menuPermissionByUserId);
        return baseUserDetail;
    }

    protected abstract BaseAuth getBaseAuth(String s);
}