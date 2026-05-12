## 项目简介（面试展示版）

这是一个在 **两天内借助 AI 工具**完成的“AI 辅助图书馆分析系统” Demo：既支持基于馆藏语料的 **RAG 问答**（**内存倒排 BM25 + 向量** 混合检索后注入上下文，解释/推荐/学习路径），也支持基于数据库的 **NL2SQL 统计分析**（馆藏概况、分类分布、库存紧张度、借阅分析等），并实现了可控的对话短期记忆与只读 SQL 安全执行。

---

## 核心能力

- **RAG 馆藏问答**：把馆藏书目转为可检索文本 → **向量化 + 内存倒排（BM25，非 Elasticsearch）** → **`hybridSearch` 混合检索 TopK** → 拼接到 Prompt 作为上下文，降低“编造馆内不存在书籍/数据”的风险  
- **NL2SQL 数据分析**：用户用 `/查询` 强制走统计链路（或命中统计意图）→ 由 Tool 执行只读 SQL → 基于真实返回数据生成 Markdown 答复  
- **短期记忆与摘要压缩**：Redis 记录对话历史，超过阈值后自动摘要压缩，保证上下文连贯且不会无限增长  
- **SQL 安全约束**：仅允许 `books`、`borrow_records`；单条 `SELECT`；必须 `LIMIT` 且 ≤100；禁止多语句与危险关键字  

---

## 模块划分

### 前端

- `frontend/src/views/Home.vue`：书籍概览（全量/热门/高分）+ AI 对话输入输出（调用 `/api/ai/analyze`）

### 后端

- **AI 入口**：`src/main/java/com/demo/controller/AiController.java`（`GET /api/ai/analyze`）
- **编排与意图分流**：`src/main/java/com/demo/service/AiService.java`
- **RAG（向量化 / BM25 倒排 / Hybrid 检索）**：`src/main/java/com/demo/service/VectorStoreService.java`
- **BM25 + 倒排实现**：`src/main/java/com/demo/search/InMemoryBM25Searcher.java`
- **检索抽象（可替换 Lucene/ES 等）**：`src/main/java/com/demo/search/DocumentSearcher.java`
- **短期记忆**：`src/main/java/com/demo/service/ChatMemoryService.java`
- **NL2SQL 编排**：`src/main/java/com/demo/service/LibraryAnalyticsAnswerService.java`
- **Tool（只读查库）**：`src/main/java/com/demo/ai/tools/LibraryAnalyticsTools.java`
- **SQL 校验/执行**：
  - `src/main/java/com/demo/ai/sql/ReadOnlySqlValidator.java`
  - `src/main/java/com/demo/ai/sql/SafeSqlQueryExecutor.java`

---

## 整体架构与请求链路

```mermaid
flowchart TD
User[User] --> Frontend[Frontend(Home.vue)]
Frontend -->|GET /api/ai/analyze?question=...| AiController[AiController]
AiController --> AiService[AiService]

AiService -->|/查询 或 统计意图| AnalyticsService[LibraryAnalyticsAnswerService]
AnalyticsService --> AnalyticsAssistant[LibraryAnalyticsAssistant(AiServices)]
AnalyticsAssistant --> Tool[queryLibraryDatabase Tool]
Tool --> SqlValidator[ReadOnlySqlValidator]
SqlValidator --> Jdbc[JdbcTemplate(MySQL)]
Jdbc --> Tool --> AnalyticsAssistant --> AnalyticsService --> AiService --> Frontend

AiService -->|否则走 RAG/闲聊| Vss[VectorStoreService.hybridSearch]
Vss --> BM25[BM25倒排 InMemoryBM25Searcher]
Vss --> Embed[Embedding text-embedding-v4]
BM25 --> Fusion[Top1归一化加权融合]
Embed --> Fusion
Fusion --> Vss
Vss --> AiService --> ChatModel[ChatModel Qwen3.5-35B-A3B]
ChatModel --> AiService --> Frontend
```

---

## AI 如何辅助分析（重点）

