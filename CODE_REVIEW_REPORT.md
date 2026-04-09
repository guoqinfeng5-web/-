# 全项目 Code Review 报告

## 评审概览

- 变更意图: 该项目为图书管理 + AI 问答的全栈系统，后端基于 Spring Boot + MyBatis-Plus + Redis + LangChain4j，前端基于 Vue3 + Element Plus。
- 影响范围: 后端控制器、服务层、配置、实体与测试；前端路由、登录态与聊天渲染链路。
- 整体评分: **2.3 / 5**
- 评审说明: 本次按 `.cursor/rules/code-cr.mdc` 的分级与模板输出，重点覆盖安全、稳定性、性能、可维护性与测试有效性。

---

## 🔴 Critical 问题 (必须修复)

### 1) 敏感凭据硬编码并提交到仓库

- 问题类型: Critical
- 位置: `src/main/resources/application.yml:9`, `src/main/resources/application.yml:14`, `src/main/resources/application.yml:32`
- 问题描述: 数据库密码、Redis 密码、第三方模型 API Key 明文写入配置文件。
- 影响: 凭据泄漏后可直接导致数据库/缓存/模型接口被滥用，属于高风险安全事件。
- 建议:
  - 立即轮换已暴露的 MySQL、Redis、模型 API Key。
  - 改为环境变量/密钥管理服务注入，仓库仅保留占位符。
  - 在 CI 增加 secret scan（如 Gitleaks/TruffleHog）阻断再次提交。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 2) 后端接口无鉴权与权限控制，前端仅本地存储“伪登录”

- 问题类型: Critical
- 位置: `src/main/java/com/demo/controller/BookController.java:13`, `src/main/java/com/demo/controller/UserController.java:13`, `src/main/java/com/demo/controller/UserActivityController.java:13`, `src/main/java/com/demo/controller/AiController.java:11`, `frontend/src/router/index.js:29`, `frontend/src/views/Login.vue:36`
- 问题描述:
  - 后端 CRUD/AI 接口未见任何认证鉴权机制（如 Spring Security/JWT/Session）。
  - 前端通过 `localStorage` 的 `demo_logged_in=true` 判断登录态，可被任意篡改。
- 影响: 未授权用户可直接访问/修改数据；权限边界完全失效，存在严重越权与数据破坏风险。
- 建议:
  - 后端落地统一认证（JWT 或 Session）+ 鉴权（RBAC）。
  - 控制器按角色加访问控制（至少区分读写与管理权限）。
  - 前端仅做展示层路由守卫，不应承担安全控制职责。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 3) 用户密码以明文形式持久化与返回

- 问题类型: Critical
- 位置: `src/main/java/com/demo/entity/User.java:33`, `src/main/java/com/demo/controller/UserController.java:21`, `src/main/java/com/demo/controller/UserController.java:42`
- 问题描述: 用户实体包含 `password` 明文字段，创建/查询接口直接以实体入参与返回，未见加密哈希或脱敏处理。
- 影响: 一旦数据库泄漏或接口被抓包，用户口令直接暴露，造成账户接管与连带风险。
- 建议:
  - 密码入库前使用强哈希（BCrypt/Argon2），禁止可逆存储。
  - API 返回 DTO，默认排除密码字段。
  - 增加密码复杂度策略与失败重试限制。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

---

## 🟡 Warning 问题 (建议修复)

### 1) 请求参数缺少 Bean Validation 与边界校验

- 问题类型: Warning
- 位置: `src/main/java/com/demo/controller/BookController.java:24`, `src/main/java/com/demo/controller/UserController.java:21`, `src/main/java/com/demo/controller/UserActivityController.java:21`, `src/main/java/com/demo/controller/BookController.java:67`
- 问题描述: `@RequestBody` 未配合 `@Valid`，分页参数缺少最小/最大边界控制。
- 影响: 无效数据可直接入库；极端分页参数可能引发性能问题或异常行为。
- 建议:
  - DTO + `jakarta.validation` 注解（如 `@NotBlank/@Size/@Min/@Max`）。
  - 对 `pageNum/pageSize` 设上限并统一校验错误响应。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 2) AI 问答接口使用 GET 传递长文本问题

