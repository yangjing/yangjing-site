---
title: 我的 SDD 实践和 sdd skill
date: 2026-08-15 10:00:00
tags:

- AI 编程
- spec-driven-development
- claude-code
- skill
- 软件工程

categories:

- 工程实践

---

过去半年，我全程用 AI 编程智能体（Claude Code / zcode）开发了两个产品：hetuos 是多业务线的 AI 应用平台，hetu-creative 是面向创作者的 AI 课程创作产品。两个仓库共用同一套技术栈：后端 Rust + PostgreSQL，契约 Protobuf + ConnectRPC，前端 React 19 + TanStack Router/Query + Ant Design 6。

把 SDD 用好后，即使不依赖 Claude / Codex，也能达到很好的开发质量和效率——最近半年我主要使用 GLM / Kimi，而现在最常用的开发工具是 zcode。

AI 写代码的效率没什么可抱怨的，真正反复出问题的是另外三件事：

1. **文档要么不写，要么复述代码**。让它补个设计文档，交上来的多半是把函数体翻译成中文散文，读了等于没读；真正该写的「为什么这样选、否决了什么」，一个字没有。
2. **仓库规则越攒越多，且无法复用**。规则散在 AGENTS.md 里，每次会话全量注入，越长越贵也越长越钝。开第二个仓库时，这些规则几乎要全部重写一遍——因为里面混着「换项目也不变的规则」和「只有这个仓库才成立的取值」。
3. **AI 的自述不是证据**。「已测试」「应该没问题」是它的口头禅，但没有可追踪、可重复的判定机制，验收就是玄学。

这些问题的答案，是两个仓库里逐步长出来的一套 SDD 规范，以及由它反向提炼成的 sdd skill。本文先讲这套规范在两个仓库里的实际运转——文档原则、验收机制、执行计划、UAT 与工程底座，再讲 sdd skill 本身的设计。

## 第一原则：文档只写代码无法表达的内容

这是整个规范集里优先级最高的一条，先于一切载体、结构与流程规则：**文档只写代码无法表达的内容**。一段内容若不能通过这一问，写得再规范也是负债——读者要读它，维护者要跟着代码改它，而它本可以由读代码直接得到。

落到操作上，就是写下每一段之前先问一句：**「删掉它，只读代码的人会失去什么？」** 答不上来，或答案是「少打几分钟字」，则删。

准入清单只收五类代码确实表达不了、缺失即知识失传的内容：

| 类别 | 承载什么 | 缺失的后果 |
| --- | --- | --- |
| 意图与规格 | 为什么需要它、必须满足什么、范围与非范围 | 后来者从实现反推需求，把缺陷当特性 |
| 对外契约 | 跨边界的接口、字段、错误码、权限码及兼容承诺 | 消费方靠猜，破坏性变更无从识别 |
| 决策理由 | 为何这样选、否决了哪些替代方案、代价是什么 | 已被否决的方案被反复重提 |
| 领域术语 | 业务词汇的精确定义与消歧 | 同一个词在两个模块指两件事 |
| 运维知识 | 部署形态、环境差异、故障信号与处置 | 代码里根本不存在这些事实 |

禁入清单则针对 AI 最爱犯的毛病：复述函数体、复述外部官方文档、复述已在别处定义的条款。第三条尤其值得展开——引用其它文档时只写「主题 → 链接」，禁止附带条款正文，因为**带正文的摘要必然滞后于真相源，摘要一旦带上正文就变成第二个（错误的）真相源**。

这条原则落到大活儿上是什么样？看 hetu-creative 的 `docs/designs/ai-usage-billing.md`。这份文档承载 AI 用量计费的「决策理由与运维知识」，开头明确声明：表结构、RLS、权限码、proto 字段的现行真相源是 `contracts/` 与代码，本文不复述。那它写什么？写这种东西：

> | provider | 缓存机制 | trial 实际计费 |
> |---|---|---|
> | DeepSeek | 官方支持 prompt cache，**但 rig 0.39 client 默认禁用 cache** | 全部按未命中价计费 |
>
> DeepSeek 的 cache 机制是「能吃但没吃」——rig client 没启用。Moonshot / Qwen 是「自动在吃」。这条差异直接影响 cost 数据解读：DeepSeek 的 cost 偏高是 client 限制，不是模型贵。

这段表格从代码里读不出来吗？严格说读得出来——翻 rig 的源码能找到那个默认值。但「推断错误的代价」太高：不写它，每个看成本报表的人都会得出「DeepSeek 比 Moonshot 贵」的错误结论。所以规范里还有一条豁免判据：对外接口、fail-closed 分支、幂等与重放语义这类**推断错误代价高的行为**，即使能推断也必须显式写明。判据不是「能否推断」，是「推错了多贵」。

## 规则必须可验证：Verification Oracle 与 Human Approval Gate

针对「AI 自述不是证据」的问题，规范强制每条关键验收都写成三列表：验收项 ↔ Verification Oracle ↔ Evidence。Verification Oracle 是能够以可追踪、可重复方式判定验收是否成立的权威机制——自动化测试、契约生成一致性、静态检查、人工审批记录。「已测试」不是 Oracle，「API 测试（requires_password_change + ChangeInitialPassword）」才是。

hetuos 身份中心规格里的真实验收表：

