# mybranch.diff Code Review 报告

## 评审概览

- 变更意图: 本次变更主要是将 `AiService` 从“直接拼接全量馆藏+内嵌记忆”重构为“向量检索 + 记忆服务分层”，并同步升级 LangChain4j 依赖、修复相关单测。
- 影响范围: `pom.xml`、`src/main/java/com/demo/service/AiService.java`、`src/main/java/com/demo/service/LlmSummaryService.java`、`src/test/java/com/demo/service/*`，以及规则文档与报告文档。
- 整体评分: **3.6 / 5**
- 评审说明: 按 `.cursor/rules/code-cr.mdc` 进行分级评审，重点关注正确性、可维护性、稳定性与测试有效性。

---

## 🔴 Critical 问题 (必须修复)

本次 diff 中未发现明确的 Critical 级问题。

---

## 🟡 Warning 问题 (建议修复)

### 1) 会话记忆改为异步写入，可能导致同会话“上一轮对话丢失”现象

- 问题类型: Warning
- 位置: `src/main/java/com/demo/service/AiService.java:118`
- 问题描述: `chatMemoryService.saveTurnSafelyAsync(...)` 采用异步写入，当前请求返回后下一次请求可能先于写入完成。
- 影响: 连续追问场景下，模型读取到的历史可能缺少最近一轮，导致上下文不稳定或回答跳变。
- 建议:
  - 对“强依赖上下文”的会话改为同步写入，或
  - 引入队列/批量刷写并提供可观测性（延迟、失败重试），确保可预期一致性。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 2) 过滤推理泄露文本的截断规则可能误伤正常回答

- 问题类型: Warning
- 位置: `src/main/java/com/demo/service/AiService.java:430-445`
- 问题描述: `sanitizeLeakedReasoning` 使用固定关键字截断（如 `Revised Draft`、`Let's finalize`）。若用户正常提问中包含这些词，合法内容可能被提前截断。
- 影响: 返回内容不完整，表现为“答案突然中断”或缺少尾段信息。
- 建议:
  - 增加更严格的判定条件（如与 `usage`/JSON 结构联合命中再截断）。
  - 对截断行为增加可观测日志（仅记录命中规则，不记录敏感正文）。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 3) 依赖跨版本耦合风险增加（Spring Boot 3.2.5 + LangChain4j beta）

- 问题类型: Warning
- 位置: `pom.xml:95-104`
- 问题描述: 将 LangChain4j 升级到 `1.0.0-beta5` 并新增 DashScope 社区模块，属于 beta 版本组合，兼容性与后续升级风险高于稳定版本。
- 影响: 后续可能出现 API 变动、自动配置兼容问题，增加维护和回归测试成本。
- 建议:
  - 固化依赖矩阵（Boot/LangChain4j/模型适配器）并在 CI 中增加集成测试。
  - 评估使用 BOM 锁定同版本族，减少跨模块版本漂移。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

---

## 🔵 Info 优化建议

### 1) 规则文档新增参考链接为空，知识链路不可追踪

- 问题类型: Info
- 位置: `.cursor/rules/code-cr.mdc:327-328`
- 问题描述: 新增 `[单元测试规范]()` 为空链接。
- 影响: 评审规则无法跳转到参考规范，降低规则可用性。
- 建议: 改为有效相对路径或文档地址，避免空链接。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 2) 删除历史评审报告文件，可能丢失审计上下文

- 问题类型: Info
- 位置: `CODE_REVIEW_REPORT.md`（删除）
- 问题描述: 直接删除既有评审报告。
- 影响: 历史整改跟踪与审计链条中断，不利于回归验证。
- 建议: 若需替换，建议改为归档（如 `reports/archive/`）而非删除。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 3) 测试通过依赖 `sleep` 等待异步写入，长期可维护性一般

- 问题类型: Info
- 位置: `src/test/java/com/demo/service/AiServiceMemorySummaryTest.java:693-730`, `src/test/java/com/demo/service/AiServiceSummaryLiveKeyTest.java:803-834`
- 问题描述: 测试采用固定 sleep + 轮询等待异步结果，时间参数对环境敏感。
- 影响: 在慢机或 CI 负载高时可能出现偶发性波动。
- 建议: 使用可控同步点（例如 awaitility 或显式完成信号）替代固定 sleep。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

---

## 总结

本次改造方向正确：完成了 `AiService` 的职责下沉与模块化，技术路线从“全量上下文拼接”转向“检索增强+记忆服务”，并补充了对应测试。当前主要风险集中在“异步记忆一致性”和“文本清洗误截断”两个行为层问题，建议优先治理这两项，再推进依赖矩阵稳定化与测试等待机制优化。

