package com.yzx.system.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yzx.common.permission.helper.DataPermissionHelper;
import com.yzx.system.handler.PlusDataPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @className: PlusDataPermissionInterceptor
 * @author: yzx
 * @date: 2026/5/24 12:28
 * @Version: 1.0
 * @description: 数据权限拦截器 - 使用字符串拼接避免jsqlparser重新生成SQL的问题
 */
@Slf4j
@Component
public class PlusDataPermissionInterceptor implements InnerInterceptor {

    @Autowired
    private PlusDataPermissionHandler handler;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }
        if (handler.invalid()) return;

        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        String originalSql = mpBoundSql.sql();

        try {
            String newSql = processSelectSql(originalSql);
            mpBoundSql.sql(newSql);
        } catch (Exception e) {
            log.error("数据权限SQL处理失败, 使用原始SQL", e);
        } finally {
            DataPermissionHelper.removePermissionCache();
        }
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();

        if (sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId()))
                return;
            if (handler.invalid()) return;

            PluginUtils.MPBoundSql mpBoundSql = mpSh.mPBoundSql();
            String originalSql = mpBoundSql.sql();

            try {
                String newSql = processUpdateDeleteSql(originalSql, sct);
                mpBoundSql.sql(newSql);
            } catch (Exception e) {
                log.error("数据权限SQL处理失败, 使用原始SQL", e);
            } finally {
                DataPermissionHelper.removePermissionCache();
            }
        }
    }

    /**
     * 处理SELECT语句 - 使用字符串拼接WHERE条件，避免重新生成整个SQL
     */
    private String processSelectSql(String sql) throws JSQLParserException {
        // 解析SQL获取WHERE条件
        Statement stmt = CCJSqlParserUtil.parse(sql);

        if (!(stmt instanceof Select)) {
            return sql;
        }

        Select select = (Select) stmt;
        SelectBody selectBody = select.getSelectBody();

        Expression where = null;
        if (selectBody instanceof PlainSelect) {
            where = ((PlainSelect) selectBody).getWhere();
        } else if (selectBody instanceof SetOperationList) {
            // 对于UNION等操作，只处理第一个SELECT的WHERE
            SetOperationList setOperationList = (SetOperationList) selectBody;
            if (!setOperationList.getSelects().isEmpty()) {
                SelectBody first = setOperationList.getSelects().get(0);
                if (first instanceof PlainSelect) {
                    where = ((PlainSelect) first).getWhere();
                }
            }
        }

        // 获取数据权限条件
        Expression dataPermissionWhere = handler.getSqlSegment(where, true);
        if (dataPermissionWhere == null || dataPermissionWhere.equals(where)) {
            return sql;
        }

        // 使用字符串拼接，而不是重新生成整个SQL
        return appendWhereCondition(sql, dataPermissionWhere.toString(), where);
    }

    /**
     * 在原始SQL的WHERE位置追加条件
     */
    private String appendWhereCondition(String originalSql, String dataPermissionCondition, Expression originalWhere) {
        String upperSql = originalSql.toUpperCase();

        // 如果没有原始WHERE条件，直接添加WHERE
        if (originalWhere == null) {
            // 查找最后一个 FROM 子句的位置
            int fromIndex = findLastFromIndex(originalSql);
            if (fromIndex == -1) {
                return originalSql;
            }

            // 在 FROM 子句后面查找合适的位置插入WHERE
            // 查找 ORDER BY, GROUP BY, HAVING, LIMIT 等关键字
            int insertPos = findWhereInsertPosition(originalSql, fromIndex);

            StringBuilder sb = new StringBuilder(originalSql);
            sb.insert(insertPos, " WHERE (" + dataPermissionCondition + ")");
            return sb.toString();
        } else {
            // 有原始WHERE条件，需要合并
            String originalWhereStr = originalWhere.toString();

            // 查找原始WHERE条件在SQL中的位置
            int whereIndex = findWhereKeywordIndex(originalSql);
            if (whereIndex == -1) {
                return originalSql;
            }

            // 找到WHERE关键字后的第一个非空格位置
            int conditionStart = whereIndex + 5; // "WHERE".length()
            while (conditionStart < originalSql.length() && Character.isWhitespace(originalSql.charAt(conditionStart))) {
                conditionStart++;
            }

            // 找到原始条件的结束位置（即下一个关键字的位置）
            int conditionEnd = findConditionEndIndex(originalSql, conditionStart);

            // 构建新的WHERE条件
            StringBuilder sb = new StringBuilder(originalSql);
            String newCondition = "(" + originalSql.substring(conditionStart, conditionEnd) + ") AND (" + dataPermissionCondition + ")";
            sb.replace(conditionStart, conditionEnd, newCondition);

            return sb.toString();
        }
    }

    /**
     * 查找最后一个 FROM 关键字的位置
     */
    private int findLastFromIndex(String sql) {
        String upperSql = sql.toUpperCase();
        int lastIndex = -1;
        int fromIndex = 0;

        while ((fromIndex = upperSql.indexOf(" FROM ", fromIndex)) != -1) {
            lastIndex = fromIndex;
            fromIndex += 6;
        }

        return lastIndex;
    }

    /**
     * 查找WHERE关键字的位置
     */
    private int findWhereKeywordIndex(String sql) {
        String upperSql = sql.toUpperCase();
        Pattern pattern = Pattern.compile("\\sWHERE\\s", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.start();
        }
        return -1;
    }

    /**
     * 查找WHERE条件应该插入的位置
     */
    private int findWhereInsertPosition(String sql, int fromIndex) {
        String upperSql = sql.toUpperCase();
        String[] keywords = {" ORDER ", " GROUP ", " HAVING ", " LIMIT ", " UNION ", " INTERSECT ", " EXCEPT ", " FOR "};

        int minPos = sql.length();
        for (String keyword : keywords) {
            int pos = upperSql.indexOf(keyword, fromIndex);
            if (pos != -1 && pos < minPos) {
                minPos = pos;
            }
        }

        return minPos;
    }

    /**
     * 查找条件的结束位置
     */
    private int findConditionEndIndex(String sql, int startPos) {
        String upperSql = sql.toUpperCase();
        String[] keywords = {" ORDER ", " GROUP ", " HAVING ", " LIMIT ", " UNION ", " INTERSECT ", " EXCEPT ", " FOR "};

        int minPos = sql.length();
        for (String keyword : keywords) {
            int pos = upperSql.indexOf(keyword, startPos);
            if (pos != -1 && pos < minPos) {
                minPos = pos;
            }
        }

        return minPos;
    }

    /**
     * 处理UPDATE/DELETE语句
     */
    private String processUpdateDeleteSql(String sql, SqlCommandType type) throws JSQLParserException {
        Statement stmt = CCJSqlParserUtil.parse(sql);

        Expression where = null;
        if (stmt instanceof Update) {
            where = ((Update) stmt).getWhere();
        } else if (stmt instanceof Delete) {
            where = ((Delete) stmt).getWhere();
        } else {
            return sql;
        }

        Expression dataPermissionWhere = handler.getSqlSegment(where, false);
        if (dataPermissionWhere == null || dataPermissionWhere.equals(where)) {
            return sql;
        }

        return appendWhereCondition(sql, dataPermissionWhere.toString(), where);
    }
}