| 验收项 | Verification Oracle | Evidence |
|-------|--------------------|----------|
| 代建受限激活 + 首登强制改密 | API 测试（requires_password_change + ChangeInitialPassword） | 测试结果 |
| 跨上下文数据隔离 | API 测试：personal 上下文不可见租户数据 | 测试结果 |
| 实名要素脱敏存储 | 契约与实现检视（SECURITY §5 机制） | review 记录 |

验收只能由人判断时，禁止硬凑一个机械 Oracle，必须显式标记 Human Approval Gate 并写明 evidence 形态。hetuos 康养系统规格的验收表最后一行，Oracle 列写的就是 Human Approval Gate，Evidence 列写「签署 / 商定记录」——具体到知情同意这一条，还附着法务认可记录：「紧急例外（PDPA deemed consent emergency，法务 2026-07-23 认可）：救治场景 MUST 先行救治，MUST 在 24-48h 内补签或留痕」。AI 无法自证的部分被显式隔离出来，而不是混在「已完成」里。

配套的表述纪律是 BCP 14（RFC 2119/8174）：规范性语句一律用大写的 MUST / MUST NOT / SHOULD / MAY，BDD 场景的 Then 里全是 MUST：

```gherkin
Scenario: 租户解绑不影响 personal 上下文
Given 一个同时具备 personal 上下文与某租户 tenant 上下文的 User
When 该租户解除其成员关系
Then personal 上下文 MUST 保持可用
And 该 User 的全局登录身份 MUST 不受影响
```

小写的 "must" 只是叙述，评审时无法区分硬约束和作者语气——这个区分对人是便利，对 AI 是必需，因为 AI 需要知道哪些条款可以用工程判断变通，哪些变通即违规。

还有一条容易被忽视的纪律：**UAT 不等于自动化测试**。覆盖矩阵里 `AUTO` 标记的语义是「存在自动化证据文件，不表示本轮已执行或已通过」。hetuos 的 UAT 覆盖矩阵把「功能入口 ↔ Spec 章节 ↔ 人工 UAT ↔ 自动化证据」四层对齐成一张活账本，基线数字可以直接引用：四前端 52 路由、builder 域 9 service / 64 RPC、48 个 API suite、e2e 20 例。把「文档声称覆盖」变成可 grep 的账，是这套规范一直在做的事。

## 规则会逼出文档：一次真实的连锁反应

规范集里有一条强制条款：边界信任模型是架构选择而非默认值，项目 MUST 在 ADR（Architecture Decision Record，架构决策记录）中显式声明，二选一：per-service trust-root（无网关，每个服务自解凭证）或 trusted gateway（前置网关验签注入身份头）。

hetuos 实际上早就采用了模型 A，但从未为此写过 ADR——代码是这么写的而已。规范落地后，这条条款把这份隐性决策逼成了一份显性文档：ADR-0007《边界信任模型采用 per-service trust-root》。它的 Status 字段相当诚实：

> Accepted（2026-07-26。本 ADR 为**追认既有决策 + 补独立载体**：该模型自 SECURITY.md 安全原则确立起即已生效并全量落地，此前分散声明于三处非 ADR 载体。SDD §4.6 要求信任模型必须由 ADR 声明，故补此记录。不改变任何现行行为。）

补写 ADR 的过程做了一次落地事实核查，结果发现了真东西：`crates/hetu-core` 的 `EntryKind` 枚举里躺着一个前身项目遗留的 `GatewayProxied` 分支——模型 B 的语义，文档注释还写着「流量来自可信网关并 MUST 携带 trusted header」。hetuos 无网关，五个 bin 一律走别的分支，这个分支零使用。

ADR 的处置不是加个 deprecated 注释，而是直接删除，理由写在否决方案里：

> 一个无人走的分支就是一条无人看守的漂移路径——翻转一个已有枚举取值比新增枚举容易得多，且不会触发评审。故直接删除该档并在模块文档写明「刻意不设」，把重新引入的代价提高到显式改枚举定义 + match 分支。

于是规范、文档、代码在同一个 commit 里互相校准：条款要求落 ADR → 补写 ADR 时核查代码 → 发现并删除危险的死分支 → 删除动作本身又被 ADR 记录。这条链路里没有任何一步是「为了文档而文档」，每一步都在消除一个真实的漂移风险。

## 执行计划是易耗品：归档可删除

两个仓库的迭代都跑在同一套执行计划体系上，目录完全同构：

```
docs/exec-plans/
├── PLANS.md      # 活跃计划索引 / 状态板
├── TODOS.md      # 未规划但后续需要做的事项
├── active/       # 进行中的计划
├── deferred/     # 暂缓的计划
└── archived/     # 已完结、已回流的计划
```

`PLANS.md` 对自己的定位很克制：状态板，非真相源——业务规则在 `docs/specs/`，设计在 `docs/designs/`，计划明细在 `active/<plan>.md` 自己身上，它只保留活跃计划索引和当前状态，六列：计划、状态、负责人、目标版本、验收标准、下一步。「目标版本」有个反直觉的注解：它是功能集归属，MUST NOT 解读为交付先后，也不绑定仓库版本号或 tag。hetuos 的 `PLANS.md` 里还有一节「全量 UAT 触发点」，把「下一次全量回归什么时候跑」写成显式约定：下个行为变更批次完结时；内部重构批次按 v0.7 先例不需全量回归。

