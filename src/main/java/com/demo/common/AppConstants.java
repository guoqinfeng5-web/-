package com.demo.common;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一管理跨模块使用的静态常量（全部大写）。
 *
 * <p>约定：
 * <ul>
 *   <li>仅放「跨类复用」或「需要全局统一口径」的常量</li>
 *   <li>大段系统 Prompt 仍放在各模块（如 {@code LibraryAnalyticsSchema}）；本类可放短规则、阈值、关键词列表</li>
 * </ul>
 *
 * <h2>常量分区与引用位置</h2>
 * <ul>
 *   <li><b>会话 / RAG 展示</b> — {@link com.demo.service.AiService}、{@link com.demo.service.ChatMemoryService}</li>
 *   <li><b>Hybrid 检索（BM25 + 向量）</b> — {@link com.demo.service.VectorStoreService}；BM25 参数与测试可参考
 *       {@link com.demo.search.InMemoryBM25Searcher}、{@code src/test/.../InMemoryBM25SearcherTest}</li>
 *   <li><b>NL2SQL 校验与前缀</b> — {@link com.demo.ai.sql.ReadOnlySqlValidator}、
 *       {@link com.demo.service.LibraryAnalyticsAnswerService}</li>
 *   <li><b>统计意图正则</b> — {@link com.demo.ai.intent.StructuredAnalyticsIntentDetector}</li>
 *   <li><b>RAG 关键词门控 / 答案清洗</b> — {@link com.demo.service.AiService}</li>
 *   <li><b>API 默认值</b> — {@link com.demo.controller.AiController}</li>
 * </ul>
 */
public final class AppConstants {

    private AppConstants() {
    }

    // -------------------------------------------------------------------------
    // 会话与 Redis 记忆
    // 使用方：AiService、ChatMemoryService、LlmSummaryService（间接）
    // -------------------------------------------------------------------------

    /** Demo 会话 ID（用于 Redis 记忆、摘要压缩等）。 */
    public static final String DEMO_SESSION_ID = "demo_user_001";

    /** Redis 历史 key 前缀。 */
    public static final String HISTORY_KEY_PREFIX = "ai:chat:history:v2:";

    /** 历史消息保留上限（超过则触发压缩摘要）。 */
    public static final int MAX_HISTORY_MESSAGES = 10;

    // -------------------------------------------------------------------------
    // RAG 回答与来源标记
    // 使用方：AiService
    // -------------------------------------------------------------------------

    /** 馆藏数据来源标记（回答末尾追加）。 */
    public static final String SOURCE_TAG = "[数据来源：馆藏库]";

    /** 聊天助手默认人设（RAG 路径使用）。 */
    public static final String SYSTEM_PERSONA = "你是“智能图书管员”，首要任务是帮用户找书，但你也是一个博学、温暖的伙伴。";

    /** 向量检索命中阈值；Hybrid 融合分与此比较，用于是否采用强馆藏上下文（hasStrongContext）。 */
    public static final double MIN_VECTOR_SCORE = 0.45D;

    /**
     * 主链路 Hybrid 检索返回给 Prompt 的条数（与向量/BM25 候选池不同）。
     * 使用方：AiService.analyze 中调用 {@code vectorStoreService.hybridSearch(question, RAG_HYBRID_TOP_K, debug)}。
     */
    public static final int RAG_HYBRID_TOP_K = 5;

    // -------------------------------------------------------------------------
    // Hybrid：候选池、融合权重、BM25 参数、实体优先规则
    // 使用方：VectorStoreService；InMemoryBM25Searcher 构造参数与 BM25_K1/BM25_B 保持一致
    // -------------------------------------------------------------------------

    /** BM25 路召回候选数（每查询从倒排取前 N 条参与融合）。 */
    public static final int HYBRID_BM25_CANDIDATES = 50;

    /** 向量余弦路召回候选数。 */
    public static final int HYBRID_VECTOR_CANDIDATES = 50;

    /** 默认融合：BM25 归一化分权重。 */
    public static final double HYBRID_BM25_WEIGHT = 0.40D;

    /** 默认融合：向量归一化分权重。 */
    public static final double HYBRID_VECTOR_WEIGHT = 0.60D;

    /** 含《书名》或强 BM25 信号时：BM25 权重（词法优先）。 */
    public static final double HYBRID_ENTITY_BM25_WEIGHT = 0.85D;

    /** 含《书名》或强 BM25 信号时：向量权重（语义补充）。 */
    public static final double HYBRID_ENTITY_VECTOR_WEIGHT = 0.15D;

    /** {@link com.demo.search.InMemoryBM25Searcher} BM25 k1 参数。 */
    public static final double BM25_K1 = 1.2D;

    /** {@link com.demo.search.InMemoryBM25Searcher} BM25 b 参数。 */
    public static final double BM25_B = 0.75D;

