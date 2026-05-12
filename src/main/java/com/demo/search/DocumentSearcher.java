package com.demo.search;

import java.util.List;

/**
 * 文档检索接口（用于 RAG 召回层）。
 *
 * <p>设计目标：把检索实现（BM25/倒排索引、Lucene、ES 等）与业务服务解耦，
 * 便于替换或做 Hybrid Retrieval。
 */
public interface DocumentSearcher {

    /**
     * 重建索引（全量）。
     *
     * @param documents 文档列表（每个文档是可检索文本）
     */
    void rebuild(List<String> documents);

    /**
     * 搜索 topN。
     */
    List<SearchResult> search(String query, int topN);

    record SearchResult(int docId, double score) {
    }
}

