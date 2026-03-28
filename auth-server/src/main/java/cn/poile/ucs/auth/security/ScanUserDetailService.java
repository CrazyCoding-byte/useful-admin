package cn.poile.ucs.auth.security;

import com.yzx.model.ucenter.BaseAuth;
import org.springframework.stereotype.Component;

/**
 * @className: ScanUserDetailService
 * @author: yzx
 * @date: 2026/3/27 15:11
 * @Version: 1.0
 * @description:
 */
@Component
public class ScanUserDetailService extends BaseUserDetails{
    @Override
    protected BaseAuth getBaseAuth(String s) {
        return null;
    }
}
