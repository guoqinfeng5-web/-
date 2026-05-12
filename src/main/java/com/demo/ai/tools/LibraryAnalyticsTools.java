package com.demo.ai.tools;

import com.demo.ai.sql.SafeSqlQueryExecutor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 图书馆统计分析 Tool：由模型生成 SQL，经安全校验后查库，结果回传模型生成自然语言答案。
 */
@Component
@RequiredArgsConstructor
public class LibraryAnalyticsTools {

    private final SafeSqlQueryExecutor safeSqlQueryExecutor;

    @Tool("对图书馆数据库执行只读查询。表仅可为 books、borrow_records。必须是一条 MySQL SELECT，且包含 LIMIT（≤100）。返回 JSON 数组字符串，每行一对象。")
    public String queryLibraryDatabase(
            @P("完整的一条 MySQL SELECT 语句，仅使用 books 与 borrow_records，必须带 LIMIT") String sql) {
        return safeSqlQueryExecutor.execute(sql);
    }
}
