package com.demo.ai.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 在通过 {@link ReadOnlySqlValidator} 校验后，执行只读查询并将结果序列化为 JSON 字符串供模型总结。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafeSqlQueryExecutor {

    private final ReadOnlySqlValidator readOnlySqlValidator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @return JSON 数组字符串，元素为行 Map（列名 → 值）
     */
    public String execute(String sql) {
        try {
            String normalized = readOnlySqlValidator.validateAndNormalize(sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(normalized);
            return objectMapper.writeValueAsString(rows);
        } catch (SecurityException e) {
            log.warn("SQL 校验拒绝: {}", e.getMessage());
            return "QUERY_REJECTED: " + e.getMessage();
        } catch (JSQLParserException e) {
            log.warn("SQL 解析失败: {}", e.getMessage());
            return "QUERY_PARSE_ERROR: " + e.getMessage();
        } catch (JsonProcessingException e) {
            log.warn("结果序列化失败", e);
            return "SERIALIZE_ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.warn("查询执行失败", e);
            return "EXECUTION_ERROR: " + e.getMessage();
        }
    }
}