`active/` 里的计划是稳定形态，不是随手备忘：文首字段表（Status / Owner / 目标版本 / 依赖），正文按「目标 → 范围（In / Out）→ 关键设计 → 任务分解 → 验收标准 → 完结前置」组织，hetuos 的验收表同样带 Verification Oracle 列。hetu-creative 的阶段表则把完成证据就地回填在验收格子里——「（✅ 2026-08-15：backend 141 + task-worker 16 全绿，含 DB 集成测试）」——计划和证据不分离，完成情况没有第二处可查。它的计划还多一节「实现决策记录」，规则就写在标题后面：只记代码不能说明的。比如 t044 里这条：「`ZipWriter` 适配形态：本地临时文件中转，不做 channel Seek 适配」——zip 库要求 `Write + Seek`（local header 要回写），流式 sink 实现不了回写；这个取舍代码里读得出结果、读不出理由。

`deferred/` 针对计划最容易的失控形态：等一个外部回复，整条线悬着。规则明文：默认 MUST NOT 因外部回复未到而阻塞开发，能配置 / 参数化承载的先承载，剩下的暂停并上报 owner。每份暂缓计划文首 MUST 记 Status（含暂停日期）、暂停理由、恢复条件，恢复条件未成立前 MUST NOT 重启；措辞还必须区分「启动前置」（缺它无法开工）与「上线门」（不阻塞开发 / 联调 / E2E，但上线前必须补齐）——hetuos 暂缓的合规特性计划，恢复条件逐项挂到送审稿的八个待法务确认问题（L-1 到 L-8）上，不是一句「等法务」。

设计上最反直觉的一条是：**归档不是永久保管**。归档项满足三个条件后 SHOULD 删除——已真实完整实现、结论已回流现行规范（specs / designs / SDD）、无现行文档依赖。决策过程一律归 git 历史。

理由是回流纪律的单向性：回流时把事实结论**抄写**进现行 SSOT，现行文档禁止反向链接归档计划作为出处——归档文件注定被删，SSOT 挂上去等于预埋 broken link。而「先回流、再删除」的顺序保证了删掉的是纯过程记录，不是知识。

这套机制实际跑起来的强度超出我的预期。hetuos 近一个月里批量清理了两轮已完结计划共三十余份，删除的 commit message 逐份记录回流去向。hetu-creative 更彻底：`archived/` 目录里现在一个计划文件都没有，只剩那份写着清理规则的 README，删除留痕按日期记了六批——最后三批的留痕细到这种程度：

> 2026-08-13 删除 T-039：5 阶段全部实现，稳定行为规则回流新建 `designs/build-artifact-incremental-reuse.md`（判据 = 全输入指纹 / stale 只比 hash 字段 / 按层精确失效），同步清理代码注释 ~75 处 `T-039 §` 标签。
>
> 2026-08-14 删除 T-040：阶段 0-5 全部实现（Rust 523 + 前端 107 全绿），proto 契约形态与五条嵌套语义以 contracts 注释 + 代码注释自包含，判无需回流 design，同步清理 ~90 处 `t040 §N` 注释标签，顺手修复归档漏网断链 ~13 处。

「判无需回流 design，对齐 T-033『代码为真相源』先例」这句话值得停一下：回流的目标不一定是文档，**代码注释若是该知识的最佳载体，回流目的地就是代码**。知识回到离事实最近的地方，而不是堆进一个中央文档库。

技术债的处置口径同样是为「防止攒成 backlog」设计的，两个仓库的 Agent 规则文件里都是同一段：

1. **能立即修** → 直接修，禁止登记到任何地方——登记本身就是把该修的事推后；
2. **修不了 / 不该现在修** → `TODOS.md`，必须写明解除或触发条件；无条件的条目不收，那是变相 backlog；
3. **难度过大或涉及取舍** → 输出原因和建议交人决策，禁止自行取舍。

登记同样有格式纪律。hetu-creative 的 TODOS 用全局流水号（与计划文件同一序列，t044 对应 T-044），每条分节：现状描述加三个固定字段——**解除条件**（MUST 可验证：外部版本号、部署状态、商务准入，不接受「以后再说」）、**来源**（指向把它逼出来的那条计划）、**落地方案**。最硬的一条是：**条目解除后 MUST 移除，不是标完成**——「本文件不是 backlog，是『外部条件未到』的等待区」。hetuos 的 TODOS 是表格式：依赖版本约束表（依赖 / 当前 / 最新 / 阻塞方 / 解除条件），比如 opendal 卡在 0.57 这一格——0.58.0 被上游 yanked、0.58.1 未发布，连带 quick-xml 的两条 RUSTSEC 通告只能在 `deny.toml` 里临时 ignore，实际风险评估（只解析受信云服务经 TLS 的 XML 响应）和解除条件（上游发布非 yanked 版本后升级并移除 ignore）整段留在表里。条目解除时则留一段带日期的「出表注记」——谁触发、交付了什么、按登记规则第几条出表。清零是常态：一个只进不出、永不出清的清单，很快就没有人会读。

例外是安全面：基线已定、服务端强制点未接线的安全缺口单独走 `SECURITY.md` §9，禁止混进 TODOS——混放会让安全缺口淹没在普通技术债里。hetu-creative 的 TODOS 里因此出现了一些很有意思的条目：与竞品仓库逐能力对比后，把**双方都未实现**的能力登记为带触发条件的储备项，而不是立即排期。

