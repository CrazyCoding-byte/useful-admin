package com.yzx.system.handler;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.yzx.common.permission.helper.DataPermissionHelper;
import com.yzx.model.annotation.DataColumn;
import com.yzx.model.annotation.DataPermission;
import com.yzx.model.enums.DataScopeType;
import com.yzx.model.system.SysRole;
import com.yzx.model.system.SysUser;
import com.yzx.system.service.ISysRoleService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @className: PlusDataPermissionHandler
 * @author: yzx
 * @date: 2026/5/24 13:12
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
public class PlusDataPermissionHandler implements ApplicationContextAware {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParserContext parserContext = new TemplateParserContext();
    private BeanResolver beanResolver;
    @Autowired
    private ISysRoleService roleService;

    private List<SysRole> getUserRoles(SysUser user) {
        return roleService.selectRolesByUserId(user.getUserId());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.beanResolver = new BeanFactoryResolver(applicationContext);
    }

    public Expression getSqlSegment(Expression where, boolean isSelect) {
        try {
            DataPermission dataPermission = getDataPermission();
            if (dataPermission == null) return where;

            SysUser user = DataPermissionHelper.getVariable("user");
            if (user == null) return where;

            String dataFilterSql = buildDataFilter(dataPermission, isSelect);
            if (StringUtils.isBlank(dataFilterSql)) {
                return where;
            }
            Expression expression = CCJSqlParserUtil.parseExpression(dataFilterSql);
            Parenthesis parenthesis = new Parenthesis(expression);
            if (!ObjectUtil.isEmpty(where)) {
                return new AndExpression(where, parenthesis);
            }
            return parenthesis;
        } catch (JSQLParserException e) {
            throw new RuntimeException("数据权限解析异常 => " + e.getMessage());
        } finally {
            DataPermissionHelper.removePermissionCache();  // ← 加这行！
        }
    }

    private String buildDataFilter(DataPermission dataPermission, boolean isSelect) {
        String joinStr = isSelect ? " OR " : " AND ";
        if (StringUtils.isNotBlank(dataPermission.joinStr())) {
            joinStr = " " + dataPermission.joinStr() + " ";
        }
        SysUser user = DataPermissionHelper.getVariable("user");
        if (user == null || user.getUserId() == null) {
            return "";
        }
        Object defaultValue = "-1";
        NullSafeEvaluationContext context = new NullSafeEvaluationContext(beanResolver, defaultValue);
        DataPermissionHelper.getContext().forEach(context::setVariable);
        Set<String> conditions = new HashSet<>();
        List<String> keys = new ArrayList<>();
        for (DataColumn dataColumn : dataPermission.value()) {
            if (dataColumn.key().length != dataColumn.value().length) {
                throw new RuntimeException("数据权限注解 key与value长度不匹配");
            }
            for (int i = 0; i < dataColumn.key().length; i++) {
                context.setVariable(dataColumn.key()[i], dataColumn.value()[i]);
            }
            keys.addAll(Arrays.stream(dataColumn.key()).map(k -> "#" + k).collect(Collectors.toList()));
        }

        //获取用户角色
        List<SysRole> roles = getUserRoles(user);
        for (SysRole role : roles) {
            DataScopeType type = DataScopeType.getByCode(role.getDataScope());
            if (ObjectUtil.isNull(type)) continue;
            //全部数据权限直接返回
            if (type == DataScopeType.ALL) return "";
            boolean isSuccess = false;
            for (DataColumn dataColumn : dataPermission.value()) {
                if (!org.apache.commons.lang3.StringUtils.containsAny(type.getSqlTemplate(), keys.toArray(new String[0]))) {
                    continue;
                }
                String sql = parser.parseExpression(type.getSqlTemplate(), parserContext).getValue(context, String.class);
                conditions.add(joinStr + sql);
                isSuccess = true;
            }
            if (!isSuccess && StringUtils.isNotBlank(type.getElseSql())) {
                conditions.add(joinStr + type.getElseSql());
            }
        }
        if (CollUtil.isNotEmpty(conditions)) {
            String sql = String.join("", conditions);
            return sql.substring(joinStr.length());
        }
        return "";
    }

    public DataPermission getDataPermission() {
        return DataPermissionHelper.getPermission();
    }

    public boolean invalid() {
        return getDataPermission() == null;
    }


    @AllArgsConstructor
    private static class NullSafeEvaluationContext extends StandardEvaluationContext {
        private final Object defaultValue;

        NullSafeEvaluationContext(BeanResolver beanResolver, Object defaultValue) {
            this.defaultValue = defaultValue;
            if (beanResolver != null) {
                setBeanResolver(beanResolver);
            }
        }

        @Override
        public Object lookupVariable(String name) {
            Object obj = super.lookupVariable(name);
            return obj == null ? defaultValue : obj;
        }
    }
}
