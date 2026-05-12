package com.demo.search;

import com.demo.common.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("内存 BM25 倒排检索单元测试")
class InMemoryBM25SearcherTest {

    private InMemoryBM25Searcher searcher;

    @BeforeEach
    void setUp() {
        // Given：与 VectorStoreService / AppConstants 中 BM25 参数一致
        searcher = new InMemoryBM25Searcher(AppConstants.BM25_K1, AppConstants.BM25_B);
    }

    @Test
    @DisplayName("空索引或未 rebuild - 搜索返回空列表")
    void testSearch_NoRebuild_ReturnsEmpty() {
        // When
        List<DocumentSearcher.SearchResult> hits = searcher.search("Java虚拟机", 5);

        // Then
        assertTrue(hits.isEmpty());
    }

    @Test
    @DisplayName("rebuild 后 - 包含查询词的文档得分更高")
    void testSearch_AfterRebuild_RelevantDocRanksFirst() {
        // Given：避免停用词「书」等；用可切分专有词
        List<String> docs = List.of(
                "title: 红楼梦 author: 曹雪芹 category: 文学 summary: 古典小说",
                "title: 深入理解Java虚拟机 author: 周志明 category: 计算机 summary: JVM垃圾回收"
        );
        searcher.rebuild(docs);

        // When
        List<DocumentSearcher.SearchResult> hits = searcher.search("Java虚拟机", 2);

        // Then
        assertEquals(2, hits.size());
        assertEquals(1, hits.get(0).docId());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
    }

    @Test
    @DisplayName("查询为空或 null - 返回空")
    void testSearch_BlankQuery_ReturnsEmpty() {
        // Given
        searcher.rebuild(List.of("alpha beta", "gamma delta"));

        // When & Then
        assertTrue(searcher.search(null, 5).isEmpty());
        assertTrue(searcher.search("", 5).isEmpty());
        assertTrue(searcher.search("   ", 5).isEmpty());
    }

    @Test
    @DisplayName("rebuild(null) - 搜索安全返回空")
    void testRebuild_NullDocuments_SearchReturnsEmpty() {
        // Given
        searcher.rebuild(null);

        // When
        List<DocumentSearcher.SearchResult> hits = searcher.search("anything", 3);

        // Then
        assertTrue(hits.isEmpty());
    }
}
