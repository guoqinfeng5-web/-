# 全项目 Code Review 报告

> 评审依据：[`.cursor/rules/code-cr.mdc`](.cursor/rules/code-cr.mdc)  
> 评审范围：后端 `src/main/java`、配置 `application.yml`、前端 `frontend/src` 主要链路（未逐行审阅全部历史代码）  
> 报告日期：2026-04-12

---

## 评审概览

| 项 | 说明 |
|----|------|
| **变更意图 / 项目定位** | 图书管理 + AI 问答 Demo：Spring Boot 3 + MyBatis-Plus + Redis + LangChain4j；RAG（Hybrid BM25+向量）+ NL2SQL 统计；Vue3 前端对话与书目列表。 |
| **影响范围** | 配置与密钥、REST API、AI 编排（`AiService` / `VectorStoreService` / 统计助手）、Redis 会话、前端渲染与路由。 |
| **整体评分** | **2.4 / 5**（功能链路完整，但安全基线与生产化差距大，适合作为课程/Demo，不建议未整改直接公网暴露。） |

---

## 🔴 Critical 问题（必须修复）

### 1) 敏感凭据明文写入仓库配置

- **问题类型**: Critical  
- **位置**: [`src/main/resources/application.yml`](src/main/resources/application.yml)（约 L8–L15：MySQL/Redis；约 L29–L44：ModelScope、DashScope API Key）  
- **问题描述**: 数据库密码、Redis 密码、第三方模型与 Embedding 的 API Key 以明文出现在可提交的配置文件中。  
- **影响**: 仓库泄漏或备份外泄即可导致数据库、缓存与模型接口被滥用；合规与审计风险高。  
- **建议**: 立即轮换已暴露密钥；改为环境变量或密钥管理服务；仓库内仅保留占位符与示例；CI 增加 secret 扫描（如 Gitleaks）。  
- **处理情况**: 否 / 待定 / 待定  

### 2) 后端 API 无认证鉴权

- **问题类型**: Critical  
- **位置**: [`BookController`](src/main/java/com/demo/controller/BookController.java)、[`UserController`](src/main/java/com/demo/controller/UserController.java)、[`BorrowRecordController`](src/main/java/com/demo/controller/BorrowRecordController.java)、[`AiController`](src/main/java/com/demo/controller/AiController.java) 等均未使用 Spring Security / JWT 等。  
- **问题描述**: 任意可达客户端可对书目、用户、借阅记录及 AI 接口进行读写调用。  
- **影响**: 数据被篡改、批量爬取、滥用大模型配额；与「登录页」无真实安全关联。  
- **建议**: 引入统一认证（如 Spring Security + JWT 或 Session）；按角色授权；AI 与写操作接口至少需登录态。  
- **处理情况**: 否 / 待定 / 待定  

### 3) 用户密码明文存储与接口回传

- **问题类型**: Critical  
- **位置**: [`User.java`](src/main/java/com/demo/entity/User.java) L32–L34；[`UserController`](src/main/java/com/demo/controller/UserController.java) `create` / `update` / `getById` 直接使用实体。  
- **问题描述**: `password` 字段以明文形式参与持久化与 API 往返，未见哈希与返回 DTO 脱敏。  
- **影响**: 数据库或抓包泄漏即口令暴露；账户可被接管。  
- **建议**: 入库前 BCrypt/Argon2；返回使用 DTO 排除密码；登录接口单独设计。  
- **处理情况**: 否 / 待定 / 待定  

---

## 🟡 Warning 问题（建议修复）

### 1) AI 分析问题通过 GET + Query 传长文本

- **问题类型**: Warning  
- **位置**: [`AiController.java`](src/main/java/com/demo/controller/AiController.java) `GET /api/ai/analyze`；[`Home.vue`](frontend/src/views/Home.vue) `axios.get('/api/ai/analyze', { params: { question: q } })`（约 L181–L183）  
- **问题描述**: 问题全文出现在 URL 查询串，易被代理、访问日志、浏览器历史记录。  
- **影响**: 隐私与合规风险；URL 长度限制可能导致失败。  
- **建议**: 改为 `POST`，请求体承载 `question`；服务端日志对问题字段脱敏。  
- **处理情况**: 否 / 待定 / 待定  

### 2) 写接口缺少 Bean Validation 与分页边界

- **问题类型**: Warning  
- **位置**: 各 Controller `@RequestBody` 未配合 `@Valid`；`pageNum`/`pageSize` 无上限（如 [`BookController.page`](src/main/java/com/demo/controller/BookController.java) L66–L77）。  
- **影响**: 异常或恶意超大 `pageSize` 带来内存与 DB 压力；无效数据入库。  
- **建议**: DTO + `jakarta.validation`；分页 `pageSize` 设最大值（如 100）。  
- **处理情况**: 否 / 待定 / 待定  

### 3) Redis 会话历史无 TTL

- **问题类型**: Warning  
- **位置**: [`ChatMemoryService`](src/main/java/com/demo/service/ChatMemoryService.java) 对 List 的 `rightPush` / `leftPush` 未设置 key 过期。  
- **影响**: 长期运行 Redis 内存持续增长。  
- **建议**: 对 `ai:chat:history:v2:*` 设置合理 TTL，或定期清理 + 条数硬顶。  
- **处理情况**: 否 / 待定 / 待定  

### 4) 前端「登录态」仅依赖 localStorage 标志位