- 问题类型: Warning
- 位置: `src/main/java/com/demo/controller/AiController.java:18`, `frontend/src/views/Home.vue:181`
- 问题描述: 问题文本通过 URL Query 传输（GET），易被网关、浏览器历史、日志记录。
- 影响: 隐私文本泄漏风险上升，且受 URL 长度限制。
- 建议: 改为 `POST /api/ai/analyze`，请求体承载问题文本，并完善审计脱敏策略。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 3) AI 服务每次全量拉取图书构造上下文，扩展性不足

- 问题类型: Warning
- 位置: `src/main/java/com/demo/service/AiService.java:42`, `src/main/java/com/demo/service/AiService.java:295`
- 问题描述: 每次问答都 `bookService.list()` 拉全量，再截断拼 prompt。
- 影响: 数据量增大后 DB 与 token 成本攀升，响应时间明显变慢。
- 建议:
  - 改为检索式召回（关键词/向量检索 TopK）。
  - 增加缓存与分层上下文策略，避免每次全量拼接。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 4) Redis 会话历史无 TTL，存在持续增长风险

- 问题类型: Warning
- 位置: `src/main/java/com/demo/service/AiService.java:251`
- 问题描述: 对话写入 Redis List 后未设置过期时间，压缩后仍会长期增长。
- 影响: 长期运行下内存占用不可控，影响 Redis 稳定性。
- 建议: 对会话键设置 TTL，并配合条数上限与定期清理策略。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 5) 异常被静默吞掉，排障困难

- 问题类型: Warning
- 位置: `src/main/java/com/demo/service/AiService.java:185`, `src/main/java/com/demo/service/AiService.java:221`, `src/main/java/com/demo/service/AiService.java:253`
- 问题描述: 多处 `catch (Exception ignored)` 仅降级不记录日志。
- 影响: 线上故障难定位，真实错误被长期隐藏。
- 建议: 至少记录 warn/error 日志（含链路标识），并区分可预期异常与系统异常。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 6) XSS 风险：聊天内容通过 `v-html` 渲染

- 问题类型: Warning
- 位置: `frontend/src/views/Home.vue:277`
- 问题描述: 虽对 `<`/`>` 有基础转义，但最终仍采用 `v-html` 注入 HTML，后续规则变更或绕过时风险较高。
- 影响: 一旦渲染链被绕过，可能导致脚本注入与会话劫持。
- 建议: 使用成熟 Markdown 渲染器 + 白名单 Sanitizer（如 DOMPurify）并默认禁用危险标签/属性。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

---

## 🔵 Info 优化建议

### 1) 日志配置偏开发态，生产环境风险较高

- 问题类型: Info
- 位置: `src/main/resources/application.yml:25`, `src/main/resources/application.yml:35`, `src/main/resources/application.yml:36`
- 问题描述: MyBatis SQL stdout 与模型请求/响应日志默认开启。
- 影响: 生产环境可能产生敏感信息泄漏与日志膨胀。
- 建议: 通过 profile 区分环境，生产默认关闭请求/响应明细日志。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 2) 依赖使用 alpha 版本，稳定性需评估

- 问题类型: Info
- 位置: `pom.xml:98`
- 问题描述: `langchain4j-open-ai-spring-boot-starter` 使用 `1.0.0-alpha1`。
- 影响: API/行为变更概率较高，升级维护成本上升。
- 建议: 评估升级到稳定版本并补充兼容性测试。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

### 3) 测试有效性存在偏差与覆盖缺口

- 问题类型: Info
- 位置: `src/test/java/com/demo/service/AiServiceMemorySummaryTest.java:25`, `src/test/java/com/demo/controller/BookControllerTest.java:49`
- 问题描述:
  - `AiServiceMemorySummaryTest` 使用的历史 key 与生产代码 key 前缀不一致。
  - `BookControllerTest` 传参使用 `title`，而控制器实际读取 `keyword`。
- 影响: 测试可能出现“通过但未覆盖真实路径”的假阳性。
- 建议: 对齐测试参数与生产代码，补充鉴权、参数校验、异常链路与性能回归测试。
- 处理情况:
  - 是否处理: 否
  - 处理人: 待定
  - 时间: 待定

---

## 总结

当前项目在功能链路上可运行，但存在三类必须优先治理的安全问题：**明文凭据、缺失后端鉴权、明文密码处理**。建议先完成安全基线整改（凭据治理 + 认证授权 + 密码哈希/脱敏），再推进参数校验、AI 检索性能优化与测试修正。按上述优先级处理后，项目可维护性和上线安全性将显著提升。