## UAT：把人工验收也做成流程

自动化测试再全，也有一层只有人能验：界面顺不顺手、文案对不对、真实数据下流程走不走得通。这一层在两个仓库里不是「到时候点一点」，而是 `docs/uat/` 下一套有编排、有门禁、有留痕的流程：`manual-acceptance-runbook.md` 把一轮验收拆成 S0 到 S8 的固定章节，每个业务域一份场景文档（操作 → 预期），`coverage-matrix.md` 就是前文那张把功能入口 ↔ Spec 章节 ↔ 人工 UAT ↔ 自动化证据对齐的活账本，`reports/` 放当轮报告，过期报告按规则清理。

进场先过 S0。hetuos 的环境准备交给 `scripts/uat.sh`：探活、做新鲜度守卫（环境在跑但已过期就自动重建）、打印入口 URL 和测试账号——脚本头注释里有一句准确的自我声明：它自己不跑任何测试，跑测试的是拿着场景文档走浏览器的人。然后自动化门禁先绿，runbook 原文就是「自动化门禁先绿（人工验收不替代自动化）」：cargo、`make -C contracts check`、`pnpm check:commit`、API 测试、e2e、`scan-secrets.sh` 逐项过——门禁挂了先修门禁，MUST NOT 进 UAT。

报告纪律是一组通用规约：只记失败 / 用例失效先修后重跑 / 阻塞即终止 / 不修复不归因。通过项不逐条记，按章节记「通过 N/N」；被测代码 MUST NOT 现场修，失败原因 MUST NOT 现场分析——验收和分析是两个角色，混在一起两边都做不好。复验 MUST 就地更新该轮原报告，MUST NOT 为复验新建文件；规格和手册也 MUST NOT 链接具体报告，关键发现回流 TODOS 或相关规格。报告和执行计划是同一个生命周期哲学：过程性文件，用完即走。实测列回填的是真实观测值——hetuos v0.8 全量轮报告里那一格是「✅ 12 期间，2026-01~2026-12，2 月 28 天」（「期间」即月度会计期间）。

什么时候跑全量 UAT 不靠记性，靠 `PLANS.md` 里的「全量 UAT 触发点」显式约定。hetu-creative 的编排比 hetuos 多出两章，把场景间的时序依赖做成了设计：S9 是综合业务流「毕业考试」，用一个新建的空白课程贯穿全流程（从新建到出成片），前置是 S2 到 S5 全部通过，再加上 S7 真实档；S10 数据导出压轴，runbook 里的理由原文——「导出包是全部业务域的聚合快照……越晚执行覆盖面越完整；S9 产生的构建制品正好作为 S5 域的对账素材」。

## contracts/：一个目录，四个真相源

「契约先于实现」要能落地，契约得先有个固定的家。两个仓库都把契约性资产集中进 `contracts/`，它本身还是 pnpm workspace 的成员——fixtures 与种子定义以 workspace 包被测试直接 import，零构建中间层。

hetuos 的 `contracts/` 按职责分子目录：`protos/`（60 个 proto、11,823 行，按「系统 / 领域 / v1」分目录）、`schemas/`（纯 DDL，按库一文件）、`seed-definitions/`（种子的 TS 真相源）、`entities/`（内置实体声明）、`fixtures/`（测试数据）。目录里那份 143 行的 AGENTS.md 用一张数据流图把生成方向写死：

```
seed-definitions/*.ts ──(生成脚本)──→ seeds/*.sql
entities/**.json ──(EntityDefinition 编译器)──→ schemas/*.sql 的 GENERATED 段
fixtures/*.ts ─────────────────────→ tests/ import
```

真相源到生成物单向流动，「改哪边」不靠自觉靠 gate：`make check` 聚合六个检查脚本，`check-seed-codegen.sh` 的做法是「先备份 → 重新生成 → 与备份比对 → 不一致则恢复备份并报错，工作树保持干净」；`check-entity-codegen.sh` 的头注释把动机说透：「分叉在建库那一刻是静默的——表建出来了，只是不是声明的那张。本 gate 把『记得重新生成』从人工纪律变成 CI 判定」。

贯通 Rust 与 TypeScript 两端的生成链只有一个中间产物：`buf build` 出的 `descriptor-set.bin`（约 788 KB）。全仓十余个 bin / crate 的 `build.rs` 直接引用它做 ConnectRPC 代码生成，TS 侧的 protoc-gen-es 也从同一份 descriptor 生成——proto 改完，两端从同一份二进制重新长出来，没有哪端能「忘了」。这也是 `contracts/AGENTS.md` 里那条顺序陷阱的来源：proto 变更后 MUST 重建所有消费它的 bin，只重建单个，跨 bin 的 wire 类型错配要到运行时才爆。

目录级规则细到 RLS 策略命名：只允许 `scope_select` / `scope_insert` / `scope_update` / `scope_delete` / `scope_isolation` 五个名字，理由是「跨表 grep `CREATE POLICY scope_*` 一次定位全仓 RLS 策略」。规则本身单点维护在 `contracts/AGENTS.md`，一个目录只留一份规则载体——两处并存必然漂移。

