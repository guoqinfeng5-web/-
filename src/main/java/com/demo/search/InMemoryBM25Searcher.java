package com.demo.search;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 纯内存 BM25 检索（倒排索引）。
 *
 * <p>适用场景：Demo/中小规模数据（几十~几万文档），无需额外中间件，易于讲清原理与调试。
 */
public class InMemoryBM25Searcher implements DocumentSearcher {

    /**
     * BM25 参数（常用默认）。
     *
     * <p>k1：控制 TF 饱和速度（越大越强调高 TF）；
     * b ：控制长度归一化强度（越大越惩罚长文档）。
     */
    private final double k1;
    private final double b;

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    /**
     * token -> (docId -> tf)
     */
    private final Map<String, Map<Integer, Integer>> invertedIndex = new HashMap<>();

    /**
     * token -> df（出现该 token 的文档数）
     */
    private final Map<String, Integer> docFreq = new HashMap<>();

    /**
     * docId -> docLen（token 数）
     */
    private int[] docLen = new int[0];

    private int docCount = 0;
    private double avgDocLen = 0D;

    /**
     * 简单停用词表（可按需扩充）。
     */
    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "的", "了", "是", "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "这", "那", "一个", "一些", "目前", "现在", "请", "帮", "一下",
            "以及", "并且", "或者", "如果", "那么", "因为", "所以",
            "吗", "呢", "呀", "啊", "哦",
            "有", "没有", "多少", "怎么", "什么", "哪个", "哪些", "是否",
            "书", "图书"
    )));

    public InMemoryBM25Searcher(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    @Override
    public synchronized void rebuild(List<String> documents) {
        invertedIndex.clear();
        docFreq.clear();
        docCount = documents == null ? 0 : documents.size();
        docLen = new int[docCount];
        avgDocLen = 0D;
        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (int docId = 0; docId < documents.size(); docId++) {
            String text = documents.get(docId);
            List<String> tokens = tokenize(text);
            docLen[docId] = tokens.size();
            avgDocLen += tokens.size();

            // 统计本 doc 的 tf
            Map<String, Integer> tfMap = new HashMap<>();
            for (String t : tokens) {
                tfMap.merge(t, 1, Integer::sum);
            }

            // 写入倒排索引，同时累加 df（每个 token 在该 doc 只计一次 df）
            for (Map.Entry<String, Integer> e : tfMap.entrySet()) {
                String token = e.getKey();
                int tf = e.getValue();
                invertedIndex.computeIfAbsent(token, k -> new HashMap<>()).put(docId, tf);
                docFreq.merge(token, 1, Integer::sum);
            }
        }

        avgDocLen = avgDocLen / Math.max(1, docCount);
    }

    @Override
    public synchronized List<SearchResult> search(String query, int topN) {
        if (query == null || query.isBlank() || docCount == 0) {
            return List.of();
        }
        int n = Math.max(1, topN);

        // BM25 累积分：docId -> score
        Map<Integer, Double> scoreMap = new HashMap<>();
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }

        for (String token : qTokens) {
            Map<Integer, Integer> postings = invertedIndex.get(token);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            int df = docFreq.getOrDefault(token, 0);
            double idf = idf(docCount, df);

            for (Map.Entry<Integer, Integer> p : postings.entrySet()) {
                int docId = p.getKey();
                int tf = p.getValue();

                // ---------- BM25 核心公式（带中文注释，便于面试讲解） ----------
                // TF：term frequency，token 在文档 d 中出现的次数（越多通常越相关，但会“饱和”）
                // |d|：文档长度（token 数），用于长度归一化（避免长文本天然包含更多词导致偏高）
                // avgdl：平均文档长度
                //
                // TF 归一化（带饱和）：
                //   tfNorm = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * |d| / avgdl))
                //
                // IDF：inverse document frequency，越稀有的词信息量越大：
                //   idf = ln(1 + (N - df + 0.5) / (df + 0.5))
                //
                // 最终得分：
                //   score(d, q) = Σ_{t∈q} idf(t) * tfNorm(t, d)
                // -----------------------------------------------------------
                double tfNorm = tfNorm(tf, docLen[docId], avgDocLen);
                double add = idf * tfNorm;
                scoreMap.merge(docId, add, Double::sum);
            }
        }

        if (scoreMap.isEmpty()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>(scoreMap.size());
        for (Map.Entry<Integer, Double> e : scoreMap.entrySet()) {
            results.add(new SearchResult(e.getKey(), e.getValue()));
        }
        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        if (results.size() > n) {
            return results.subList(0, n);
        }
        return results;
    }

    private double tfNorm(int tf, int docLen, double avgDocLen) {
        double dl = Math.max(1, docLen);
        double avg = Math.max(1e-9, avgDocLen);
        return (tf * (k1 + 1D)) / (tf + k1 * (1D - b + b * (dl / avg)));
    }

    private double idf(int n, int df) {
        // ln(1 + (N - df + 0.5)/(df + 0.5))
        double numerator = (n - df + 0.5D);
        double denominator = (df + 0.5D);
        return Math.log(1D + (numerator / Math.max(1e-9, denominator)));
    }

    /**
     * Jieba 分词 + 简单清洗。
     *
     * <p>策略：
     * - 保留英文/数字 token（如 JVM、SpringBoot、Java17）
     * - 中文 token 过滤长度 1 的噪声
     * - 过滤停用词与纯空白
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<SegToken> seg = segmenter.process(normalized, JiebaSegmenter.SegMode.SEARCH);
        if (seg == null || seg.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(seg.size());
        for (SegToken token : seg) {
            if (token == null || token.word == null) {
                continue;
            }
            String w = token.word.trim();
            if (w.isEmpty()) {
                continue;
            }
            if (STOP_WORDS.contains(w)) {
                continue;
            }
            // 过滤过短噪声：中文单字、标点等
            if (w.length() == 1 && isMostlyChinese(w)) {
                continue;
            }
            // 过滤纯标点
            if (isPunctuationOnly(w)) {
                continue;
            }
            out.add(w);
        }
        return out;
    }

    private boolean isPunctuationOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return false;
            }
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    private boolean isMostlyChinese(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}

