package com.demo.ai.sql;

import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("只读 SQL 校验单元测试")
class ReadOnlySqlValidatorTest {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();

    @Test
    @DisplayName("books 表 SELECT 无 LIMIT - 自动补全 LIMIT")
    void testValidateAndNormalize_BooksSelect_AppendsLimit() throws JSQLParserException {
        // When
        String sql = validator.validateAndNormalize("SELECT id, title FROM books");

        // Then
        assertTrue(sql.toLowerCase().contains("limit"));
    }

    @Test
    @DisplayName("borrow_records JOIN books - 允许白名单表")
    void testValidateAndNormalize_JoinBorrowRecords_Allowed() throws JSQLParserException {
        // When
        String sql = validator.validateAndNormalize(
                "SELECT b.title FROM borrow_records r JOIN books b ON r.book_id = b.id LIMIT 10");

        // Then
        assertTrue(sql.toLowerCase().contains("limit"));
    }

    @Test
    @DisplayName("非 SELECT - 拒绝")
    void testValidateAndNormalize_Delete_Throws() {
        assertThrows(SecurityException.class, () -> validator.validateAndNormalize("DELETE FROM books"));
    }

    @Test
    @DisplayName("非白名单表 - 拒绝")
    void testValidateAndNormalize_UnknownTable_Throws() {
        assertThrows(SecurityException.class, () ->
                validator.validateAndNormalize("SELECT * FROM users LIMIT 1"));
    }

    @Test
    @DisplayName("多条语句 - 拒绝")
    void testValidateAndNormalize_MultiStatement_Throws() {
        assertThrows(SecurityException.class, () ->
                validator.validateAndNormalize("SELECT 1; SELECT 2 LIMIT 1"));
    }
}
