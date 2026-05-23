package com.yzx.system;

import com.yzx.common.tenant.TenantContext;
import com.yzx.model.system.SysUser;
import com.yzx.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class SystemApplicationTests {
    @Autowired
    private SysUserMapper userMapper;

    @Test
    void contextLoads() {
        // 模拟租户 000001
        TenantContext.setCurrentTenantId("000000");
        List<SysUser> users1 = userMapper.selectList(null);
        System.out.println(users1);
        // 模拟租户 000002
        TenantContext.setCurrentTenantId("000001");
        List<SysUser> users2 = userMapper.selectList(null);
        System.out.println(users2);
        // 验证数据隔离
        assertNotEquals(users1.size(), users2.size());
    }

}