    /**
     * 中文书名号《...》识别，用于判定实体查询分支。
     * 使用方：VectorStoreService.containsBookTitleEntity。
     */
    public static final Pattern BOOK_TITLE_PATTERN = Pattern.compile("《\\s*([^》]+?)\\s*》");

    /** 仅一条 BM25 命中时，Top1 分超过此值视为「强 BM25」信号。 */
    public static final double BM25_STRONG_TOP1_ABSOLUTE = 0.8D;

    /** 多条命中时，Top1/Top2 分比值超过此值视为「强 BM25」信号。 */
    public static final double BM25_STRONG_TOP1_TOP2_RATIO = 1.30D;

    // -------------------------------------------------------------------------
    // NL2SQL：强制前缀（与 LibraryAnalyticsAnswerService 一致）
    // -------------------------------------------------------------------------

    /** NL2SQL 强制前缀（中文）。 */
    public static final String FORCE_ANALYTICS_PREFIX_CN = "/查询";

    /** NL2SQL 强制前缀（英文）。 */
    public static final String FORCE_ANALYTICS_PREFIX_EN = "/query";

    /**
     * Tool 查询仅允许访问的表名（小写集合，校验时与解析出的表名比对）。
     * 使用方：ReadOnlySqlValidator；语义上与 LibraryAnalyticsSchema 中的表说明一致。
     */
    public static final Set<String> NL2SQL_ALLOWED_TABLES = Set.of("books", "borrow_records");

    /** 单条 SELECT 允许的最大 LIMIT。 */
    public static final int NL2SQL_MAX_LIMIT = 100;

    /** 未写 LIMIT 时自动追加的默认行数上限。 */
    public static final int NL2SQL_DEFAULT_LIMIT = 50;

    // -------------------------------------------------------------------------
    // 统计分析意图（正则字符串，CASE_INSENSITIVE 在编译处指定）
    // 使用方：StructuredAnalyticsIntentDetector
    // -------------------------------------------------------------------------

    public static final String ANALYTICS_INTENT_REGEX =
            "(统计|汇总|总数|总量|有多少本|多少本|一共有多少|有多少个|多少个|一共多少|共计|合计|平均(价|价格|评分)?|均价"
                    + "|排行|排名|top\\s*\\d+|前\\s*\\d+\\s*名|占比|比例|分布"
                    + "|分类|各分类|每个分类|按分类|不同.*分类|几种|多少种|多少册|总册|册数"
                    + "|借阅\\s*(次数|量|记录|统计)?|借出|归还|未归还|在借"
                    + "|最(多|少|热门|受欢迎|畅销)"
                    + "|how\\s+many|total\\s+number|distinct|count|sum|avg|group\\s*by)";

    /** 预编译的统计意图正则（与 {@link #ANALYTICS_INTENT_REGEX} 一致）。 */
    public static final Pattern ANALYTICS_INTENT_PATTERN =
            Pattern.compile(ANALYTICS_INTENT_REGEX, Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // RAG 路径：是否检索 / 后处理（子串包含即命中）
    // 使用方：AiService.isFactIntent / isChitchatIntent / isLikelyBookIntent
    // -------------------------------------------------------------------------

    public static final List<String> RAG_FACT_INTENT_KEYWORDS = List.of(
            "价格", "多少钱", "价位", "作者", "出版", "isbn", "库存", "借阅", "借了多少", "borrow",
            "score", "评分", "分类", "在哪", "位置", "馆藏", "有几本", "数量"
    );

    public static final List<String> RAG_CHITCHAT_INTENT_KEYWORDS = List.of(
            "你好", "嗨", "hello", "hi", "你是谁", "谢谢", "感谢", "在吗", "早上好", "晚上好",
            "心情不好", "难过", "焦虑", "聊聊", "随便聊", "无聊", "最近怎么样"
    );

    public static final List<String> RAG_LIKELY_BOOK_INTENT_KEYWORDS = List.of(
            "书", "图书", "推荐", "作者", "分类", "评分", "借阅", "库存", "馆藏",
            "price", "borrow", "isbn", "title", "book"
    );

    /**
     * 模型偶发泄露的推理/用量片段起始标记；从此处截断用户可见回答。
     * 使用方：AiService.sanitizeLeakedReasoning。
     */
    public static final List<String> RAG_OUTPUT_CUT_MARKERS = List.of(
            "\"usage\":{",
            "\"prompt_tokens\"",
            "Final Output Generation",
            "Self-Correction",
            "Revised Draft",
            "Let's finalize",
            "*Final decision:*",
            "*Looks good.*"
    );

    // -------------------------------------------------------------------------
    // HTTP API 默认值（注解 defaultValue 须为编译期常量 String）
    // 使用方：AiController /api/ai/analyze 的 debug 参数
    // -------------------------------------------------------------------------

    /** GET /api/ai/analyze 的 debug 查询参数默认值（字符串形式供注解使用）。 */
    public static final String API_AI_ANALYZE_DEBUG_DEFAULT = "true";
}