hetu-creative 的 `contracts/` 是同一范式的裁剪：只剩 `protos/` 和 `sql/`。`sql/` 用数字前缀编排生命周期：`00-roles.sql` 建非超级用户角色，`01-schema.sql` 全量 DDL，`02-seed.sql` 开发种子，03 起是上线后的增量（AI 用量审计表、模型价格、存量回填），Docker initdb 按文件名序自动装载——文件名即装载顺序。迁移历史也有交代：一份 `MIGRATION-DIFF.md` 给从 TypeSpec 迁到 proto 的全部语义调整记账（2,668 行 TypeSpec 对 17 个 proto 文件、99 个 rpc；teacher → creator 全栈改名、SSE 流改同步 unary，逐条入账）。对照之下，hetuos 的 `migrations/` 目录刻意为空，README 写明首版上线前结构变更直接改 `schemas/`、「schema 变更无迁移路径，只能重建库」——空目录是登记过的刻意状态，不是忘了建。

## 测试栈：打真实栈，不打桩

API 测试的定位写在 `tests/AGENTS.md` 第一行：「Vitest + ConnectRPC TS client，跑真实 bin / 真实 DB，验证后端遵守 proto 契约」。没有 mock 层：测试用种子账号走真实登录换会话凭证，明文禁止在 TS 端自构 token；连 LLM 都不打桩，builder 域以 `HETU_BUILDER_LLM_MODE=replay` 回放录好的应答，确定、可重复、不花钱。globalSetup 把「起一套被测栈」整段自动化：幂等拉 e2e 数据库容器 → 全新 schema + 种子 → `cargo build` 七个 bin → 依次拉起到 e2e 端口段 → 逐个轮询 `/health` → 跑 suite → teardown。

隔离靠端口分段和容器分段双保险：hetuos 的 dev 占 59081-59087 / DB 55432，e2e 占 39081-39087 / DB 55435，各自独立容器独立卷，reset 脚本互相「绝不碰对方容器」。这些数字不是拍脑袋，是事故喂出来的——hetu-creative 的 e2e 隔离注释里第一条就是真实事故：dev 的 task worker 进程会把 e2e 入队的任务认领走，两边抢活，测试永远等不到自己的任务。hetuos 的 e2e 端口段还留了覆写口，理由写在注释里：39081-39085 落在 Linux 内核临时端口区间（`ip_local_port_range` 通常 32768-60999），任何本机进程的出站连接都可能先占住其中一个，bind 随即 EADDRINUSE——实证是本机一条 PostgreSQL 连接把 39084 当源端口占了。

执行强制串行：`fileParallelism: false`，配置里给的理由是「单一后端 + 共享 PostgreSQL 实例——文件级并行会导致跨 suite 互相干扰」。

为 AI 协作做的适配藏在 `run-tests.sh`：全量跑「30+ suite / 500+ 用例」时管道截断会丢失败列表，脚本把全量输出落盘 `tests/.test-logs/`，结束只打摘要和 FAIL 清单；排查纪律是 MUST 用 grep / tail 读日志文件，「避免一次性点满 LLM 上下文」。测试基础设施开始把「AI 读日志」当一等消费场景来设计——传统工程里不存在这个需求，AI 协作编程里天天发生。

还有一个模式把测试、部署和 UAT 串了起来：external 模式。同一套 suite 加 `--env staging` 直接打已部署的 staging 栈，起栈的 globalSetup 整段跳过，只切连接目标。hetu-creative 的 staging UAT 报告里「api-tests --env staging 17 passed」跑的就是平时那套测试——验收报告里的自动化证据和日常回归，不再是两套东西。external 模式的边界也被登记：有 suite 的数据准备直连 e2e 库而断言打 staging，两库错位必挂，于是它在 external 模式下显式 skip，口径写进文档而不是留给执行者踩坑。

hetu-creative 起步时只有 4 个 suite，但做了一个 hetuos 甘愿付固定开销也没做的优化：把不打 RPC 的纯逻辑测试拆进独立的 `vitest.config.env.ts`——无 globalSetup、允许并行——不为一次「起容器 + 三个 bin」的固定开销买单；hetuos 对同类纯逻辑 suite 的态度记在 AGENTS.md 里：照付一次 globalSetup 代价。

## 部署与脚本：运维知识的归宿

第一原则的准入清单里有一类「运维知识：部署形态、环境差异、故障信号与处置——代码里根本不存在这些事实」，`deploy/` 就是这一行的落地。hetu-creative 的 `deploy/README.md` 开头定位与准入清单严丝合缝：「本目录是部署形态的真相源：Dockerfile / docker-compose / systemd unit / nginx.conf 即现行事实，本文只承载代码无法表达的环境差异、装配口径、降级行为与故障信号」。

最有代表性的是那张原生运行时依赖矩阵。task-worker 的构建管线（PPTX → PDF → PNG → 视频合成）依赖 libpdfium、libreoffice、ffmpeg，每种登记三件事：版本锁定口径（libpdfium MUST 用 chromium/7543，与 `pdfium-render` 0.8 的绑定版本锁定，防 ABI 漂移）、装配方式、**缺它时的降级行为**——「PDF→PNG 跳过 → build job 不产 slide PNG / cover，但 PPTX/MP3/PDF 产物仍正常」。把「缺 X 时系统什么行为」写成矩阵，故障处置从考古变成查表，配套还有一节「信号 → 根因 → 处置」。

