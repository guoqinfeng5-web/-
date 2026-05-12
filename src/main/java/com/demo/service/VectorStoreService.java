package com.demo.service;

import com.demo.common.AppConstants;
import com.demo.entity.Book;
import com.demo.config.DashScopeEmbeddingProperties;
import com.demo.search.DocumentSearcher;
import com.demo.search.InMemoryBM25Searcher;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

@Slf4j
@Service
public class VectorStoreService {

    // 使用 DashScope OpenAI 兼容 Embedding 接口
    private final OpenAiEmbeddingModel embeddingModel;
    private final List<StoredSegment> memoryStore = new ArrayList<>();

    /**
     * BM25 检索器（倒排索引），与向量检索组成 Hybrid Retrieval。
     */
    private final DocumentSearcher bm25Searcher;

    public VectorStoreService(DashScopeEmbeddingProperties properties) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(JdkHttpClient.builder())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .logRequests(true)
                .logResponses(true)
                .build();

        this.bm25Searcher = new InMemoryBM25Searcher(AppConstants.BM25_K1, AppConstants.BM25_B);
    }

    public synchronized void ingest(List<Book> books) {
        if (books == null || books.isEmpty()) {
            return;
        }
        memoryStore.clear();
        List<String> docs = new ArrayList<>(books.size());
        for (Book book : books) {
            if (book == null) {
                continue;
            }
            String featureText = """
                    title: %s
                    author: %s
                    category: %s
                    price: %s
                    score: %s
                    stock: %s
                    tags: %s
                    borrowCount: %s
                    summary: %s
                    """.formatted(
                    safe(book.getTitle()),
                    safe(book.getAuthor()),
                    safe(book.getCategory()),
                    String.valueOf(book.getPrice()),
                    String.valueOf(book.getScore()),
                    String.valueOf(book.getStock()),
                    safe(book.getTags()),
                    String.valueOf(book.getBorrowCount()),
                    safe(book.getSummary())
            );
            TextSegment segment = TextSegment.from(featureText);
            Embedding embedding = embeddingModel.embed(featureText).content();
            memoryStore.add(new StoredSegment(segment, embedding));
            docs.add(featureText);
        }
        // 全量重建倒排索引（Demo 场景简化实现）
        bm25Searcher.rebuild(docs);
    }

    public synchronized List<SearchHit> search(String question, int k) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (memoryStore.isEmpty()) {
            return List.of();
        }
        // 保持旧接口语义：返回 TopK 的向量余弦命中（但补充 docId，便于 Hybrid 对齐）
        return searchVectorCandidates(question, Math.max(1, k));
    }

    /**
     * Hybrid Retrieval：BM25（倒排）+ 向量余弦。
     *
     * <p>规则优先（实体命中优先）：\n
     * - 若用户问题包含明显书名实体（《...》），或 BM25 命中非常强，则优先使用 BM25 TopK。\n
     * - 否则对 BM25 与向量分数做归一化后加权融合，再取 TopK。
     */
    public synchronized List<SearchHit> hybridSearch(String question, int k) {
        return hybridSearch(question, k, false);
    }

    public synchronized List<SearchHit> hybridSearch(String question, int k, boolean debug) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (memoryStore.isEmpty()) {
            return List.of();
        }

        int topK = Math.max(1, k);

        List<DocumentSearcher.SearchResult> bm25 = bm25Searcher.search(question, AppConstants.HYBRID_BM25_CANDIDATES);
        List<SearchHit> vec = searchVectorCandidates(question, AppConstants.HYBRID_VECTOR_CANDIDATES);

        boolean entityQuery = containsBookTitleEntity(question) || isStrongBm25Signal(bm25);

        // docId -> scores
        Map<Integer, Double> bm25Score = new HashMap<>();
        for (DocumentSearcher.SearchResult r : bm25) {
            bm25Score.put(r.docId(), r.score());
        }
        Map<Integer, Double> vecScore = new HashMap<>();
        for (SearchHit h : vec) {
            vecScore.put(h.docId, h.score);
        }

        Set<Integer> candidates = new HashSet<>();
        candidates.addAll(bm25Score.keySet());
        candidates.addAll(vecScore.keySet());
        if (candidates.isEmpty()) {
            return List.of();
        }

        double bm25Top1 = bm25.isEmpty() ? 0D : bm25.get(0).score();
        double vecTop1 = vec.isEmpty() ? 0D : vec.get(0).score;

        List<SearchHit> merged = new ArrayList<>(candidates.size());
        List<HybridDebugRow> debugRows = debug ? new ArrayList<>(candidates.size()) : List.of();
        for (Integer docId : candidates) {
            double bScore = bm25Score.getOrDefault(docId, 0D);
            double vScore = vecScore.getOrDefault(docId, 0D);

            double finalScore;
            if (entityQuery) {
                // 实体优先：BM25 为主，向量作为轻量补充（避免同名歧义时完全忽略语义）
                double nb = normalizeByTop1(bScore, bm25Top1);
                double nv = normalizeByTop1(vScore, vecTop1);
                finalScore = AppConstants.HYBRID_ENTITY_BM25_WEIGHT * nb + AppConstants.HYBRID_ENTITY_VECTOR_WEIGHT * nv;
            } else {
                // 通用加权融合
                double nb = normalizeByTop1(bScore, bm25Top1);
                double nv = normalizeByTop1(vScore, vecTop1);
                finalScore = AppConstants.HYBRID_BM25_WEIGHT * nb + AppConstants.HYBRID_VECTOR_WEIGHT * nv;
            }

            String text = memoryStore.get(docId).segment.text();
            merged.add(new SearchHit(docId, text, finalScore));
            if (debug) {
                debugRows.add(new HybridDebugRow(docId, bScore, vScore, finalScore));
            }
        }

        merged.sort((a, b) -> Double.compare(b.score, a.score));
        if (debug) {
            debugRows.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
            int show = Math.min(topK, debugRows.size());
            StringBuilder sb = new StringBuilder(512);
            sb.append("HybridRetrieval debug:")
                    .append(" entityQuery=").append(entityQuery)
                    .append(", bm25Top1=").append(String.format(java.util.Locale.ROOT, "%.4f", bm25Top1))
                    .append(", vecTop1=").append(String.format(java.util.Locale.ROOT, "%.4f", vecTop1))
                    .append(", topK=").append(topK)
                    .append('\n');
            for (int i = 0; i < show; i++) {
                HybridDebugRow r = debugRows.get(i);
                sb.append("  #").append(i + 1)
                        .append(" docId=").append(r.docId)
                        .append(" bm25=").append(String.format(java.util.Locale.ROOT, "%.4f", r.bm25Score))
                        .append(" vec=").append(String.format(java.util.Locale.ROOT, "%.4f", r.vecScore))
                        .append(" final=").append(String.format(java.util.Locale.ROOT, "%.4f", r.finalScore))
                        .append('\n');
            }
            log.info(sb.toString().trim());
        }
        if (merged.size() > topK) {
            return merged.subList(0, topK);
        }
        return merged;
    }

    private List<SearchHit> searchVectorCandidates(String question, int n) {
        int maxResults = Math.max(1, n);
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        // docId 取 memoryStore 下标，便于与 BM25 docId 对齐
        List<SearchHit> hits = new ArrayList<>(memoryStore.size());
        for (int i = 0; i < memoryStore.size(); i++) {
            StoredSegment stored = memoryStore.get(i);
            hits.add(new SearchHit(i, stored.segment.text(), cosineSimilarity(queryEmbedding, stored.embedding)));
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        if (hits.size() > maxResults) {
            return hits.subList(0, maxResults);
        }
        return hits;
    }

    private boolean containsBookTitleEntity(String q) {
        if (q == null) return false;
        Matcher m = AppConstants.BOOK_TITLE_PATTERN.matcher(q);
        return m.find();
    }

    /**
     * 判定 BM25 命中是否“非常强”。这是经验规则，用于“实体查询优先 BM25”。
     */
    private boolean isStrongBm25Signal(List<DocumentSearcher.SearchResult> bm25) {
        if (bm25 == null || bm25.isEmpty()) {
            return false;
        }
        if (bm25.size() == 1) {
            return bm25.get(0).score() > AppConstants.BM25_STRONG_TOP1_ABSOLUTE;
        }
        double s1 = bm25.get(0).score();
        double s2 = bm25.get(1).score();
        if (s1 <= 0D) return false;
        // Top1 明显领先 Top2（常见于书名/作者/标签强匹配）
        return (s2 <= 1e-9) || (s1 / s2) >= AppConstants.BM25_STRONG_TOP1_TOP2_RATIO;
    }

    private double normalizeByTop1(double score, double top1) {
        if (score <= 0D || top1 <= 1e-9) {
            return 0D;
        }
        return score / top1;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    public record SearchHit(int docId, String text, double score) {
    }

    private record HybridDebugRow(int docId, double bm25Score, double vecScore, double finalScore) {
    }

    private double cosineSimilarity(Embedding query, Embedding target) {
        float[] a = query.vector();
        float[] b = target.vector();
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0D;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0D;
        double normA = 0D;
        double normB = 0D;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0D || normB == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record StoredSegment(TextSegment segment, Embedding embedding) {
    }
}