### 1) RAG 原理：从馆藏中“检索证据”再回答

RAG 的目标是让模型在回答图书相关问题时，尽量基于“馆藏可验证信息”作答。该项目的 RAG 流程是：

1. **向量化与倒排入库（ingest）**：启动时从 `books` 读取数据，拼接为每本书的 `featureText`；调用 Embedding 生成向量并保存 `TextSegment + Embedding`；**同步对全部 `featureText` 执行 `bm25Searcher.rebuild`，重建内存倒排索引**  
2. **混合检索（hybridSearch）**：并行走 **BM25（倒排召回）** 与 **向量余弦召回**，对候选文档的分数按各路 Top1 做归一化后加权融合，再取 TopK 片段作为上下文  
3. **上下文注入（prompt）**：将 TopK 片段拼接进 `AiService` 的 `[RAG 馆藏背景]`，再调用 ChatModel 生成回答  
4. **阈值控制**：对 **Hybrid 融合后的最终 `score`** 与 `AppConstants.MIN_VECTOR_SCORE` 比较；不足则弱化/不使用强馆藏上下文，避免把弱相关片段硬塞进回答

对应实现：

- 入库：`VectorStoreService.ingest(List<Book>)`（内含向量写入 + `rebuild` 倒排）
- **主路径检索**：`VectorStoreService.hybridSearch(String question, int k, boolean debug)`（`AiService` 默认调用）
- **保留接口**：`VectorStoreService.search(...)` 仍为纯向量 TopK，便于对比或内部使用，线上问答主链路以 `hybridSearch` 为准
- 阈值：`AppConstants.MIN_VECTOR_SCORE`（在 `AiService.hasStrongContext()` 中对融合分使用）

### 2) 向量嵌入：featureText 包含哪些字段

向量入库时，每本书会被序列化为一段结构化文本（便于模型“看懂”字段），例如包含：

- `title / author / category / price`
- `score / stock / tags / borrowCount`
- `summary`

这意味着：不带 `/查询` 的普通问答，也有机会从 RAG 上下文直接取到“评分/库存/借阅次数”等事实字段（前提是检索命中且字段存在）。

### 3) 检索方式：BM25（倒排）+ 向量余弦 + Hybrid 融合

检索由 `VectorStoreService.hybridSearch` 完成，两路召回再融合（默认各取最多 50 条候选，最终 TopK 默认 5，与 `AiService` 一致）：

**BM25 路（`InMemoryBM25Searcher`）**

- Jieba 分词（`SEARCH` 模式）、停用词与短词过滤后，查 **内存倒排索引** `token -> (docId -> tf)`，按 BM25 打分排序取候选。

**向量路**

- 对用户问题做 Embedding，与每条 `StoredSegment.embedding` 计算 **余弦相似度**，取候选。

**融合与 TopK**

- 将两路分数分别用 **各自 Top1** 归一化到约 `[0,1]` 区间后加权求和得到 **最终分**：
  - **默认**：`0.4 × BM25_norm + 0.6 × vector_norm`
  - **实体优先**（问题含《书名》书名号，或 BM25 强信号）：`0.85 × BM25_norm + 0.15 × vector_norm`，关键词命中更稳、语义作补充
- 按最终分降序排序，截取 TopK，格式化为 `[RAG 馆藏背景]`；`debug=true` 时会在日志中输出各路分数便于排查。

**与阈值的关系**：`AiService.hasStrongContext()` 用融合后的 `hit.score()` 与 `MIN_VECTOR_SCORE` 比较，判断是否采用「强馆藏上下文」策略。

### 4) 短期记忆：Redis 历史 + 摘要压缩

为了让对话连续但不膨胀，该项目使用 Redis 保存对话历史，并在超过阈值后压缩：

- **历史存储**：Redis List，key 为 `ai:chat:history:v2:<sessionId>`  
- **压缩策略**：当历史条数超过 `MAX_HISTORY_MESSAGES`，将“前半段”历史摘要为一条 `SUMMARY:` system 消息，并 trim 掉已摘要的部分  

对应实现：

