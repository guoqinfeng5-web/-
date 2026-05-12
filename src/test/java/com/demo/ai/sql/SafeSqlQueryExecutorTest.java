package com.demo.ai.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import net.sf.jsqlparser.JSQLParserException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("安全 SQL 执行器单元测试")
@ExtendWith(MockitoExtension.class)
class SafeSqlQueryExecutorTest {

    @Mock
    private ReadOnlySqlValidator readOnlySqlValidator;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("校验通过且查询成功 - 返回 JSON 数组字符串")
    void testExecute_ValidSql_ReturnsJson() throws JSQLParserException {
        // Given
        SafeSqlQueryExecutor executor = new SafeSqlQueryExecutor(readOnlySqlValidator, jdbcTemplate, objectMapper);
        when(readOnlySqlValidator.validateAndNormalize(anyString())).thenReturn("SELECT id FROM books LIMIT 1");
        when(jdbcTemplate.queryForList("SELECT id FROM books LIMIT 1"))
                .thenReturn(List.of(Map.of("id", 1L)));

        // When
        String json = executor.execute("SELECT id FROM books");

        // Then
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("id"));
    }

    @Test
    @DisplayName("校验拒绝 - 返回 QUERY_REJECTED 前缀")
    void testExecute_ValidatorRejects_ReturnsRejected() throws JSQLParserException {
        // Given
        SafeSqlQueryExecutor executor = new SafeSqlQueryExecutor(readOnlySqlValidator, jdbcTemplate, objectMapper);
        when(readOnlySqlValidator.validateAndNormalize(anyString()))
                .thenThrow(new SecurityException("不允许访问表"));

        // When
        String out = executor.execute("SELECT * FROM secret");

        // Then
        assertTrue(out.startsWith("QUERY_REJECTED:"));
        assertTrue(out.contains("不允许访问表"));
    }

    @Test
    @DisplayName("执行阶段异常 - 返回 EXECUTION_ERROR 前缀")
    void testExecute_JdbcFails_ReturnsExecutionError() throws JSQLParserException {
        // Given
        SafeSqlQueryExecutor executor = new SafeSqlQueryExecutor(readOnlySqlValidator, jdbcTemplate, objectMapper);
        when(readOnlySqlValidator.validateAndNormalize(anyString())).thenReturn("SELECT 1 LIMIT 1");
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("connection down"));

        // When
        String out = executor.execute("SELECT 1");

        // Then
        assertTrue(out.startsWith("EXECUTION_ERROR:"));
        assertTrue(out.contains("connection down"));
    }
}
