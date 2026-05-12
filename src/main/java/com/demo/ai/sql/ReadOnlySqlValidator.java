package com.demo.ai.sql;

import com.demo.common.AppConstants;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 校验 NL2SQL 仅生成可执行的只读查询：单语句 SELECT、白名单表、LIMIT 上限。
 * 表白名单与 LIMIT 上下限见 {@link AppConstants#NL2SQL_ALLOWED_TABLES} 等。
 */
@Component
public class ReadOnlySqlValidator {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "INSERT ", "UPDATE ", "DELETE ", "MERGE ", "DROP ", "TRUNCATE ", "ALTER ",
            "CREATE ", "REPLACE ", "GRANT ", "REVOKE ", "EXEC ", "CALL ", " INTO OUTFILE",
            " INFILE ", "LOAD ", "HANDLER ", "PREPARE ", "EXECUTE "
    );

    /**
     * 解析并规范化 SQL；若无 LIMIT 则追加默认 LIMIT。
     *
     * @throws SecurityException 不满足安全策略时
     * @throws JSQLParserException 无法解析时
     */
    public String validateAndNormalize(String sql) throws JSQLParserException {
        if (sql == null || sql.isBlank()) {
            throw new SecurityException("SQL 不能为空");
        }
        String cleaned = sql.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.contains(";")) {
            throw new SecurityException("禁止多条语句");
        }

        String upper = cleaned.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")) {
            throw new SecurityException("仅允许 SELECT");
        }
        for (String bad : FORBIDDEN_TOKENS) {
            if (upper.contains(bad)) {
                throw new SecurityException("含有禁止关键字: " + bad.trim());
            }
        }

        Statement statement = CCJSqlParserUtil.parse(cleaned);
        if (!(statement instanceof Select select)) {
            throw new SecurityException("仅支持 SELECT");
        }
        if (select.getPlainSelect() == null) {
            throw new SecurityException("仅支持单条 SELECT（不支持 UNION 等）");
        }
        PlainSelect plain = select.getPlainSelect();

        TablesNamesFinder finder = new TablesNamesFinder();
        List<String> tables = finder.getTableList(statement);
        for (String rawName : tables) {
            String name = stripQuotes(rawName).toLowerCase(Locale.ROOT);
            if (!AppConstants.NL2SQL_ALLOWED_TABLES.contains(name)) {
                throw new SecurityException("不允许访问表: " + rawName);
            }
        }

        enforceLimit(plain);
        return select.toString();
    }

    private static void enforceLimit(PlainSelect plain) {
        Limit limit = plain.getLimit();
        if (limit == null) {
            Limit lim = new Limit();
            lim.setRowCount(new LongValue(AppConstants.NL2SQL_DEFAULT_LIMIT));
            plain.setLimit(lim);
            return;
        }
        long rowCount = extractLimitValue(limit);
        if (rowCount < 1) {
            throw new SecurityException("LIMIT 必须为正数");
        }
        if (rowCount > AppConstants.NL2SQL_MAX_LIMIT) {
            throw new SecurityException("LIMIT 不能超过 " + AppConstants.NL2SQL_MAX_LIMIT);
        }
    }

    private static long extractLimitValue(Limit limit) {
        if (limit.getRowCount() instanceof LongValue lv) {
            return lv.getValue();
        }
        throw new SecurityException("LIMIT 须为固定整数，便于校验");
    }

    private static String stripQuotes(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim();
        if (n.startsWith("`") && n.endsWith("`") && n.length() >= 2) {
            return n.substring(1, n.length() - 1);
        }
        return n;
    }
}