- `ChatMemoryService.loadAndCompressHistory(sessionId)`
- `ChatMemoryService.buildMemoryContext(history)`
- `LlmSummaryService.generateSummary(messages)`

### 5) NL2SQL：面向数据分析的“可追溯回答”

当用户想要统计/聚合/排行/分布等分析时，RAG 容易出现“口径不稳/片段不全”。因此项目提供 NL2SQL：

- **触发方式**：
  - 用户以 `/查询` 或 `/query` 开头（强制走 NL2SQL）
  - 或命中统计分析意图（正则/关键词检测）
- **执行方式**：
  1. `LibraryAnalyticsAnswerService.tryAnswer()` 调用 `LibraryAnalyticsAssistant`（LangChain4j AiServices + Tool）
  2. 模型生成 SQL，通过 Tool `queryLibraryDatabase(sql)` 执行
  3. Tool 返回 JSON 数组，模型基于真实数据生成 Markdown 答复
- **安全保证**：
  - `ReadOnlySqlValidator`：只允许单条 `SELECT`、白名单表、`LIMIT` 上限、禁止多语句等
  - `SafeSqlQueryExecutor`：JdbcTemplate 执行并序列化为 JSON

---

## 快速测试用例

### RAG（不加 `/查询`，偏解释/推荐/路径）

- `《深入理解Java虚拟机》适合什么阶段读？它的评分和库存如何？`
- `我想系统学 Java，请给我一条入门到进阶的阅读路径（结合馆藏现有书目）。`
- `推荐 3 本高分且库存充足的计算机类书，并说明适合人群。`

### NL2SQL（加 `/查询`，偏统计/聚合/排行）

- `/查询 评分最高的书是哪本？返回 title、score、stock，按 score DESC LIMIT 1`
- `/查询 按分类汇总：每个分类多少种书（COUNT(*)）与总库存多少册（SUM(stock)），按种数降序 TOP10`
- `/查询 库存最紧张的 TOP5 图书（stock 最小），返回 title、stock、category`
- `/查询 我目前有哪些书还没归还？（默认 user_id=1，列 title、borrow_time、已借天数）`
- `/查询 我借阅过哪些书？按 borrow_time 倒序列出最近 20 条（含 return_time）`

---

## 本地运行（简洁）

### 环境要求

- JDK 17+（项目 `pom.xml` 声明 `java.version=17`）
- Maven 3.8+
- Node.js 18+（建议）
- MySQL 8+
- Redis

### 后端启动

```bash
mvn spring-boot:run
```

默认端口：`8080`（见 `src/main/resources/application.yml`）。

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端通过 Vite 代理调用后端接口（详见 `frontend/vite.config.js`）。

### 关键配置

后端配置在 `src/main/resources/application.yml`，包括：

- `spring.datasource.*`：MySQL
- `spring.data.redis.*`：Redis
- `langchain4j.open-ai.chat-model.*`：聊天模型（示例配置为 `Qwen/Qwen3.5-35B-A3B`）
- `dashscope.embedding.*`：Embedding 模型（示例配置为 `text-embedding-v4`，用于 RAG）

注意：提交到公开仓库时，建议将 API Key、数据库密码等敏感信息迁移到环境变量或本地配置文件，避免泄露。

---

## 可扩展方向

- **向量与 BM25 均在进程内内存维护**（`VectorStoreService.memoryStore` + `InMemoryBM25Searcher` 倒排）：实现简单、便于 Demo；缺点是重启需重新 `ingest` 以重建向量与倒排。后续可将 **向量侧** 换为 Milvus、pgvector 等；将 **全文/BM25 侧** 换为 Lucene、Elasticsearch、OpenSearch 等以支撑更大规模文档（当前 **未使用 ES**）。
- **意图识别基于关键词/正则**：可替换为更强的意图分类模型，或引入统一的 Router（例如先分类再选择 RAG/NL2SQL/闲聊）。
- **实时信息类问题（如天气）**：目前不接第三方数据源，可扩展接入天气 API 或定位信息。