hetuos 的裸机部署则示范了部署本身也被 gate 管着。两阶段纪律：先以普通用户构建，再以 root 安装——sudo 的 secure_path 会剥掉 cargo / pnpm，root 构建还会污染 `target/`。配置模板的占位符分两种形态：`__MARKER__` 给渲染器自动替换，`CHANGE_ME` 留给人工填写，形态写错没人提醒，于是 `preflight.sh` 把部署后残留的两种占位符一律判 FAIL。这道 fail-closed 门禁的职责写在头注释里：缺二进制、缺配置、缺用户、缺证书时，部署停下来，而不是带起半个栈。preflight / verify / verify-tls 三道门禁全过，一次部署才算完。

secrets 单独立规：真实凭证整目录 gitignore、权限 0600，「凭证一旦疑似暴露 MUST 走 provider 控制台轮换，代码 / 权限修复不能替代轮换」；`scan-secrets.sh` 作为发版门禁扫五类泄漏，输出刻意收敛——只报 file:line，永不回显命中的密钥内容。

最后是 `scripts/`。这个目录表面是工具箱，实际是文档体系的一部分：几乎每个脚本的头注释都在写「代码无法表达的内容」。reset-db 类脚本的注释里写着「不能只等 pg_isready，要等 users 表出现（02-seed 装载完成的标志），最长 60s」——postmaster 起来不等于 schema 和种子装完；docker-compose 文件的注释里解释了为什么 MUST 有显式 project name：「同名 project 会让 compose 把对方的容器当作本 project 的旧容器替换掉（已实际发生过：误删 careos 的 hetu-dev-db）」。事故教训写进头注释，下一个人——或下一个 AI——动脚本之前会先读到它。hetu-creative 的每个脚本还注明与 hetuos 的对齐关系：「对齐 hylx-careos scripts/reset-db.sh 范式，本仓单库 hetu_creative 故精简掉多库 / identity 断言 / seed manifest」——工程范式的跨仓复用不靠整目录复制，靠范式加显式登记的裁剪理由。

`scripts/` 里另有三个仓库级 gate，和后文的机械检查器是同一哲学在代码侧的延伸：`check-crate-layering.sh` 用 `cargo tree` 做依赖白名单判定（平台层 crate 的源码禁出现业务域概念、builder-core 禁依赖任何数据库 driver、bin 之间禁互依）；`check-leave-domain-redlines.sh` 守护护理业务的两条红线，其中「时限零硬编码」的扫描范围有个精致取舍——时间字面量只扫 `#[cfg(test)]` 之前的代码，理由原文：「一个『证明时限来自机构声明』的单测必须能写出那份声明里的秒数并断言算出的时点——否则这条纪律就只能靠『不写测试』来遵守，那是把 gate 变成反向激励」。

## 从 docs/sdd/ 到 sdd skill