- **问题类型**: Warning  
- **位置**: [`frontend/src/router/index.js`](frontend/src/router/index.js) L28–L33（`demo_logged_in`）  
- **问题描述**: 无服务端 Session/JWT 校验，路由守卫可被本地篡改绕过（与后端无鉴权叠加）。  
- **影响**: 误导性的「已登录」体验；真实安全依赖后端，但后端当前开放。  
- **建议**: 与后端真实认证对齐；敏感操作以服务端校验为准。  
- **处理情况**: 否 / 待定 / 待定  

### 5) 聊天内容 `v-html` + 自研 Markdown 子集

- **问题类型**: Warning  
- **位置**: [`Home.vue`](frontend/src/views/Home.vue) `renderMarkdown`（约 L110–L131）、模板 `v-html`（约 L277）  
- **问题描述**: 已对 `<>&` 做转义并支持有限标签，但仍非成熟 Markdown + HTML 消毒管线；模型输出格式变化或边界情况可能引入风险。  
- **影响**: XSS 面仍存在理论风险（取决于模型输出与解析顺序）。  
- **建议**: 使用成熟渲染库 + DOMPurify 白名单；或禁止 `v-html`，仅文本安全渲染。  
- **处理情况**: 否 / 待定 / 待定  

### 6) 开发态日志与 SQL 打印默认开启

- **问题类型**: Warning  
- **位置**: [`application.yml`](src/main/resources/application.yml) `mybatis-plus.configuration.log-impl: StdOutImpl`；`langchain4j.open-ai.chat-model.log-requests: true`  
- **影响**: 生产环境日志膨胀与敏感片段泄漏风险。  
- **建议**: 按 `spring.profiles` 区分；生产默认关闭明细 SQL 与请求日志。  
- **处理情况**: 否 / 待定 / 待定  

### 7) NL2SQL「我的借阅」固定 `user_id = 1`

- **问题类型**: Warning（业务/隐私口径）  
- **位置**: [`LibraryAnalyticsAnswerService`](src/main/java/com/demo/service/LibraryAnalyticsAnswerService.java) 中 `SELF_BORROW_RULE_PROMPT` 与规则说明  
- **问题描述**: Demo 将「我」硬编码为 `user_id=1`，与真实多用户场景不符。  
- **影响**: 误用生产时数据串用户或泄露他人借阅语义空间（在鉴权缺失下更严重）。  
- **建议**: 从认证上下文取当前用户 ID；无登录则明确禁止「我的」类查询或返回说明。  
- **处理情况**: 否 / 待定 / 待定  

---

## 🔵 Info 优化建议

### 1) 架构与可维护性

- **常量集中**: [`AppConstants`](src/main/java/com/demo/common/AppConstants.java) 已聚合 Hybrid、NL2SQL 白名单、RAG 关键词等，有利于统一调参与文档化（保持与 README 同步更佳）。  
- **分层清晰**: `LibraryAnalyticsAnswerService` 与 RAG 路径分离、`ReadOnlySqlValidator` + `SafeSqlQueryExecutor` 组成 NL2SQL 安全边界，设计合理。  

### 2) 测试

- **覆盖**: 已具备 `ReadOnlySqlValidatorTest`、`StructuredAnalyticsIntentDetectorTest`、`InMemoryBM25SearcherTest`、`LibraryAnalyticsAnswerServiceTest`、`SafeSqlQueryExecutorTest`、`AiControllerTest` 等。  
- **建议**: 补充 `AiService` 集成测试（Mock ChatModel/VectorStore）；关键 Controller 在加鉴权后补充安全相关用例。  

### 3) 性能与扩展

- **向量与倒排**: 全内存 `VectorStoreService` + `InMemoryBM25Searcher`，规模增大时需外置向量库与全文引擎（README 已提及）。  
- **启动 ingest**: `@PostConstruct` 全量向量化，大数据量时考虑异步或增量。  

### 4) API 设计

- REST 资源划分基本清楚；统计强制前缀 `/查询` 与意图检测并存，建议在文档中明确优先级与失败回退行为（便于运维排障）。  

---

## 总结

当前项目在 **AI 编排（Hybrid RAG + NL2SQL + Redis 记忆）** 上结构清晰，**SQL 只读校验与表白名单**降低了裸执行 SQL 的风险，但 **配置中的明文密钥、全接口无鉴权、用户口令明文** 属于上线前必须治理的 **Critical** 项。建议优先完成 **凭据外置与轮换、认证授权、密码哈希与 DTO 脱敏**，再迭代 **POST 化 AI 接口、分页与校验、Redis TTL、生产日志策略**。

---

## 附录：评审维度自检（摘要）

| 维度 | 结论摘要 |
|------|-----------|
| 代码质量 | 主路径可读；部分魔法逻辑已收拢至 `AppConstants`。 |
| 安全性 | 密钥、鉴权、密码、XSS/GET 传参存在明显缺口。 |
| 可维护性 | 模块边界较好；依赖外部模型与配置较多。 |
| 架构 | 分层与 AI 子模块（intent/sql/analytics）合理。 |
| 数据库 | MyBatis-Plus 常规用法；需注意分页与生产 SQL 日志。 |
| 测试 | 核心工具与部分服务有单测；端到端与 `AiService` 覆盖可加强。 |
| 性能 | Demo 级内存检索可接受；扩展需规划。 |