这套规范最初长在 hetuos 的 `docs/sdd/` 目录里，是项目内规范，天然带着前面第二个问题的隐患——规则锁在单个仓库里就无法复用，开第二个仓库时几乎要重写一遍。近期，我把它反向提炼成了一个项目中立的 skill——`sdd`，安装在 `.agents/skills/sdd/`。hetu-creative 这个新仓库从建仓第一天起就带着这个 skill 起步。两个仓库至今共用同一份 skill 本体，`diff -rq` 零差异，没有 fork，项目差异全部收敛在各自的 overlay 里。sdd skill 的安装方式见 [skills 仓库](https://github.com/yangjing/skills/#-sdd)。

## sdd skill 长什么样

目录结构：

```
.agents/skills/sdd/
├── SKILL.md              # 只做触发路由 + 执行协议，不复述任何条款
├── references/           # 9 册通用规范，约 2800 行
│   ├── SPECIFICATION.md        # 总纲：内容准入、契约先行、术语、兼容性、门禁
│   ├── spec-driven-development.md  # Sprint 物料：契约包、生成链、迭代 checklist
│   ├── design-philosophy.md    # 深模块、设计两次、YAGNI、注释准入
│   ├── naming-conventions.md   # 三层命名、权限码、禁用词
│   ├── backend-layering.md     # api/application/domain/infra 分层
│   ├── frontend-conventions.md # route 职责、远程数据、金额日期渲染
│   ├── i18n-conventions.md     # 命名空间、文案真相源、fallback
│   ├── service-dependency-contract.md  # SoR、通信协议、信任模型
│   └── sdd-overview.md         # 分册总览、overlay 边界、自审方法
├── stacks/               # 技术栈适配层
│   ├── protobuf-connectrpc.md
│   ├── rust-postgres.md
│   └── react-tanstack-antd.md
├── templates/            # overlay / Feature Spec / 栈适配层骨架
└── scripts/
    └── check-spec-conformance.py  # 机械检查器，零第三方依赖
```

关键设计是 `SKILL.md` 只有 133 行，**只做两件事：触发路由和执行协议，不复述任何条款**。AI 命中场景时按路由表只加载对应分册的对应章节，明文禁止预读全部分册——这是 skill 的渐进披露（progressive disclosure）机制在规范加载上的直接应用：2800 行规范如果每次全量注入，既贵又钝。

触发路由表长这样（节选）：

| 触发场景 | 加载 |
| --- | --- |
| 写或评审任何文档 | SPECIFICATION §1.0 内容准入——第一项，不过即删 |
| 设计或变更 Contract Surface（API / 事件 / Schema / 权限码 / 错误码） | SPECIFICATION §4 + §7 |
| 一个域该有多少 RPC / 能否合并 / 粒度下限 | SPECIFICATION §7.5 |
| 评审模块设计 / 判定是否重构 / PR 判断代码质量 | design-philosophy |
| 是否加抽象 / 新依赖 / 重写、MVP 范围裁剪 | design-philosophy §14 |
| handler 里写 SQL、字段类型该落哪层 | backend-layering |
| 前端 route / Provider / 远程数据 / 金额渲染 | frontend-conventions |

值得注意的是触发词的粒度。路由表覆盖的不只是「写规格文档」这种显式场景，还包括「这字段该叫什么」「这模块该不该拆」「这段要不要写进文档」——AI 嘴上没提 SDD，只要动作命中场景，规范就得加载。否则规范只在一个仪式性的「写文档时刻」被想起，其余时间形同虚设。

执行协议固定六段：Trigger（何时必须加载）、Load（先读什么、禁止读什么）、Apply（冲突时以谁为准）、Conflict/Stop（什么情况必须停下来报告、禁止自行取舍）、Output（交付说明必须点名依据的章节号和证据形态）、MUST NOT（禁止的捷径）。每份分册的开头也有自己的六段协议。这个格式不是给 reviewer 看的排版偏好，而是让规则对 AI 可执行的最小骨架——少了 Conflict/Stop 段，AI 遇到规范与代码冲突时会自己拍板；少了 Output 段，「我已按规范执行」就变成无法核验的自述。

## 三层内容模型：规则、形态、取值分开

skill 解决「第二个仓库怎么办」的办法，是把所有内容按**变化源**切成三层：

| 层 | 收什么 | 变化源 |
| --- | --- | --- |
| `references/` | 换栈、换项目都不变的规则 | 方法论演进 |
| `stacks/` | 换项目不变、换栈就变的落地形态（类型映射、框架 API、生成链） | 技术选型 |
| 项目 overlay | 换项目一定变的取值（路径、包名、词表、命令、迁移策略） | 项目决策 |

判断归属就靠两个问句，先问栈、再问项目：换掉技术栈还成立吗？成立 → `references/`；不成立，再问：这条规则在另一个用同样技术栈的项目里还成立吗？成立 → `stacks/`，不成立 → 项目 overlay。

典型误判是把「所有 id 主键 MUST 是 UUID 或 BIGINT」放进 `stacks/`——它换栈依然成立，属于 `references/`。反过来的误判更常见：把仓库路径、make target、专属权限码写进通用规范，下一个项目就整册报废。

每个仓库用一个 `sdd.overlay.md` 登记自己的取值，只登记通用规范禁写的那些内容。hetuos 的 overlay 里是这样的表：

| 类别 | 本仓路径 |
| --- | --- |
| 契约根目录（proto + schema/seeds + fixtures） | `contracts/` |
| 规格根目录 | `docs/specs/` |
| 后端进程单元 ↔ 共享库 | `bins/` ↔ `crates/` |
| 契约全量检查 | `make -C contracts check` |
| 兼容性阶段 | 未发布 1.0，内部契约字段编号 MAY 放宽 |

而 hetu-creative 的 overlay 里，同一张表登记的是另一个世界：规格根目录一栏写的是「未设立（本仓当前无功能/系统规格文档）」，边界信任模型一栏写的是「未裁决（TODO：触发该强制条款时，先落 ADR 再实施）」。同一份 skill，两个仓库各自如实登记自己的现状——包括「还没有」这件事本身。

配套还有一条命名纪律：扩展某份通用分册的项目文档必须叫 `<对应文件名>.overlay.md`（如 `naming-conventions.overlay.md`），没有对应通用分册的项目文档禁止加 `.overlay` 后缀。这样「通用规范 ↔ 项目扩展」的配对关系统一由文件名承担，人和 AI 都不用猜。

## 机械检查器：把规则变成 gate

规范写得再好，靠 AI 自觉遵守总归是软约束。skill 自带一个 562 行的检查器 `check-spec-conformance.py`，脚本本身零第三方 Python 依赖，以 PEP 723 自包含形态交付，检出五类机械可判定的违规。运行时需要 [uv](https://docs.astral.sh/uv/) 来管理 Python 环境与依赖——规范检查是门禁，门禁不该因为装不上包而失效，而 uv 让这件事足够轻量：

| 检查项 | 违规形态 |
| --- | --- |
| C1 | `path:line` 行号锚点——行号随重构漂移，会把审计指向错误代码 |
| C2 | Agent 执行协议缺段——六段骨架不完整 |
| C3 | BCP 14 关键词小写——"must" 只是叙述不是规范 |
| C4 | `.overlay.md` 命名违规 |
| C5 | 文档头缺 Status / Version 控制字段 |

用法：

```bash
# 扫 skill 自身
uv run .agents/skills/sdd/scripts/check-spec-conformance.py

# 扫仓库规格文档
SDD_SCAN_ROOTS='docs/designs,docs/specs' \
  uv run .agents/skills/sdd/scripts/check-spec-conformance.py
```

在 hetuos，这道检查被串进了提交前总门禁，与 lint、type-check 平级：

```
check:commit = format → lint → type-check → 领域红线 → check:docs
                                                    ├─ 链接与锚点校验
                                                    ├─ 行号锚点 C1（扩到 Agent 规则文档链）
                                                    └─ 规范符合性 C1-C5
```

C1 值得单独说：它约束的是引用形态而非文档体裁，所以扫描面特意扩到根 `AGENTS.md`、`apps/*/AGENTS.md` 这条 Agent 规则文档链——这些文件由 harness 按目录注入、AI 直接照其执行，行号漂移的代价与规格文档等同。

扫描面治理则体现了另一层判断力：执行计划、运营跟踪、UAT 执行记录、对外文稿一律**不纳入**扫描面。理由写在脚本 docstring 里：英文对外邮件里的 "must" 是普通英语不是 BCP 14，执行计划也不承担文档控制字段义务，把它们纳进来只会产出满屏假阳性——**而会误报的 gate 最终会被绕过**。宁可少管，不可错管；一个被团队绕过的门禁比没有门禁更糟，因为它还提供着虚假的安全感。

## 同一个 skill，两种形状

回到最初的问题：第二个仓库怎么办。答案是 hetu-creative 没有复制 hetuos 的文档体系，而是用同一份 skill 长出了完全不同的形状：

| 维度 | hetuos（多业务线平台仓） | hetu-creative（单产品仓） |
| --- | --- | --- |
| specs 根目录 | `docs/specs/`，5 条业务线的功能规格 + BDD | 未设立 |
| ADR | 13 篇 | 无目录，overlay 登记「未裁决，触发时先补建」 |
| 权限码真相源 | `designs/naming-conventions.overlay.md` 文档 | `contracts/sql/02-seed.sql` 的 INSERT 行 |
| 提案体裁 | 2 份讨论草稿 | 8 篇结构化产品提案 + 竞品调研 |
| designs 文档 | 23 份 | 3 份（都是「代码读不出的为什么」） |
| contracts 组织 | 八个职责子目录（protos / schemas / seeds / entities / fixtures…） | `protos/` + 数字前缀 `sql/` |
| 测试栈 | 48 个 API suite + e2e 两档 20 例 | 4 个 suite 起步 + external 模式直打 staging |
| 部署主形态 | 裸机 systemd，七个 bin 多库，docker 仅冒烟 | docker-compose（dev）+ systemd（生产）+ 降级矩阵 |
| skill 本体 | `.agents/skills/sdd/` | 完全相同，diff -rq 零差异 |

差异不是随意长出来的，每个选择都有判据。hetu-creative 是单一产品，产品范围由 `DESIGN.md` 和 proto 契约承载，再立一套 specs 树就是制造第二个真相源；权限码总共二十几个，SQL seed 里的 INSERT 行天然就是权威数据，再写一份文档清单反而要维护两处同步。规范里「每个系统 MUST 用独立子目录放规格」这类条款对它是空转，它就诚实地在 overlay 里登记「未设立」，而不是为了合规硬造一批空壳规格。

反过来，hetu-creative 把精力花在了 hetuos 没有的体裁上：`docs/proposals/` 里的产品提案。最有代表性的是那份全景分析——先用三路代码盘点（16 proto / 21 service / 95 rpc 全量核对实现状态、前后端逐页核对、UAT 记录核对），再叠加三路联网调研（19 家 AI 课程竞品、20+ 家数字人/TTS 竞品），得出第一条判断：

> 最大缺口在「完成度」而非「新想法」：南北向 77 个 rpc 前端仅接线 25 个（约 32%）——构建管线（发起/进度/重试/取消/制品审阅下载）前端零入口，而后端 build DAG 在首 stage 后断链。「构建」是核心卖点，当前用户却无处可点、链路也无产出。

「77 个 rpc 接线 25 个」这种数字能写进提案，前提是规格与契约体系让「完成度」变成可清点的事实。这也是这套规范的一个副产品：它不光约束怎么写代码，还让仓库的当前状态始终是可测量的。

## 小结

两个仓库，一份 skill，零 fork。hetuos 四个月 832 个 commit、664 份文档（其中 224 个 commit 以 docs 开头，文档与代码同节奏演进）；hetu-creative 五个月 1656 个 commit，跑完了「迁移 → 两轮全量 UAT → 提案立项 → 逐计划闭环归档」的完整循环，`archived/` 清空了 334 份已回流计划。

回头看，这套东西真正立住的是三条原则，它们对人、对 AI 同样成立：

1. **契约先于实现**——先写实现再补契约，契约就退化为实现的描述，失去约束力；
2. **文档只写代码无法表达的内容**——过不了「删掉它，只读代码的人会失去什么」这一问的内容，写得再规范也是负债；
3. **规则必须可验证**——每条验收有 Verification Oracle（可追踪、可重复的权威判定机制），只能由人判断的，显式标记为 Human Approval Gate（人工审批门），机械可判定的交给脚本，会误报的 gate 宁可不设。

而把它做成 skill 而不是一份长文档的意义在于：规范被拆成了「路由 → 按需加载的条款 → 栈适配 → 项目取值」四层，AI 只在命中场景时读它需要的那几节，新仓库用一个 overlay 就能继承全部方法论沉淀。规范不再是那份每次会话全量注入、越来越没人读的 AGENTS.md，而是可以被触发、被执行、被检查的活规则。
