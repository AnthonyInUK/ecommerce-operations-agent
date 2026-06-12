# 数据分析 Agent 开发路线图

> **项目定位：** 电商平台运营数据分析 Agent —— 以 Text2Code（代码即行动）取代 NL2SQL，
> 面向阿里/京东/拼多多体量的电商平台内部运营团队，解决准确性、多步推理、会话记忆、主动监控四大核心瓶颈。
>
> **对标竞品：** 京东 JoyAgent DataAgent（Text2SQL 路线）、阿里瓴羊智能小Q（NL2SQL + 语义层）
> **核心差异：** Text2Code 执行路径，天然支持多步归因、跨表联算、业务规则编码，NL2SQL 做不到
>
> **开发周期：** 8 周 / 56 天（天天做）
> **框架基座：** AssistantAgent（阿里开源，Spring AI Alibaba 0.2.6，Code-as-Action + GraalVM）
> **最终交付要求：** 不只是本地 Demo，最终要能部署到一台 Linux 服务器上稳定运行
> **参考：** [PAIN_POINTS.md](./PAIN_POINTS.md) | [DEVLOG.md](./DEVLOG.md)
>
> **执行纪律：** 边开发边读懂。每一天都必须同时留下三类证据：功能推进、源码理解、面试可讲点。
> 每完成一个关键功能、踩到一个关键坑、做出一个重要取舍，都必须当天写进 `DEVLOG.md`。
> 记录不能只写“做了什么”，还要写清楚：业务为什么需要它、框架原来怎么支持它、我为什么这样接、框架边界在哪里。
>
> **时间策略：** 默认完整版按 8 周推进；如果求职节奏更紧，可以切到 **压缩执行版（6 周 / 42 天）**，通过合并相邻任务、减少缓冲日、把“读源码 + 做验证 + 写记录”放到同一天完成。

## 当前进度（更新到 Day 39/40）

> **当前判断：** 主线已推进到压缩执行版 **Day 39/40 左右**。  
> 这里不是“所有计划项逐字完成”，而是指：
> - Week A / Week B 主链已完成
> - Week C 核心会话能力已完成
> - Week D 的 `verified cases / FastIntent / runtime evaluation / bad case snapshot` 已完成
> - Week 4 的 Olist 数据支线已从“只有设计”推进到“真实全量导入 + raw/dwd/dim/ads 全链构建 + 三条快路径与 root cause 部分迁移”
> - Week E 主动链已从“定时/异常触发”推进到“异常巡检 -> root cause -> 责任分发 -> 通知草稿”
> - Week F 已补第一版分析轨迹展示、README 架构说明、生产配置和单机 Docker 部署骨架

### 当前实际完成状态

- `GMV / 区域 / 品类 / 订单结构` 已支持优先走 Olist 分析层
- `gmv_root_cause` 已进入**混合数据迁移期**
  - `RegionPerformanceQueryTool` → `olist_public_dataset`
  - `OrderQueryTool` → `olist_public_dataset`
  - `CategoryRankTool` → `olist_public_dataset`
  - `UserMetricTool / FunnelAnalysisTool / RefundAnalysisTool` 暂留 `demo_seed`
- Olist 正式数据已完成第一轮全量导入
  - `raw_olist_*` 全表有数据
  - `dwd_olist_* / dim_olist_* / ads_olist_*` 全链构建成功
- 数据质量门槛已补齐
  - raw 导入 summary
  - 关键关联完整性检查
  - analytics stageCounts / relationshipGaps / coverage
  - `prefer-olist-analytics=true` 下 root cause 数据来源专门校验
- Day 36 分析轨迹展示已补第一版
  - 页面展示 `trigger / fast / deep` 路径标签
  - 展示耗时、数据来源、Tool 调用摘要
  - trigger 结果已接回页面，能直接展示 root cause / 责任分发 / 通知草稿
- Day 38 README + 架构图已补第一版
  - 已说明为什么做、怎么跑、核心链路、与普通问数差异
  - 已说明 Olist + demo 混合数据口径
- Day 39/40 生产配置与单机部署骨架已补第一版
  - `application-prod.yml`
  - `deploy/.env.example`
  - `deploy/Dockerfile`
  - `deploy/docker-compose.yml`
  - `deploy/start-prod.sh / stop-prod.sh / logs.sh`
- 端到端 demo smoke 已补第一版
  - `scripts/smoke_ecommerce_demo.py`
  - 校验 `/runtime`、手动 root cause、GMV 异常巡检 trigger
  - 检查结构化 root cause、责任分发、通知草稿、证据可信度和数据来源

### 当前之后最值得继续做的事

1. 录制 2-3 条核心 Demo，优先录“主动异常巡检”和“root cause 多步归因”
2. 补部署实测：用 `docker compose up` 在本机或 Linux 服务器完整跑一遍，并运行 `scripts/smoke_ecommerce_demo.py`
3. 补更强降级 / 缓存 / 持久化幂等，不再继续盲目扩业务支线
4. 整理简历版、1 分钟版、3 分钟版项目讲解

### 下一阶段业务化升级：贴近中大厂电商运营分析

> **目标：** 把项目从“能回答一次 GMV 为什么跌了”继续推进到“像一个内部运营分析工作台”。  
> 中大厂真实场景里，数据分析不是只给一个结论，而是要发现异常、判断可信度、分发责任、跟踪处理、沉淀反馈。

#### 1. 异常中心：从单条 GMV 异常变成经营异常列表

**业务上是什么：**  
平台运营每天不会只看一个问题，而是同时面对很多异常：GMV 掉了、订单量掉了、客单价异常、退款率升高、转化率下滑、某个品类突然掉量、某个区域表现异常。真实工作里，运营需要先看到“今天有哪些异常”，再决定优先处理哪个。

**为什么需要：**  
如果系统只能回答“华东 GMV 为什么跌了”，它还是一个单点分析工具；如果能形成异常列表，就更像一个运营工作台。运营可以按严重程度、影响金额、责任角色和处理状态来排优先级。

**项目里怎么落地：**
- 新增异常列表数据结构：`metric_id / metric_name / region / category / stat_date / severity / status / owner_role / confidence`
- 支持异常类型：`GMV`、`订单量`、`客单价`、`退款率`、`转化率`、`品类 GMV`、`区域 GMV`
- 支持状态流转：`待确认`、`已派发`、`处理中`、`已关闭`
- 页面上把当前单条异常升级成“异常中心”，点击某条异常后再进入 root cause 和责任分发

#### 2. 归因置信度：从“中/高可信”变成证据评分

**业务上是什么：**  
不是所有异常都值得发群。比如 GMV 掉了 3%，可能只是自然波动；掉了 30%，并且订单量、品类、区域都同步异常，才更像真正问题。业务人员需要知道：这个判断到底有多可靠。

**为什么需要：**  
中大厂不会让系统看到一个波动就报警。报警太多会造成“告警疲劳”，大家最后都不看。归因置信度的作用是告诉用户：这次异常是“建议通知”、“先复核再通知”，还是“只记录观察”。

**项目里怎么落地：**
- 把当前 `confidence=medium/high` 扩成分数：`0-100`
- 评分维度包括：
  - 数据来源：Olist 公开数据 / demo 补齐口径 / 真实业务表
  - 异常幅度：下降比例和影响金额
  - 持续天数：单日波动还是连续多日异常
  - 多基线一致性：环比、上周同日、近 7 日均值、去年同期是否都异常
  - 维度贡献度：是否能定位到区域、品类、商品、商家
  - 下钻对象：是否有明确商品、商家、渠道、订单批次
- 输出 `confidence_score`、`confidence_level`、`notify_recommendation`
- 页面展示“为什么是中可信/高可信”，而不是只显示一个标签

#### 3. 责任人 / 组织映射：从角色名变成可派发对象

**业务上是什么：**  
现在项目里写的是“平台运营”“类目运营”“售后治理”，但真实公司里需要知道发给谁：哪个区域负责人、哪个品类负责人、哪个商家运营、哪个售后治理群。

**为什么需要：**  
责任分发不是写一句“请类目运营排查”就结束。真正要推动处理，必须能把问题发给正确的人或群。如果找错人，异常就会在群里被踢来踢去，处理效率很低。

**项目里怎么落地：**
- 新增组织映射配置：
  - `region -> platform_operation_owner`
  - `category -> category_operation_owner`
  - `seller_id -> merchant_operation_owner`
  - `refund_category -> after_sales_owner`
  - `channel -> growth_operation_owner`
- 每条责任分发不只返回 `owner_name`，还返回：
  - `owner_role`
  - `owner_group`
  - `feishu_webhook`
  - `mention_name` 或模拟负责人
- 飞书通知从“发到一个总群”升级为“按责任对象选择通知目标”

#### 4. 处理闭环：从发送通知变成跟踪问题有没有被处理

**业务上是什么：**  
真实运营里，发出异常通知只是开始。后面还要知道：谁接了、有没有处理、最终原因是什么、是不是误报、采取了什么动作、问题有没有恢复。

**为什么需要：**  
如果没有处理闭环，系统只是“报警器”；有了处理闭环，系统才像“运营协同系统”。它还能沉淀经验：哪些异常经常误报，哪些归因判断经常正确，哪些责任人处理最及时。

**项目里怎么落地：**
- 为异常增加处理记录：
  - `confirmed_by`
  - `assigned_to`
  - `status`
  - `final_reason`
  - `action_taken`
  - `is_false_positive`
  - `closed_at`
- 页面支持状态流转：`待确认 -> 已派发 -> 处理中 -> 已关闭`
- 人工回填最终原因后，写回 bad case / verified case / experience
- 后续可以做“误报复盘”和“归因准确率统计”

#### 5. 可配置规则中心：让不同业务线有不同异常标准

**业务上是什么：**  
中大厂不会所有指标都用同一个阈值。大促期间 GMV 波动大，普通日期波动小；家电客单价高，食品订单量高；一线区域和长尾区域的波动容忍度也不同。

**为什么需要：**  
固定阈值容易误报或漏报。比如 `GMV 下跌 15%` 对大促后一天可能正常，对普通工作日可能很严重。规则中心的价值是把业务经验配置进系统。

**项目里怎么落地：**
- 新增规则配置：
  - 指标：GMV / 订单量 / 客单价 / 退款率 / 转化率
  - 维度：区域 / 品类 / 商家 / 渠道
  - 时间：普通日 / 周末 / 节假日 / 大促期
  - 阈值：环比、上周同日、近 7 日均值、去年同期
  - 通知策略：仅记录 / 待复核 / 建议通知 / 自动升级
- 页面新增“规则中心”配置视图
- trigger 从硬编码阈值升级为读取规则配置

#### 6. 更真实的数据域：补齐商品、商家、营销、履约、售后

**业务上是什么：**  
GMV 下跌只是结果。真实原因可能来自很多业务域：商品缺货、商家没参加活动、广告投放停了、物流延迟、退款变多、客服处理慢、价格竞争力下降。

**为什么需要：**  
现在项目已经能拆区域、订单、品类、用户、漏斗、退款，但距离真实电商运营还缺几个关键域。补这些域后，root cause 才能回答更接近真实业务的问题：是流量问题、供给问题、价格问题、履约问题，还是售后问题。

**项目里怎么落地：**
- 商品域：库存、价格带、上下架、重点商品
- 商家域：商家 GMV、商家服务分、发货表现、商家活动参与
- 营销域：广告 ROI、投放预算、渠道流量、活动曝光
- 履约域：发货时效、物流延迟、签收异常
- 售后域：退款原因、退货原因、客服响应、纠纷率
- 新增对应 Tool：
  - `InventoryAnalysisTool`
  - `AdRoiAnalysisTool`
  - `SellerPerformanceTool`
  - `FulfillmentAnalysisTool`
  - `AfterSalesReasonTool`

#### 推荐优先级

1. 先做 `异常中心`，把“单条分析”变成“运营工作台入口”
2. 再做 `归因置信度`，解决“这个异常到底值不值得通知”
3. 再做 `责任人 / 组织映射`，让责任分发真的能派到对象
4. 再做 `处理闭环`，让项目从“发通知”升级成“跟踪问题处理”
5. 最后逐步扩 `规则中心` 和 `更真实的数据域`

这条顺序更适合当前项目：先把运营流程闭起来，再扩更多业务域。

---

## 竞品格局与项目定位

### 三家大厂现在怎么做数据分析

| 公司 | 数据基础设施 | AI 分析层 | 现存痛点 |
|------|------------|---------|---------|
| **阿里巴巴** | MaxCompute → DataWorks → Hologres → Quick BI | 瓴羊智能小Q（问数/解读/报告/搭建 4 个 Agent，NL2SQL + 语义层） | 业务侧和数据侧协作链路慢；跨部门指标定义不统一；语义层维护成本高 |
| **京东** | Hive 数仓 → Tableau 可视化 → 京东商智（对商家） | **JoyAgent 3.0 DataAgent（2025年9月刚开源）**，Text2SQL + 诊断分析，GAIA 榜准确率 67% | Text2SQL 天花板：多步归因做不了；无会话记忆；无主动监控触发 |
| **拼多多** | 内部 BI 看板 + 多多情报通/多多雷达（对商家） | 基本无公开 AI 分析产品，内部依赖算法团队 | 信息封闭；人效压力大；重复性分析任务自动化程度低 |

### 我们的差异化定位

**直接对标：京东 JoyAgent DataAgent**

京东 DataAgent 做了自然语言 → 数据库查询这件事，也是目前开源里做得最认真的。
但它的路径是 **Text2SQL**，我们的路径是 **Text2Code**，两条路技术差距很大：

```
JoyAgent DataAgent（京东）
  用户问题 → LLM 生成 SQL → 查数据库 → 返回结果
  ✗ 无法多步推理（SQL 是单次查询）
  ✗ 无会话记忆（每次独立）
  ✗ 无法表达业务规则（SQL 不能写 if/else 业务逻辑）
  ✗ 无主动监控（被动问答）

我们的项目（Text2Code）
  用户问题 → LLM 生成 Python 代码 → GraalVM 沙箱执行
  ✓ 多步推理：代码里可以 for loop / 条件分支 / 多次查询
  ✓ 会话记忆：Session 上下文跨轮持久化
  ✓ 业务规则：代码可以编码"大促期间退款率 +5% 属正常"这类逻辑
  ✓ 主动监控：Trigger 模块 + 飞书推送，异常自动触发归因
```

面试时可以直接说：
> "我做的和京东 JoyAgent DataAgent 类似，但技术路径不同。
> JD 是 Text2SQL，我是 Text2Code，核心差别在于多步推理和主动监控这两个点。
> JD 的开源项目我研究过，它在 GAIA 榜上准确率 67%，但那是单步问答的评估，
> 真正的电商归因分析需要 4-5 步才能完成，SQL 做不了这个。"

### 这些发现对项目设计的直接影响

这次竞品调研不是补背景材料，而是直接改变项目边界：

1. **不做通用 BI Copilot，聚焦电商内部运营分析。**
   京东已经把通用型 DataAgent 做到很强，继续做"人人都能问 SQL"会正面撞车。
   我们的优势应该放在电商运营特有的归因链路、业务规则和主动触发上。

2. **不把 SQL 生成当主路径，把代码执行当主路径。**
   SQL 很适合"查一个数"，但不适合"发现异常后自动继续拆区域、拆品类、拆退款原因，再组织成结论"。
   所以后面的 Tool 设计、Experience 模板、Trigger 机制都要围绕"多步代码分析"来组织。

3. **演示场景优先级要按业务频率排，不按技术炫技排。**
   第一优先级是运营每天都问的大盘问题；
   第二优先级是分析师每周都做的归因和复盘；
   第三优先级才是管理层周报/月报自动生成。

4. **数据库设计不能停留在原始订单表，要直接为分析任务建模。**
   不能只是把 Olist 原表搬进来，而要加工成更像阿里/京东内部数仓的分析模型：
   订单事实、流量漏斗事实、退款事实、活动维表、用户维表、商品维表、区域维表、日级聚合快照。

5. **Experience 不只是问答模板，还要沉淀分析套路。**
   例如"GMV 下跌归因"、"活动 ROI 复盘"、"退款率异常排查"这类经验，本质上是数据分析师的固定分析框架，
   这正是 FastIntent 最适合复用的东西。

6. **从 Day 1 起按“未来要上服务器”来设计，而不是最后再补部署。**
   配置、日志、启动方式、密钥管理、健康检查、目录结构都要提前考虑，
   否则最后很容易出现“本地能跑，服务器跑不稳”的返工。

7. **开发记录本身也是交付物。**
   不是做完再回忆，而是每天同步记录：
   - 今天做了什么
   - 遇到什么问题
   - 根因是什么
   - 最后怎么解决或绕过
   - 这件事后面对面试/博客有什么价值

8. **每天必须读懂一个问题，而不是只靠 vibe coding 推功能。**
   当天任务如果涉及框架源码，必须在 `DEVLOG.md` 里回答对应的“读懂的问题”。
   如果某个问题还没有答案，要明确标成“未确认”，不能用猜测代替理解。
   每天结束时至少沉淀一句面试表达，说明这个源码设计如何影响我们的电商分析 Agent。

### 国外路线里最值得拿来用的设计

1. **语义层先行。**
   先把指标定义、同义词、维度关系、默认过滤条件建好，再让自然语言问答发生。
   对我们来说，`metric_dictionary.yaml` 不该只是词典，而应该逐步升级成轻量 semantic model。

2. **按业务域划分分析空间。**
   不做一个万能聊天框，而是拆成"大盘分析空间 / 增长分析空间 / 退款归因空间 / 活动复盘空间"。
   每个空间只暴露少量可信表、少量 Tool、少量 Experience。

3. **复杂分析展示过程，不只给结论。**
   归因分析最好能显式展示步骤、使用的 Tool、中间结果和下一步依据，而不是只吐最终答案。

4. **标准问数和高级分析走双路径。**
   高频标准问题优先走快路径（语义层 + ads + 模板），复杂归因问题再走 Text2Code 深路径。

5. **高频问题沉淀成 verified cases。**
   把问过一次就算了的问题，变成长期可复用的可信分析资产，反哺 FastIntent、评测集和回归测试。

6. **问答 Agent 和行动 Agent 分层。**
   把"回答问题"和"定时监控 + 异常触发 + 推送通知"分成两套职责，避免一个 Agent 既当分析师又当调度器。

7. **补的是“数据系统能力”，不是“大数据名词堆砌”。**
   真正值得放进项目里的 big data 能力，是：
   - 数仓分层
   - 数据清洗 / 加工链
   - 日级快照生成
   - 结构化 + 非结构化混合分析
   - 调度与回流
   而不是为了关键词硬塞 Spark / Flink / Kafka。

---

## 目标用户与业务场景

### 目标用户（以电商平台内部为核心）

| 用户角色 | 日常工作 | 当前痛点 | 我们如何解决 |
|---------|---------|---------|------------|
| **电商运营** | 每天看 GMV/订单/活动效果，出日报周报 | 不会写 SQL，找数据团队要报告要等 1-2 天；发现问题时已经太晚 | 自然语言直接问，秒级出结果；异常自动推送飞书 |
| **数据分析师** | 响应业务方的数据需求，写 SQL、出图表 | 大量时间花在重复性查询；复杂归因要手工拼多个 SQL | FastIntent 复用，重复问题秒出；代码自动多步归因 |
| **品类/商品运营** | 管某几个品类的 GMV、转化、库存 | 品类下钻分析要手工一层一层看，耗时；BI 系统不灵活 | 自然语言描述分析需求，代码自动拆分维度 |
| **增长/营销团队** | A/B 实验结果分析、渠道 ROI、大促复盘 | 实验数据分析复杂，依赖数据团队排期 | 多步 Text2Code 一次跑完实验指标计算 |
| **数据平台团队** | 建设内部数据工具，降低数据需求密度 | 需要内部部署（数据合规），Coze 等 SaaS 不行 | Spring Boot 私有化部署，数据不出内网 |

### 核心业务场景（对标阿里/京东/拼多多日常运营）

**运营每天必看（高频 · 对标阿里日常大盘 + 京东商智）**
```
"昨天全站 GMV 多少？比大前天涨了多少？"
"今天 DAU / 活跃买家数是多少？"
"双11 大促第一天 GMV，比去年同期差多少？"
"哪些品类今天转化率最高？排前5的是哪些？"
```

**多维下钻归因（高价值 · 阿里分析师日常 + 京东 DataAgent 做不到的）**
```
"华东区域上周 GMV 环比下跌 18%，原因是什么？"
  → Step1: 查各城市 GMV，定位哪几个城市拖后腿
  → Step2: 查这些城市的退款率，判断是否退款异常
  → Step3: 查这些城市的转化率，判断是否流量质量下降
  → Step4: 查是否有活动结束 / 竞对有大促
  → Step5: 生成归因报告，推送给区域运营负责人

"上月女装品类客单价下降，但 GMV 涨了，这正常吗？"
  → 同时分析：客单价 × 订单量 → 判断是以量换价还是结构变化
```

**定时报告自动生成（刚需 · 所有电商运营团队）**
```
每天 8:00 自动推飞书：昨日大盘日报
  GMV（环比/同比）、订单量、客单价、DAU、退款率、异常预警

每周一 9:00：上周核心指标周报 + 趋势图
每月1号：上月业务月报（含分品类/分区域拆解）
```

**异常自动预警（高价值 · 拼多多/阿里风控+运营监控）**
```
GMV 环比下跌 > 20%       → 自动触发归因分析 → 推送飞书
某品类退款率突然 > 15%   → 自动排查：是哪个商家/哪款商品
新用户次日留存跌破阈值   → 推送增长团队，附留存漏斗分析
大促期间实时 GMV 追踪    → 每小时播报当前进度 vs 目标差距
```

**Ad-hoc 分析（长尾 · 解决数据需求积压）**
```
"帮我查上个季度各渠道的拉新成本，按 ROI 高到低排"
"618 期间各品类的转化率和去年相比哪些进步了哪些退步了"
"近90天复购率最高的10个品类是哪些，客单价多少"
```

### 演示数据库设计（模拟真实电商平台分析模型）

用 **Kaggle Olist 巴西电商数据集** 作为原始交易来源，但不直接照搬原表。
目标是加工出一套更接近阿里/京东/拼多多内部分析习惯的"事实表 + 维表 + 快照表"模型，
这样后面的 Tool 和 Experience 才不会被原始数据结构绑死。

**建模原则：**
- 支持"查数"和"归因"两类任务，不能只适合简单报表
- 支持时间、区域、品类、商家、渠道五个核心维度
- 支持漏斗、留存、退款、活动效果这几类电商高频分析
- 原始数据保留在 raw 层，演示分析走 curated 层

**推荐分层：**

```text
raw_olist_*            -- 原始导入层，尽量少改动，便于追溯
dwd_orders             -- 订单明细事实层
dwd_order_items        -- 订单商品明细事实层
dwd_user_events        -- 用户行为事件层（浏览/加购/下单/支付）
dwd_refunds            -- 退款事实层
dim_users              -- 用户维表
dim_products           -- 商品维表
dim_categories         -- 品类维表
dim_merchants          -- 商家维表
dim_regions            -- 区域维表
dim_promotions         -- 活动维表
ads_daily_core_metrics -- 日级核心指标快照
ads_category_daily     -- 品类日级快照
ads_region_daily       -- 区域日级快照
ads_channel_daily      -- 渠道日级快照
```

**核心 schema（curated 层）示意：**

```sql
-- 订单事实：GMV、订单量、客单价的主来源
dwd_orders
  order_id, user_id, merchant_id, order_status,
  order_amount, pay_amount, discount_amount,
  created_at, paid_at, finished_at,
  province_code, city_code, channel_code, promotion_id

-- 订单商品事实：品类、SKU、商家拆解的主来源
dwd_order_items
  item_id, order_id, product_id, category_id, merchant_id,
  quantity, unit_price, item_pay_amount, item_discount_amount

-- 用户行为事件：漏斗和转化率分析主来源
dwd_user_events
  event_id, user_id, session_id, product_id, category_id,
  event_type, event_time, province_code, city_code, channel_code
  -- event_type: view / click / cart / order_submit / pay

-- 退款事实：退款率和退款原因分析
dwd_refunds
  refund_id, order_id, item_id, refund_amount,
  refund_reason, refund_status, created_at

-- 用户维表：新老用户、渠道、区域分析
dim_users
  user_id, register_date, first_pay_date,
  acquisition_channel, user_tier, province_code, city_code

-- 商品维表：品类、价格带、品牌分析
dim_products
  product_id, merchant_id, category_id, brand_name,
  product_name, list_price, price_band, is_active

dim_categories
  category_id, category_l1, category_l2, category_l3

dim_merchants
  merchant_id, merchant_name, merchant_type, merchant_level,
  province_code, city_code

dim_promotions
  promotion_id, promotion_name, promotion_type,
  start_time, end_time, target_scope, budget

-- 日级快照：日报/周报直接读，减少复杂问题的基础聚合成本
ads_daily_core_metrics
  metric_date, gmv, order_count, buyer_count, dau,
  refund_amount, refund_rate, avg_order_value

ads_category_daily
  metric_date, category_id, gmv, order_count, buyer_count,
  refund_rate, conversion_rate

ads_region_daily
  metric_date, province_code, city_code, gmv, order_count,
  buyer_count, refund_rate, conversion_rate

ads_channel_daily
  metric_date, channel_code, new_users, pay_users, gmv, roi
```

**数据量级目标：** orders ~50万条，users ~20万，page_views ~200万（够体现查询耗时差异）

**为什么这样设计：**
- `dwd_*` 事实层保证可以做多步下钻，不会被预聚合限制死
- `ads_*` 快照层保证日报/周报这类高频任务足够快
- 维表单独拆开后，LLM 更容易理解字段语义，也更容易写 Tool 描述
- 活动、渠道、退款被纳入一等公民，是为了对齐真实电商分析而不是普通零售看板

---

## 整体阶段划分

```
Week 1  ████  框架内化：读源码，搞清楚核心机制
Week 2  ████  基础能力：数据接入 + Text2Code 首个 Demo 跑通
Week 3  ████  会话记忆：多轮对话 + 时间语义解析
Week 4  ████  歧义消解：指标词典 + 澄清问答机制
Week 5  ████  经验加速：Experience 体系 + FastIntent 集成
Week 6  ████  主动行动：定时触发 + 异常检测 + 多渠道推送
Week 7  ████  打磨收尾：可解释性 + Demo UI + PR 贡献
Week 8  ████  输出沉淀 + 部署交付：博客发布 + 面试材料整理 + 服务器上线
```

---

## 压缩执行版（6 周 / 42 天）

> **适用场景：** 求职时间紧，希望更快拿到一个能讲、能演示、能部署的项目版本。
> **原则：**
> 1. 同一天完成“设计 + 编码 + 最小验证 + DEVLOG”
> 2. 优先保留最有面试价值的主链路，弱化低优先级扩展
> 3. 博客草稿、README、面试稿不单独留整周，穿插完成
> 4. 每天至少回答 1 个“读懂的问题”，并写进 `DEVLOG.md` 的源码笔记区
> 5. 每天至少形成 1 句“面试可讲点”，说明当天源码理解如何影响产品/工程设计

### Week A（Days 1–7）框架内化 + 架构定稿
- Day 1：环境搭建、首次构建、启动 Demo、写第一条 DEVLOG
- Day 2：读懂 `GraalCodeExecutor` 的代码执行链，回答 5 个沙箱问题，写入 `DEVLOG`
- Day 3：读懂 Bridge 三件套，解释 Tool 如何被 Python 代码调用，画 Tool 接入流程图
- Day 4：读懂 Experience / FastIntent，解释经验如何进入 Prompt、快路径如何跳过推理
- Day 5：读懂 PromptBuilder / Evaluation Graph，解释一次完整请求如何被组装和评估
- Day 6：明确电商场景、对标阿里/京东/拼多多，把业务痛点映射到框架能力，并把映射落回 `Experience / semantic model / verified cases`
- Day 7：让“业务痛点 -> 框架能力”映射接回 Agent 主链：产品定位问答优先披露相关 common experience，`verified cases` 标签参与 prompt contribution 和评测分组；整理 `DEVLOG`、`PAIN_POINTS`、架构图，能用 5 分钟讲清为什么选 AssistantAgent

### Week B（Days 8–14）数据接入 + Tool 基础层
- Day 8：搭 raw/dwd/dim/ads 分层，读懂数据为什么不能直接暴露原始表，写入 `DEVLOG`
- Day 9：实现数据连接与基础统计能力，读懂框架 Tool 参数定义如何影响 LLM 调用
- Day 10：实现 `GmvQueryTool` + `OrderQueryTool`，记录“业务动作 Tool 化”的设计取舍
- Day 11：实现 `UserMetricTool` + `CategoryRankTool`，记录维度拆解对归因分析的价值
- Day 12：实现 `RefundAnalysisTool` + `FunnelAnalysisTool`，记录成交前/成交后两条业务线
- Day 13：补 Tool 描述、参数校验、`TOOL_CATALOG.md`，记录 Tool Schema 如何降低幻觉
- Day 14：跑第一个多步 Demo，录第一版 GIF，写清“这个 Demo 解决什么业务问题”

### Week C（Days 15–21）会话记忆 + 歧义消解
- Day 15：做 Session 状态结构 + 代词消解
- Day 16：做时间语义解析 + 多轮上下文注入
- Day 17：完成 5 个多轮场景测试
- Day 18：构建 `metric_dictionary.yaml`
- Day 19：实现歧义检测 + 澄清问答
- Day 20：抽 `semantic_model.yaml` 第一版
- Day 21：整理这一阶段 DEVLOG、写多轮/指标词典面试故事

### Week D（Days 22–28）Experience / FastIntent / 评测闭环
- Day 22：手写 10 个电商 Experience 模板，读懂 COMMON/REACT/TOOL 三类经验边界
- Day 23：建立 `verified_cases.yaml` + bad case 池，记录“好案例/坏案例如何变成系统资产”
- Day 24：接入 FastIntent，读懂匹配方式、阈值、fallback 和参数来源
- Day 25：做命中率测试 + bad case 分类，写清误触发/漏触发的业务风险
- Day 26：做快路径/深路径/兜底路径基准测试，记录为什么标准问数和复杂归因要分路
- Day 27：设计轻量自演进闭环，写清 verified case -> Experience -> semantic model 修正链路
- Day 28：沉淀 `evaluation_summary.md` + FastIntent 博客草稿，形成一个可复述的评测驱动故事
- 并行数据升级支线：如果 Agent 主链已跑通，可从 Day 24 起同步推进 Olist 接入（`raw -> dwd -> dim -> ads`），但不要替换当前 seed data 主链；优先只迁 `GMV / 区域 / 品类` 三条快路径

### Week E（Days 29–35）主动行动 + 安全鲁棒性
- Day 29：读 trigger 模块，拆 Data Agent / Operations Agent
- Day 30：实现定时日报 + 飞书 Webhook
- Day 31：实现异常检测触发 + 归因链
- Day 32：跑端到端“异常 → 分析 → 推送”链路
- Day 33：做 Tool 白名单、SQL 只读校验、Prompt Injection 最小防线
- Day 34：做降级、缓存、超时、幂等、防重复推送
- Day 35：整理 `SECURITY.md`、`ROBUSTNESS.md`、线上问题案例

**当前完成度（更新到 2026-05-20）：**

- `Day 29`：**已完成**
  - 已沿框架原生 `trigger + reply` 主链接入业务执行器
  - 已拆出：
    - `daily_report`
    - `gmv_drop_watch`

- `Day 30`：**已完成**
  - 已实现定时日报 trigger
  - 已支持 `send_notification`
  - 已补成可展示版本：
    - [DEMO_DAILY_REPORT_CHAIN.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/DEMO_DAILY_REPORT_CHAIN.md)

- `Day 31`：**已完成（第一版 + 稳定性增强）**
  - 已实现 GMV 异常触发
  - 已从“线索摘要”升级成 root cause 推送格式
  - 已从单一 `前一天 15% 阈值` 升级成：
    - `前一天`
    - `上周同一天`
    - `最近几天均值`
    - `去年同期（可用则参与参考）`
    - `节假日 / 活动期阈值收紧`

- `Day 32`：**已完成（最小端到端链）**
  - 已打通：
    - 触发
    - 查询 / 归因
    - 推送
    - 启动期校验

- `Day 33`：**已完成（第一层）**
  - 已做 Tool 白名单式注入
  - 已做 SQL 只读校验
  - Prompt Injection 已从最小防线推进到入口风险检测 + 审计日志

- `Day 34`：**已完成（单机可靠层）**
  - 已完成：
    - 超时配置
    - 最小重试
    - 防重复推送
    - 系统化缓存策略：TTL、最大条目、空结果不缓存
    - root cause 部分降级：单个 Tool 失败时返回已完成证据和 `degradations`
    - 持久化幂等可观察：区分内存去重和持久化去重
    - Prompt Injection 风险输入检测与审计日志
  - 仍需诚实说明：
    - 当前是单共享数据库口径，不是多节点分布式强一致幂等

- `Day 35`：**已完成（第一版）**
  - 已整理：
    - [SECURITY.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/SECURITY.md)
    - [ROBUSTNESS.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/ROBUSTNESS.md)
  - “线上问题案例”目前主要沉淀在：
    - [DEVLOG.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/DEVLOG.md)

**一句话总结：**

> Week E 当前已经完成到“主动日报 + 异常巡检 + 第一层安全/鲁棒性可见化”。  
> 如果继续推进，重点不再是“有没有主动链”，而是要不要继续补更强的降级、缓存和持久化幂等。

### Week F（Days 36–42）展示收尾 + 部署上线 + 面试资产
- Day 36：做分析轨迹展示、Trace 标签、Jaeger 可观测性
- Day 37：完善 Streamlit UI，支持 analysis space 和 3 种输出视图
- Day 38：README、架构图、Agent 编排图、评测闭环图
- Day 39：整理 `application-prod.yml`、`.env.example`、部署说明
- Day 40：编写 `Dockerfile`、`docker-compose.yml`、启动脚本
- Day 41：上 Linux 服务器演练部署，验证 API / UI / 飞书链路
- Day 42：整理简历项目描述、1 分钟版、3 分钟版、岗位映射版，发布 v1.0

### 压缩版明确延后项
- 非核心博客可以延后到投递后继续发
- K8s、复杂多租户、重型权限体系先不做
- 先不追求全量自动学习发布，保留人工审核

---

## Week 1：框架内化（Days 1–7）

> **目标：** 在开始写一行业务代码之前，把框架的核心机制彻底弄清楚。
> 只有真正理解框架，才能知道在哪里扩展、在哪里踩坑、在哪里值得写 PR。

---

### Day 1 — 环境搭建 + 首次构建

**主任务：**
```bash
cd AssistantAgent
./.local-tools/apache-maven-3.9.11/bin/mvn clean package -DskipTests
```
- 确认 Java 17 / Maven 3.9 环境正常
- 记录构建耗时、JAR 大小
- 跑一下 `assistant-agent-start`，看默认 Demo 能不能起来

**预期产出：** 构建成功截图 + DEVLOG 第一条记录

**可能踩坑：**
- GraalVM 相关依赖下载失败（国内网络）→ 配置 Maven 阿里云镜像
- Java 版本不匹配（需要 17，不能低）
- 找不到主启动类 → 看 `assistant-agent-start` 模块的 `pom.xml`

**部署视角额外确认：**
- 记录运行时依赖：Java 17、GraalVM Python、Maven 打包产物、外部 API Key
- 提前确认未来服务器最小运行形态：`Spring Boot fat jar + Linux + 反向代理`

**记录要求：**
- 当天必须在 `DEVLOG.md` 写第 1 条正式记录
- 从今天开始，不允许把问题积压到周末再回忆补写

---

### Day 2 — 读 GraalCodeExecutor

**目标文件：** `assistant-agent-core/src/.../executor/GraalCodeExecutor.java`

**读懂的问题：**
1. LLM 生成的代码是怎么传入沙箱的？
2. 执行超时 / 内存限制是在哪里配置的？
3. Python 环境和 JS 环境是怎么切换的？
4. 执行结果是什么格式返回给上层的？
5. 执行报错时，错误信息怎么处理（反馈给 LLM 重试？）

**预期产出：** DEVLOG "源码笔记 → GraalVM 沙箱执行机制" 填充完毕

**可能踩坑：** 代码量大，先看类图结构，不要一行一行读

---

### Day 3 — 读 Bridge 三件套

**目标文件：**
- `executor/bridge/AgentToolBridge.java` — 工具如何注入沙箱
- `executor/bridge/StateBridge.java` — 状态如何在代码执行间传递
- `executor/bridge/LoggerBridge.java` — 执行日志如何捕获

**读懂的问题：**
1. 自定义一个 Tool 需要实现什么接口、加哪些注解？
2. StateBridge 的状态是 Session 级还是请求级？
3. LoggerBridge 的日志能不能被上层应用消费（用于溯源展示）？

**预期产出：** 手画一张"自定义 Tool 接入流程图"

---

### Day 4 — 读 Experience 体系

**目标文件：**
- `extensions/experience/disclosure/ExperienceDisclosureService.java`
- `extensions/experience/fastintent/FastIntentService.java`
- `management/` 模块中关于 Experience CRUD 的部分

**读懂的问题：**
1. COMMON / REACT / TOOL 三种类型分别在 Prompt 的哪个位置注入？
2. Experience 是存在数据库还是内存里？
3. 新增一条 Experience 的 API 格式是什么？
4. FastIntent 命中时，"参数提取"是怎么做的（正则？LLM 提取？）

**预期产出：** DEVLOG 的 Experience 体系笔记

---

### Day 5 — 读 FastIntentService

**目标文件：** `extensions/experience/fastintent/FastIntentService.java`

**读懂的问题：**
1. 语义相似度用了什么 Embedding 模型，cosine similarity 阈值是多少？
2. 命中 FastIntent 后，参数是怎么从用户输入里提取的？
3. 命中率/误触发率的监控在哪里？
4. 可以插入自定义 Embedding 模型吗（换成中文模型）？

**预期产出：** 确定是否需要替换中文 Embedding 模型，记录结论

---

### Day 6 — 读 Evaluation Graph + PromptBuilder

**目标文件：**
- `assistant-agent-evaluation/` — 评估图节点结构
- `assistant-agent-prompt-builder/` — Prompt 动态组装逻辑

**读懂的问题：**
1. 评估图的节点是 Spring Bean 吗，怎么串联？
2. Prompt 里的变量占位符格式是什么？
3. 系统 Prompt 的组装顺序（哪部分在前哪部分在后）？

**预期产出：**
- 整理出"一次完整请求的 Prompt 长什么样"的示例
- 补一份 `business_capability_map`：把阿里/京东/拼多多式痛点映射到框架能力
- 明确哪些 common experience 应该在“产品定位 / 业务价值”问答里优先披露

---

### Day 7 — 框架笔记汇总 + 项目架构初始化

**主任务：**
1. 把 Day 2–6 的笔记整理进 `DEVLOG.md` 的"源码笔记"区
2. 确定项目模块结构（电商场景定制）：
   ```
   ecommerce-analysis-agent/         ← 新建的业务模块
   ├── src/main/java/
   │   ├── semantic/
   │   │   ├── SemanticMetricRegistry ← 轻量指标语义层
   │   │   ├── SemanticDimensionMap   ← 地域/品类/渠道语义映射
   │   │   └── VerifiedCaseRegistry   ← 已验证高频分析问题
   │   ├── space/
   │   │   ├── OverviewAnalysisSpace  ← 大盘分析空间
   │   │   ├── GrowthAnalysisSpace    ← 增长分析空间
   │   │   ├── RefundAnalysisSpace    ← 退款归因空间
   │   │   └── CampaignReviewSpace    ← 活动复盘空间
   │   ├── connector/
   │   │   ├── MysqlDataConnector     ← MySQL 查询（主力）
   │   │   └── DataConnector          ← 接口（便于扩展 Hive/ClickHouse）
   │   ├── tool/
   │   │   ├── GmvQueryTool           ← GMV 查询（最核心）
   │   │   ├── OrderQueryTool         ← 订单量/订单状态
   │   │   ├── UserMetricTool         ← DAU/MAU/新用户/留存
   │   │   ├── FunnelAnalysisTool     ← 转化漏斗（浏览→加购→下单→支付）
   │   │   ├── RefundAnalysisTool     ← 退款率/退款原因
   │   │   ├── CategoryRankTool       ← 品类排名/品类对比
   │   │   ├── MerchantAnalysisTool   ← 商家 GMV/商家排名
   │   │   ├── ChartTool              ← 图表生成（折线/柱状/漏斗图）
   │   │   ├── TimeParserTool         ← 中文时间语义解析
   │   │   └── StatsTool              ← 统计计算（环比/同比/增长率）
   │   ├── experience/
   │   │   ├── DailyReportExperience  ← 日报模板
   │   │   ├── WeeklyReportExperience ← 周报模板
   │   │   ├── GmvAnalysisExperience  ← GMV 分析模板
   │   │   ├── AnomalyRootCause       ← 异常归因模板
   │   │   └── MetricDictionary       ← 电商指标词典（GMV/DAU/转化率等）
   │   ├── trigger/
   │   │   ├── DailyReportTrigger     ← 每日 8:00 日报
   │   │   ├── GmvAnomalyTrigger      ← GMV 跌幅预警
   │   │   └── RefundAnomalyTrigger   ← 退款率异常预警
   │   ├── ops/
   │   │   ├── DataAnalysisAgent      ← 负责问答/分析
   │   │   └── OperationsAgent        ← 负责监控/触发/通知
   │   └── reply/
   │       ├── FeishuReplyAdapter     ← 飞书消息卡片
   │       └── DingtalkReplyAdapter   ← 钉钉通知
   └── src/main/resources/
       ├── demo-data/
       │   ├── init_schema.sql        ← 建表 SQL（电商 schema）
       │   └── load_data.sql          ← Olist 数据导入脚本
       ├── deploy/
       │   ├── Dockerfile             ← 服务器部署镜像
       │   ├── docker-compose.yml     ← 单机部署编排
       │   ├── nginx.conf             ← 反向代理 / 静态入口
       │   └── start-prod.sh          ← 生产启动脚本
       └── experiences/
           ├── metric_dictionary.yaml ← 电商指标词典
           ├── semantic_model.yaml    ← 轻量语义模型
           ├── verified_cases.yaml    ← 已验证高频问题集合

3. 把“业务痛点 -> 框架能力”映射接回 Agent 主链：
   - 对“这是什么产品 / 解决什么业务问题 / 和阿里京东有什么差异”这类问答，优先披露对应 common experience
   - 给 `verified_cases.yaml` 增加 `target_role / benchmark_reference / framework_capabilities`
   - 让这些标签参与 prompt contribution 选择和 evaluation summary 分组
           └── report_templates.yaml  ← 报告模板 Experience
   ```
3. 初始化 Maven 模块，`mvn compile` 通过

**额外要求：**
- 从一开始区分 `application.yml` 和未来的 `application-prod.yml`
- 所有密钥默认走环境变量，不把生产配置写死在仓库里
- 从 Day 7 起固定 `DEVLOG` 记录格式：现象 → 根因 → 解法 → 影响 → 面试可讲点

**预期产出：** 项目骨架可编译，DEVLOG 框架笔记完整，架构图画出来

---

## Week 2：基础能力（Days 8–14）

> **目标：** 实现第一个能跑通的 Text2Code 数据分析 Demo。
> 完成这周，就可以说"Agent 已经能用代码查数据库并多步推理了"。

---

### Day 8 — 电商数据库搭建 + 数据接入层

**主任务：**
- 下载 Olist 数据集，导入 raw 层，再加工出 curated 分析层（脚本写进 `demo-data/init_schema.sql`）
- 先建 `dwd_* / dim_* / ads_*` 三层，而不是直接暴露原始表给 Agent
- 确保核心字段名和注释符合中国电商分析习惯（GMV、DAU、渠道、品类、退款率等）
- 顺手沉淀字段别名、指标默认口径、区域映射、类目层级，为后续语义层做准备
- 实现 `MysqlDataConnector`，注册为 Bridge Tool
- 设计一条最小可用数据加工链：
  - raw 导入
  - dwd 清洗
  - dim 补维
  - ads 日级快照生成
- 把这条链当作项目里的“轻量数据工程底座”，而不是简单 SQL 脚本堆砌

```java
// GraalVM 沙箱里的 Python 代码可以这样调用：
// result = connector.query("SELECT SUM(payment_amount) FROM orders WHERE paid_at >= '2024-01-01'")
```

**数据量目标：** orders 50万+，users 20万+，page_views 200万+（用脚本扩充原始数据倍数）

**预期产出：** Python 代码在沙箱里能查到数据，打印出来验证数字正确

**可能踩坑：**
- JDBC 在 GraalVM 沙箱里的调用方式（Python 调 Java Bridge，不是直接用 pymysql）
- Java BigDecimal → Python float 的序列化精度问题
- Olist 原始数据和国内电商业务字段不完全对应，需要在加工层补齐渠道/活动/区域口径

**big data 视角要求：**
- 在 DEVLOG 里单独记录：为什么这里采用 raw/dwd/dim/ads，而不是直接查原始表
- 明确这部分对应的是“数仓建模 + 轻量 ETL”能力

---

### Day 9 — 注册电商核心分析 Tool（第一批）

**主任务：** 先按"每日看数 / 周度分析 / 异常归因"三类任务来设计 Tool，而不是按数据库表来设计。
封装 6 个最核心的电商分析 Tool，每个 Tool 的描述要写清楚（LLM 靠描述生成代码）：

```python
# LLM 生成的代码示例（在 GraalVM 沙箱内运行）
# 查华东区域上周 GMV
result = gmv_tool.query(
    start_date="2024-05-06", end_date="2024-05-12",
    region="华东", breakdown_by="city"  # 按城市拆分
)
for city in result['breakdown']:
    print(f"{city['name']}: {city['gmv']:,.0f}元")
```

**6 个 Tool（第一批，Day 13 再补全）：**

| Tool | 核心参数 | 返回值 |
|------|---------|-------|
| `GmvQueryTool` | start_date, end_date, region, category, breakdown_by | total_gmv, order_count, breakdown[] |
| `OrderQueryTool` | date_range, status, region | order_count, avg_amount, status_breakdown |
| `UserMetricTool` | metric(DAU/MAU/new/retention), date | value, compare_yesterday, compare_lastweek |
| `FunnelAnalysisTool` | start_date, end_date, category | pv→cart→order→pay 各步骤量和转化率 |
| `RefundAnalysisTool` | date_range, category, merchant_id | refund_rate, refund_amount, top_reasons[] |
| `StatsTool` | values[], mode(growth_rate/rank/compare) | 计算结果，支持环比/同比/排名 |

**设计要求：**
- Tool 返回结构要偏业务语义，不要直接返回 JDBC 原始结果集
- 每个 Tool 要说明适用场景，例如"日报"、"区域对比"、"异常排查"
- Tool 描述里要写清楚口径，尤其是 GMV、DAU、退款率、转化率
- Tool 元数据要能挂到 analysis space 上，后续支持"某空间只暴露部分 Tool"

**预期产出：** 6 个 Tool 各有单元测试，结果与手工 SQL 验证一致

---

### Day 10 — 第一个多步 Text2Code Demo

**主任务：** 先跑通一个"对比型" Demo，再准备一个更贴近真实业务的"归因型" Demo。

**Demo A：**

> "1月北京和上海GMV分别是多少？哪个城市高？差距是多少？"

期望 LLM 生成并执行：
```python
bj = gmv_tool.query(start="2024-01-01", end="2024-01-31", region="北京")
sh = gmv_tool.query(start="2024-01-01", end="2024-01-31", region="上海")
diff = abs(bj['total'] - sh['total'])
winner = "北京" if bj['total'] > sh['total'] else "上海"
reply(f"北京GMV {bj['total']}，上海GMV {sh['total']}，{winner}高出 {diff}")
```

**Demo B：**
> "上周华东 GMV 为什么跌了？先按城市拆，再看看是不是退款率升高导致的。"

这个 Demo 更重要，因为它更能体现我们和 JoyAgent DataAgent 的差异。

**预期产出：** 截图 + 录制 GIF，存入 `docs/demo/` 目录

**可能踩坑：**
- LLM 生成的变量名和 Tool 名不匹配 → 在 Prompt 里明确列出所有 Tool 调用签名
- 代码执行超时 → 看 Day 2 笔记确认默认超时配置

---

### Day 11 — GraalVM Python 环境踩坑日

**主任务：**
- 搞清楚 GraalVM Python 环境是否支持 pandas / numpy / matplotlib
- 如果不支持（概率很高，GraalPy 对 C 扩展受限）→ 设计替代方案：
  - 把计算逻辑做成 Java Tool（`StatsTool`、`ChartTool`），Python 只做逻辑控制
- 记录结论到 DEVLOG（这是高价值的踩坑记录，写博客素材）

**预期产出：** 明确的技术决策 + DEVLOG 记录，不管结论是什么都有价值

---

### Day 12 — 图表生成能力

**主任务：**
- 实现 `ChartTool`（Java 端生成图表，Bridge 注入沙箱）
- Python 代码调用 `chart_tool.bar(labels=[...], values=[...])`，返回 base64 图片
- Agent 回复里能展示"过去 30 天 GMV 趋势折线图"
- 同时验证日报场景需要的 3 类图：趋势折线、品类排行柱状、漏斗图

**预期产出：** 能生成并展示图表的 Demo GIF

**big data 视角补充：**
- 验证图表默认优先读取 `ads_*` 快照，而不是每次扫描明细表
- 明确“快照层服务高频查询，明细层服务深度下钻”的分工

---

### Day 13 — 补全 Tool + 编写 Tool 描述文档

**主任务：**
- 补全所有 Tool 的参数校验（非法日期、不存在的地区名等的报错处理）
- 为每个 Tool 写详细的 JavaDoc（LLM 看 Tool 描述来生成代码，描述质量直接影响准确率）
- 写 `docs/TOOL_CATALOG.md`：列出所有 Tool 的名称、参数、返回值格式、调用示例
- 给每个 Tool 增加"推荐组合用法"，例如 `gmv_tool + refund_tool + stats_tool` 用于异常归因
- 在 `TOOL_CATALOG.md` 里增加"所属分析空间"字段，开始形成领域化边界

**注：** Tool 描述 = LLM 的 API 文档，这步的认真程度直接影响整体准确率

---

### Day 14 — 多步推理压测 + DEVLOG

**主任务：** 构造 10 个测试问题，覆盖不同复杂度：

| 类型 | 示例问题 |
|------|---------|
| 简单（1步） | "昨天的订单量是多少？" |
| 中等（2-3步） | "上周GMV环比上上周涨了多少？涨幅多少？" |
| 复杂（4步+） | "上月哪些城市GMV下降超10%？这些城市的退款率是否也偏高？" |

**评估指标：**
- 结果正确率
- 代码执行成功率（有没有运行时报错）
- 平均响应时间
- 查询层命中情况（走 `ads_*` 还是落到 `dwd_*`）

**预期产出：** 测试报告 + DEVLOG 记录第一批 Bug

---

## Week 3：会话记忆（Days 15–21）

> **目标：** 让 Agent 记住多轮对话上下文，支持"那上海呢"/"比上上月呢"这类追问。
> 完成这周，就解决了 Snowflake Cortex 和 Fabric Copilot 都做不到的事。

---

### Day 15 — 读 context/ 模块源码

**目标文件：** `assistant-agent-core/src/.../context/`

**读懂的问题：**
1. Session 上下文的数据结构（HashMap？自定义对象？）
2. 上下文的生命周期（什么时候创建，什么时候销毁）
3. Python 代码里如何读写 Session 上下文（通过 StateBridge？）
4. 上下文有没有大小限制（防止 OOM）

**预期产出：** 会话上下文读写的完整 API 调用示例

---

### Day 16 — Session 状态数据结构设计

**主任务：** 设计分析会话的状态结构：

```java
// 每次分析执行后，把关键信息存入 Session
class AnalysisContext {
    String lastQuerySummary;          // "查了1月北京GMV：3200万"
    Map<String, Object> lastResults;  // {"bj_gmv": 32000000, "date_range": "2024-01"}
    String lastRegion;                // "北京"（支持"那上海呢"的代词消解）
    String lastTimePeriod;            // "2024-01"（支持"比上上月呢"的时间推算）
    List<String> conversationHistory; // 最近 N 轮对话摘要
}
```

**预期产出：** `AnalysisContext.java` 完成，能通过 StateBridge 在代码执行间持久化

---

### Day 17 — 代词/省略语消解

**主任务：** 实现"那上海呢" = "上海的 [上一个问题的指标] [上一个问题的时间范围]"

策略：在 Prompt 里注入上一轮上下文摘要，让 LLM 自动补全省略部分：
```
[系统注入]
上一轮分析：1月北京GMV 3200万，时间范围=2024年1月
当前可用区域：北京、上海、广州、深圳、成都
```

**测试用例：**
```
Q1: 1月北京GMV多少？→ 3200万
Q2: 那上海呢？      → 理解为"1月上海GMV"
Q3: 两个相差多少？  → 直接用已知数据计算，不再查数据库
```

**可能踩坑：** 上下文摘要太长占大量 token → 压缩处理

---

### Day 18 — Session 历史注入优化

**主任务：**
- 确定保留最近几轮历史（推荐 5 轮）
- 对历史做摘要压缩（"第N轮：查了XXX，结果是XXX"，而非原文）
- 测试不同历史长度对 LLM 理解准确率的影响

**预期产出：** 压缩算法实现 + 不同历史长度的准确率对比数据

---

### Day 19 — 时间语义解析器

**主任务：** 实现 `TimeExpressionParser`，把中文时间表达转换为精确日期范围：

```java
parse("上个月")    → 2024-04-01 ~ 2024-04-30
parse("上上月")    → 2024-03-01 ~ 2024-03-31
parse("大前天")    → 2026-05-15
parse("Q3")       → 2024-07-01 ~ 2024-09-30
parse("上周")     → 本周一往前推7天
parse("去年同期")  → 去年同月份
```

**注意：** 这个 Parser 要注册成 Tool，LLM 生成代码时可直接调用 `time_parser.parse("上上月")`

**预期产出：** 覆盖 15+ 中文时间表达的单元测试全部通过

---

### Day 20 — 多轮对话端到端测试

**5 个典型场景全部跑通：**

```
场景1（代词追问）
  Q1: 上月北京GMV？  Q2: 那上海呢？  Q3: 哪个高？差多少？

场景2（时间追问）
  Q1: 上周转化率？  Q2: 上上周呢？  Q3: 环比变化？

场景3（维度切换）
  Q1: 1月各城市GMV排名  Q2: 退款率最高的那个城市，上月情况呢？

场景4（指标切换）
  Q1: 上月DAU  Q2: MAU呢？  Q3: 月活/日活比是多少？

场景5（深度追问）
  Q1: 哪些城市上月GMV下降？  Q2: 其中下降最多的，原因是什么？
```

**目标：** 5/5 全通过；没过的记录原因，作为下一步优化方向

---

### Day 21 — DEVLOG + 博客草稿

博客主题：**《多轮数据分析对话：状态管理的正确姿势》**

核心论点：
- Snowflake Cortex / Fabric Copilot 为什么做不到多轮（有文档数据支撑）
- Session 上下文的设计难点（代词消解 vs 时间推算 vs 历史压缩三选一还是全做）
- 在 AssistantAgent 里的实现思路（代码片段展示）

**额外要求：**
- 从前 3 周的记录里提炼至少 3 个 STAR 故事草稿，开始形成面试素材池

---

## Week 4：歧义消解（Days 22–28）

> **目标：** 让 Agent 在遇到模糊问题时主动澄清，而不是"猜一个"然后给错答案。
> 核心产出是运营行业指标词典——这个东西本身就有博客和开源价值。

---

### Day 22 — 构建电商指标词典 v1

**背景：** 阿里/京东/拼多多的运营团队每天用的核心指标其实就那几十个，
但同一个词在不同公司、不同部门可能定义完全不同——这就是 NL2SQL 系统"猜中
你说的是哪个口径"的根本难题。我们的解法是把定义显式写进词典。

**主任务：** 设计并编写 `resources/experiences/metric_dictionary.yaml`：

```yaml
metrics:
  # ── 成交类核心指标 ──────────────────────────────────────────────
  - id: gmv
    name: GMV（成交总额）
    aliases: ["成交额", "销售额", "流水", "营业额", "交易额"]
    definition: "买家实际付款金额之和，不含运费，不含已退款订单"
    # ⚠️ 阿里/京东/拼多多口径差异：
    # 阿里：付款GMV，包含优惠券抵扣后金额
    # 京东：结算GMV，部分场景含京豆抵扣
    # 拼多多：付款GMV，百亿补贴商品单独统计
    # 本项目统一口径：payment_amount WHERE status IN ('paid','completed')
    sql_template: "SELECT SUM(payment_amount) FROM orders WHERE status IN ('paid','completed') AND {date_filter}"
    default_filters:
      - "exclude_refunded: true"
      - "exclude_test_orders: true"
    allowed_dimensions: ["region", "city", "category", "merchant", "channel", "date"]
    common_confusion:
      - "含退款/不含退款：GMV 默认不含退款，含退款请说'含退款GMV'"
      - "含运费/不含运费：默认不含运费"
    disambiguation_required: false

  - id: order_count
    name: 订单量
    aliases: ["单量", "订单数", "下单量", "成单数"]
    definition: "付款成功的订单数量（一笔订单可能含多个商品）"
    sql_template: "SELECT COUNT(DISTINCT order_id) FROM orders WHERE status IN ('paid','completed') AND {date_filter}"
    allowed_dimensions: ["region", "category", "channel", "merchant"]
    common_confusion:
      - "订单量 vs 商品件数：订单量是单数，不是件数"
      - "下单量 vs 付款量：本系统统一用付款量"
    disambiguation_required: false

  - id: aov
    name: 客单价（AOV）
    aliases: ["客单价", "平均客单", "单均价", "每单金额"]
    definition: "GMV ÷ 订单量，即每笔付款订单的平均金额"
    calculation: "gmv / order_count"
    allowed_dimensions: ["region", "category", "channel"]
    disambiguation_required: false

  # ── 用户类指标 ──────────────────────────────────────────────────
  - id: dau
    name: 日活跃用户数（DAU）
    aliases: ["日活", "DAU", "当日活跃"]
    definition: "当日有登录/浏览/下单行为的去重用户数"
    sql_template: "SELECT COUNT(DISTINCT user_id) FROM user_daily_active WHERE active_date = {date}"
    disambiguation_required: false

  - id: mau
    name: 月活跃用户数（MAU）
    aliases: ["月活", "MAU", "当月活跃"]
    definition: "当月内有任意行为的去重用户数"
    disambiguation_required: false

  - id: active_user
    name: 活跃用户数
    aliases: ["活跃用户", "活跃买家", "活跃人数"]
    # ⚠️ 最高频歧义词：不说清楚是日/周/月活就没法查
    disambiguation_required: true
    disambiguation_question: "您说的「活跃用户」是指哪个周期？\nA. 日活（DAU）—— 当天有行为\nB. 周活（WAU）—— 近7天有行为\nC. 月活（MAU）—— 当月有行为"

  - id: new_user
    name: 新用户数
    aliases: ["新用户", "新客", "首购用户", "新增用户"]
    definition: "统计周期内首次完成付款的用户数"
    # 注意：注册用户 ≠ 首购用户，运营问的一般是首购
    common_confusion:
      - "注册用户 vs 首购用户：默认统计首购用户，如需注册用户请说明"
    disambiguation_required: false

  - id: retention_rate
    name: 留存率
    aliases: ["留存", "留存率", "次日留存", "7日留存", "30日留存"]
    disambiguation_required: true
    disambiguation_question: "您说的「留存率」是哪种？\nA. 次日留存（D1）\nB. 7日留存（D7）\nC. 30日留存（D30）"

  - id: repurchase_rate
    name: 复购率
    aliases: ["复购率", "回购率", "复购", "老客占比"]
    definition: "统计周期内，购买2次及以上的用户占总购买用户的比例"
    disambiguation_required: false

  # ── 转化类指标 ──────────────────────────────────────────────────
  - id: conversion_rate
    name: 转化率
    aliases: ["转化率", "成交转化", "购买转化"]
    definition: "付款用户数 ÷ 访问用户数（PV→付款的整体漏斗）"
    # ⚠️ 高频歧义：运营说的转化率可能是任意一段漏斗
    disambiguation_required: true
    disambiguation_question: "您说的「转化率」是哪个阶段的转化？\nA. 浏览→加购\nB. 加购→下单\nC. 下单→付款\nD. 浏览→最终付款（整体）"

  - id: cart_rate
    name: 加购率
    aliases: ["加购率", "加车率", "加购转化"]
    definition: "加购用户数 ÷ 浏览用户数"
    disambiguation_required: false

  # ── 退款类指标 ──────────────────────────────────────────────────
  - id: refund_rate
    name: 退款率
    aliases: ["退款率", "退货率", "退单率", "退款比例"]
    definition: "退款订单金额 ÷ 付款GMV（金额维度）"
    # 注意：件数退款率 vs 金额退款率 是两个口径
    common_confusion:
      - "金额退款率 vs 件数退款率：默认金额口径，如需件数请说明"
      - "退款率异常参考阈值：服装类 < 15%，3C类 < 5%，食品类 < 3%"
    disambiguation_required: false

  # ── 大促类指标（双11/618 专用）─────────────────────────────────
  - id: promo_gmv
    name: 大促GMV
    aliases: ["大促销售额", "活动GMV", "618GMV", "双11GMV"]
    definition: "活动期间的付款GMV，需关联 promotions 表过滤活动时间段"
    sql_template: "SELECT SUM(o.payment_amount) FROM orders o JOIN promotions p ON o.paid_at BETWEEN p.start_date AND p.end_date WHERE p.promo_id = {promo_id}"
    disambiguation_required: false

  - id: promo_yoy
    name: 大促同比
    aliases: ["大促同比", "大促vs去年", "活动增长"]
    definition: "今年大促GMV vs 去年同活动GMV，注意活动天数需对齐"
    common_confusion:
      - "今年vs去年天数可能不同，需按单日GMV对比或对齐天数"
    disambiguation_required: true
    disambiguation_question: "大促同比是按总额对比还是按日均对比？"
```

**目标：** 本版覆盖 25 个核心电商指标，后续随踩坑持续补充

**额外要求：**
- 每个指标补 `default_filters`（默认过滤条件）
- 每个指标补 `allowed_dimensions`（允许拆分维度）
- 结构向 semantic model 靠拢，既能给 Prompt 注入，也能给 Tool 参数校验复用
- `common_confusion` 里的阈值/口径差异直接引用阿里/京东/拼多多惯例（有对比价值）

---

### Day 23 — 实现歧义检测

**主任务：** 在 Prompt 组装阶段，检测用户输入是否命中 `disambiguation_required: true` 的指标：

```
用户输入：「活跃用户最近怎么样」
检测到：「活跃用户」命中 active_user，该指标需要澄清
触发：澄清问答流程（不直接执行分析）
```

**预期产出：** 用 20 个歧义问题验证检测命中率

---

### Day 24 — 实现澄清问答机制

**主任务：** 实现"提问 → 等待用户选择 → 带用户答案继续执行"的完整流程：

```
Agent：您说的"活跃用户"是指：
       A. 日活（DAU）—— 当天登录用户数
       B. 月活（MAU）—— 当月内登录用户数
用户：B
Agent：[继续执行月活分析]
```

**可能踩坑：**
- 多轮对话"等待用户回答"的状态管理（如何保存"等待澄清中"的中间状态）
- 用户直接回答"月活"而不是"B"时的识别处理

---

### Day 25 — 指标词典 → COMMON Experience

**主任务：** 把指标词典转化为 Experience，让相关问题的 Prompt 自动注入指标定义：

```
当用户问 GMV 相关问题时，Prompt 自动附加：
"[指标说明] GMV = 含税支付金额（不含退款）。如需含退款口径请明确说明。"
```

**注意：** 注入内容要简洁，不能把整个词典塞进 Prompt

**预期产出：** 5 个核心指标的 COMMON Experience 测试生效

**额外产出：**
- 抽出 `semantic_model.yaml` 第一版，只覆盖 4 个高频域：大盘、用户增长、退款、活动
- 每个域包含 metrics、dimensions、synonyms、default_time_field、verified_examples

---

### Day 26 — 歧义澄清端到端测试

**10 个场景（"该触发"与"不该触发"各半）：**

| 问题 | 预期 |
|------|------|
| "活跃用户涨了吗" | 触发 DAU/MAU 澄清 |
| "增长率" | 触发环比/同比澄清 |
| "转化率" | 触发漏斗层级澄清 |
| "上月表现怎么样" | 触发"哪个指标"澄清 |
| "用户数" | 触发注册用户/活跃用户澄清 |
| "GMV" | **不触发**（GMV 定义清晰） |
| "客单价" | **不触发** |
| "昨天订单量" | **不触发** |
| "退款率" | **不触发** |
| "DAU 是多少" | **不触发**（已经指定日活） |

**目标：** 准确率 > 80%

---

### Day 27 — 触发阈值调优

**主任务：** 分析 Day 26 误触发/漏触发的原因，调整：
- 歧义词的匹配策略（精确匹配 vs 语义匹配）
- 对高频常用问法建立"默认理解"（如"GMV"默认含税不含退款）
- 澄清问题措辞优化（让非技术用户也能看懂选项）

---

### Day 28 — DEVLOG + 词典发布

**主任务：**
1. DEVLOG 记录歧义消解的设计决策
2. 把 `metric_dictionary.yaml` 整理成可独立使用的开源文件
3. 把 `semantic_model.yaml` 和 `metric_dictionary.yaml` 的职责区别写清楚
3. 博客草稿：**《数据 Agent 的指标词典：为什么 AI 会把"活跃用户"理解成三种不同的东西》**

**额外要求：**
- 从 Day 22–28 的记录里提炼 2 个高质量"设计取舍案例"，后面面试时专门讲产品判断

### Week 4 并行升级支线：接入 Olist 公开电商数据

> **定位：** 这是一条并行的数据升级支线，目的是让项目从“手工 seed data 打通主链”升级到“公开电商数据 + 小型分析数仓”。
> **执行原则：** 不替换当前手工演示数据，不打断 Agent 主链；先保留 seed data 跑功能，再逐步让 `GMV / 区域 / 品类` 三条快路径兼容 Olist 加工结果。

#### Day 24.1 — 下载并落地 Olist 原始数据到 raw 层

**主任务：**
- 下载 Kaggle Olist 巴西电商公开数据集
- 在 `demo-data/olist/raw/` 下保留原始 CSV/说明文件
- 建第一版 `raw_olist_*` 导入脚本和建表脚本：
  - `raw_olist_orders`
  - `raw_olist_order_items`
  - `raw_olist_customers`
  - `raw_olist_products`
  - `raw_olist_payments`
  - `raw_olist_reviews`

**业务意义：**
- 证明项目不是只靠手工虚构数据
- 为后面的数仓分层和公开数据讲述打底

#### Day 25.1 — 做最小字段清洗和主键关联校验

**主任务：**
- 校验订单、商品、用户、支付四条主链是否能串起来
- 输出一份 `raw_data_quality_report.md`
- 记录：
  - 缺失值
  - 主键重复
  - 关联缺口
  - 明显脏字段

**业务意义：**
- 后面 Agent 看到的不是“原始脏表”，而是经过最小可信校验的数据

#### Day 26.1 — 从 Olist 加工第一版 dwd 明细层

**主任务：**
- 加工：
  - `dwd_orders`
  - `dwd_order_items`
- 统一最基本的分析口径：
  - 订单时间
  - 支付金额
  - 商品明细
  - 用户关联

**业务意义：**
- 让 GMV、订单量、品类结构分析开始有稳定明细底座

#### Day 27.1 — 补 dim 维表和中文业务映射

**主任务：**
- 加工：
  - `dim_products`
  - `dim_regions`
- 额外补齐：
  - 巴西州/城市到“大区”映射（为了对齐“华东/华南”式区域分析表达）
  - 类目中文解释或归并层级

**注意：**
- 这一步不是追求“完全忠实还原 Olist”
- 而是把公开数据加工成更适合“内部运营分析 Agent”使用的业务表达

#### Day 28.1 — 生成第一版 ads 聚合层

**主任务：**
- 生成：
  - `ads_daily_core_metrics`
  - `ads_region_daily`
  - `ads_category_daily`
- 让高频标准问题优先读 ads，而不是每次从明细重算

**业务意义：**
- `昨天 GMV 多少`
- `哪个区域更差`
- `品类排行`
  
这三类快路径开始能逐步迁到公开数据加工结果上

#### Day 29.1 — 让快路径 Tool 兼容 Olist 加工表

**主任务：**
- 先迁三条最稳定的快路径：
  - `GmvQueryTool`
  - `RegionPerformanceQueryTool`
  - `CategoryRankTool`
- 兼容读取 Olist 加工出的 ads 表
- 保留当前 seed data 兜底，不一次性全切

**业务意义：**
- 主链继续稳定
- 数据可信度明显提升
- 面试时可以明确说：
  - 我先用手工 seed data 打通 Agent 主链
  - 再把 Olist 公开数据接入 raw/dwd/dim/ads，逐步迁快路径

---

## Week 5：经验加速（Days 29–35）

> **目标：** 让重复分析任务越来越快。FastIntent 命中时，响应从 5–8 秒降到 < 1 秒。

---

### Day 29 — 读 learning/ 模块

**目标文件：** `extensions/learning/`

**读懂的问题：**
1. 经验自动学习在什么时机触发（对话结束后异步？）
2. 学习提取的 Prompt 是什么？LLM 怎么从对话中抽取 Experience？
3. 新学到的 Experience 需要审核吗？
4. 能不能完全手动写 Experience 而不依赖自动学习？

---

### Day 30 — 手写 10 个电商 TOOL 类 Experience

**主任务：** 针对电商运营最高频的分析任务，手写执行模板。
这 10 个模板直接对应阿里/京东运营团队的真实日常需求，并按三层场景组织：
- 每日运营看数
- 周度分析师归因
- 管理层报告/异常触发

不是把常见问句机械模板化，而是把"分析套路"固化成可复用经验：

```yaml
# 模板示例：单日 GMV 查询
id: daily_gmv_query
type: TOOL
description: "查询某一天的全站或分区域/品类GMV"
intent_patterns:
  - "昨天/今天/{date}的GMV"
  - "今日/昨日成交额"
  - "{date}全站销售额"
parameter_slots:
  - name: date
    extract_from: user_input
    type: date
    default: "yesterday"
  - name: region
    extract_from: user_input
    type: string
    optional: true
code_template: |
  date_range = time_parser.parse("{date}")
  result = gmv_tool.query(
      start_date=date_range.start, end_date=date_range.end,
      region="{region}" if "{region}" else None
  )
  compare = gmv_tool.query(
      start_date=date_range.prev_start, end_date=date_range.prev_end,
      region="{region}" if "{region}" else None
  )
  growth = stats_tool.growth_rate(result['total'], compare['total'])
  reply(f"{date}GMV: ¥{result['total']:,.0f}，环比{growth:+.1f}%，订单{result['order_count']}单")
```

**10 个电商专用模板：**

| # | 模板 ID | 对应场景 | 触发问法示例 |
|---|---------|---------|------------|
| 1 | `daily_gmv_query` | 每日运营看数 | "昨天华东 GMV 多少" |
| 2 | `gmv_period_compare` | 每日运营看数 | "上周 GMV 比上上周涨了多少" |
| 3 | `category_gmv_rank` | 每日运营看数 | "本月哪些品类 GMV 最高，排前5" |
| 4 | `city_gmv_breakdown` | 周度区域分析 | "上月各省份 GMV 分布" |
| 5 | `dau_mau_query` | 每日运营看数 | "昨天 DAU 多少，比上周同天怎样" |
| 6 | `new_user_analysis` | 增长分析 | "本月新用户数，来自哪些渠道" |
| 7 | `conversion_funnel` | 周度分析师归因 | "上周女装品类的转化漏斗" |
| 8 | `refund_rate_query` | 异常排查 | "退款率最高的品类是哪些" |
| 9 | `gmv_root_cause` | 周度分析师归因 | "华东 GMV 为什么跌了" |
| 10 | `daily_full_report` | 管理层/群播日报 | 定时触发，不需要用户问 |

**刻意先不做的模板：**
- 通用 SQL 生成模板
- 面向任意行业的通用 BI 模板
- 纯闲聊解释型模板

这些都不是当前差异化重点。

**额外要求：**
- 每个模板标注属于哪个 analysis space
- 每个模板标注建议走"快路径"还是"深路径"

---

### Day 31 — 集成 FastIntent + 中文 Embedding

**主任务：**
- 确认 FastIntentService 的 Embedding 模型配置方式
- 替换/新增中文语义模型（推荐：`BAAI/bge-small-zh-v1.5`，轻量高效）
- 把 Day 30 的 10 个 Experience 注册到系统
- 新建 `verified_cases.yaml`，先收录 20 个高价值问题，作为 FastIntent 和评测的共同基线
- 给每条 verified case 增加标准字段：
  - `analysis_space`
  - `path_type`
  - `expected_tool_chain`
  - `expected_answer_shape`
  - `target_role`
  - `benchmark_reference`
  - `framework_capabilities`
- 建立第一版 bad case 池：把当前最容易答错的 10 个问题单独收集，后续用于回流修正
- 把“产品定位 / 业务价值 / 和竞品差异”这类问题映射到对应 common experience，作为优先披露候选

**可能踩坑：**
- Embedding 模型加载方式（本地加载 vs 调用 API）
- 向量维度不匹配问题

---

### Day 32 — FastIntent 命中率测试

**主任务：** 构造 50 个测试问题（10 个模板 × 每模板 5 个语义相似变体），并和 `verified_cases.yaml` 交叉验证：

```
模板：daily_gmv_report
变体1: "昨天的GMV是多少"    → 应命中（提取 date=yesterday）
变体2: "昨日成交额"          → 应命中
变体3: "今天卖了多少"        → 应命中（date=today）
变体4: "昨天收入"            → 应命中
变体5: "5月17日的流水"       → 应命中（提取 date=2026-05-17）
```

**评估指标：**
- 命中率（50 题中命中多少）
- 误触发率（不该命中的命中了多少）
- 参数提取准确率
- bad case 占比（失败问题里最主要是哪一类）

**额外要求：**
- 把失败样例按原因分类：
  - 意图识别错
  - 指标理解错
  - 时间解析错
  - Tool 选择错
  - 归因链断掉
  - 回答组织差
- 增加按 `target_role / benchmark_reference / analysis_space` 的命中分组，避免只看整体命中率

---

### Day 33 — 性能基准测试

**主任务：** 对比 3 条路径：
- 快路径：semantic model + ads + verified template
- 深路径：Text2Code 多步执行
- 兜底路径：全量 LLM 推理

| 指标 | 全量 LLM | 快路径 | 深路径 |
|------|---------|--------|--------|
| 平均响应时间 | ? ms | ? ms | ? ms |
| 输入 Token 消耗 | ? | 低 | 中/高 |
| 输出 Token 消耗 | ? | 低 | 中 |
| 结果一致性 | 有随机性 | 高 | 中高 |
| 适用问题 | 泛化问题 | 高频标准问题 | 复杂归因问题 |

**预期产出：** 性能对比表格，这是博客的核心数据

---

### Day 34 — 阈值调优 + 自动学习试验

**主任务：**
- 根据 Day 32 结果调整相似度阈值
- 试验学习模块：执行几次高质量分析后，看 learning 模块是否生成了有效 Experience
- 评估自动生成的 Experience 质量（是否需要人工审核）
- 评估自动学习能否反哺 `verified_cases.yaml`

**自演进闭环要求：**
- 设计一条轻量闭环：
  - 成功分析案例 → verified case 候选
  - verified case 稳定出现 → Experience 候选
  - 高频失败问题 → semantic model / metric dictionary 修正项
- 不做自动无审核发布，第一版采用"系统提建议 + 人工确认沉淀"
- 把 `benchmark_reference / framework_capabilities` 接回 PromptContribution 选择逻辑，确保“业务定位问答”和“高频分析问答”能看到不同的经验侧重

---

### Day 35 — DEVLOG + 博客草稿

博客主题：**《FastIntent：不是缓存，是推理模板复用》**

核心内容：
- FastIntent vs 结果缓存的本质区别（带实测数据）
- 为什么企业分析场景特别适合（高频 + 结构化）
- 性能数据：响应时间从 5s 降到 0.3s

**额外要求：**
- 新增一份 `evaluation_summary.md` 草稿，统一沉淀：
  - 命中率
  - 响应时间
  - bad case 分类
  - 当前最值得修的前 5 个问题
- 写一段"系统为什么会变强"说明，明确 verified cases / Experience / semantic model 三者关系
- 增加一段“数据回流闭环”说明：
  - 评测数据怎么收集
  - bad case 怎么进入修复队列
  - 修复后如何更新 verified cases / semantic model

---

## Week 6：主动行动（Days 36–42）

> **目标：** 让 Agent 从"被动回答"变成"主动发现并通知"。
> 这是瓴羊 VP 说的"行业最前沿、最未解决的课题"，做出来是核心差异化亮点。

---

### Day 36 — 读 trigger/ 模块

**目标文件：** `extensions/trigger/`

**读懂的问题：**
1. 定时触发器（Cron）怎么配置？用 Spring Scheduler 还是 Quartz？
2. 事件触发器（数据阈值）怎么定义触发条件？
3. 触发后的执行流程和正常对话请求有什么区别？
4. 多个触发器同时触发时有没有排队机制？

**额外目标：**
- 明确拆出两类职责：
  - Data Agent：负责查数、下钻、归因、解释
  - Operations Agent：负责监控、阈值判断、定时触发、推送通知

---

### Day 37 — 实现定时日报

**主任务：** 每天早上 8:00 自动生成昨日业务日报：

```yaml
trigger:
  id: daily_morning_report
  type: CRON
  cron: "0 8 * * *"
  action:
    experience_id: daily_full_report
    reply_channel: feishu_group
```

**日报内容：**
- 昨日 GMV（环比前日、同比去年）
- 订单量、客单价
- DAU（环比）
- 退款率
- 异常预警（如果有）

**预期产出：** 本地运行，定时任务触发并生成报告文本

---

### Day 38 — 实现电商异常检测触发

**背景：** 阿里/京东/拼多多的运营团队最怕的就是"GMV 跌了很久才发现"。
现有产品（Quick BI、京东商智）的预警是固定阈值规则，我们的 Agent 在触发后还能自动归因。

**主任务：** 实现 3 类电商常见异常的自动检测 + 归因链：

**异常类型 A：GMV 环比下跌预警（最高优先级）**
```
检测：每小时对比当前小时 GMV vs 上周同时段 GMV
阈值：下跌 > 15%（参考阿里运营团队惯例，非大促期）
触发后的归因链（Text2Code 多步执行）：
  Step 1. 按区域拆分 GMV，定位是哪几个省/城市跌了
  Step 2. 查这些区域的转化率变化（是流量问题还是转化问题）
  Step 3. 查退款率变化（是否有质量投诉爆发）
  Step 4. 查是否有大促结束 / 竞对有大促（关联 promotions 表）
  Step 5. 生成归因报告：主因 + 次因 + 建议动作
  Step 6. 推送飞书，@区域运营负责人
```

**异常类型 B：退款率突增预警（品控风险）**
```
检测：每天凌晨跑前日退款率 vs 过去7日均值
阈值：
  服装类退款率 > 20%（行业基准 ~12%）
  3C类退款率 > 8%（行业基准 ~3%）
  食品类退款率 > 5%（行业基准 ~2%）
触发后的归因链：
  Step 1. 按品类拆分退款率，找到异常品类
  Step 2. 按商家拆分，找到问题集中在哪些店铺
  Step 3. 拉退款原因分布（质量问题/描述不符/物流损坏等）
  Step 4. 推送给品控/类目运营
```

**异常类型 C：DAU 异常波动预警（流量监控）**
```
检测：每天上午 10:00 对比当日 9:00 DAU vs 昨日同时段
阈值：跌幅 > 10% 或涨幅 > 50%（两个方向都是异常）
触发后的归因链：
  Step 1. 按渠道拆分流量来源（自然流量/付费流量/推送）
  Step 2. 查服务器/APP 是否有故障记录（暂时用 page_views 异常代替）
  Step 3. 查是否有热点事件/活动推送
  Step 4. 推送给增长团队
```

**实现要点：**
```yaml
# 3 个 Trigger 配置
triggers:
  - id: gmv_anomaly
    type: CRON
    cron: "0 * * * *"      # 每小时
    threshold: -0.15
    agent: data_agent
    experience: anomaly_root_cause
    reply_channel: feishu_ops_group

  - id: refund_rate_anomaly
    type: CRON
    cron: "0 6 * * *"      # 每天凌晨6点
    threshold_by_category: true
    agent: data_agent
    reply_channel: feishu_quality_group

  - id: dau_anomaly
    type: CRON
    cron: "0 10 * * *"     # 每天上午10点
    threshold_up: 0.5
    threshold_down: -0.1
    agent: data_agent
    reply_channel: feishu_growth_group
```

**可能踩坑：**
- 归因链条复杂（5步+），LLM 一次性生成代码太长 → 拆成多步执行，每步结果传入下一步
- 周末/节假日的基准值不能用工作日数据 → 要区分日期类型做基准修正
- 大促期间阈值要动态调整（大促期 GMV 大幅波动是正常的）→ 关联 promotions 表判断当前是否大促期
- 防止同一异常在 1 小时内重复推送（幂等保护）

---

### Day 39 — 接入飞书 Webhook

**主任务：**
- 读 `reply/` 模块的飞书适配器
- 配置飞书 Bot Webhook URL（本地测试用）
- 实现飞书消息卡片格式：

```
📊 昨日业务日报 (2026-05-17)

GMV：¥3,240万  ↑ 8.3% 环比
订单量：12,840  ↑ 5.1%
客单价：¥252   ↑ 3.0%
DAU：284,000   ↓ 2.1%

⚠️ 异常：上海区域GMV下跌 23%，已触发归因分析
```

**部署要求：**
- Webhook URL 从环境变量读取
- 预留服务器公网部署后的回调/访问方式说明

**RAG 增强预留：**
- 预留一类"非结构化业务知识"输入来源：
  - 指标说明文档
  - 活动规则文档
  - 历史周报模板
- 明确后面归因时不仅查结构化数据，还能补充业务知识上下文

**big data / RAG 结合要求：**
- 明确项目后续支持“结构化数仓 + 非结构化业务知识”的混合分析
- 在面试表达中把它定义成“数据分析 Agent 的混合检索增强”，不是普通知识库问答

---

### Day 40 — 端到端测试：异常 → 飞书推送

**主任务：** 构造异常数据场景，走完完整链路：

```
人工修改演示数据（让某城市 GMV 下跌 30%）
→ 触发异常检测
→ Agent 自动归因分析
→ 飞书收到消息卡片（含图表）
```

**预期产出：** 全链路 GIF 录屏（这是 Demo 的核心亮点片段）

---

### Day 41 — Bug 修复 + GitHub Issue/PR

**主任务：**
- 整理 Week 6 发现的 AssistantAgent 框架层 Bug（不是业务层）
- 至少开一个 Issue（描述问题、复现步骤、影响范围）
- 如果 Bug 不复杂，直接提 PR（修复 + 测试 + 说明）

**注：** 有 PR 记录是简历和博客的重要加分项

---

### Day 42 — DEVLOG + 博客草稿

博客主题：**《从洞察到行动：数据分析 Agent 的最后一公里》**

核心内容：
- 为什么现有产品止步于"可决策"而不是"可行动"（引用瓴羊 VP 原话，有出处）
- 事件驱动 + 代码执行 + 多渠道推送的技术组合
- 演示链路截图

**额外要求：**
- 复盘 Week 6 所有触发链路问题，至少沉淀 3 条可用于面试的"线上系统问题处理案例"
- 写一段 Data Agent / Operations Agent 的产品边界解释，避免面试时只讲成技术拆层

---

## Week 7：打磨收尾（Days 43–49）

> **目标：** 让项目可以对外展示，建立面试时的"现场 Demo"能力。
> 同时开始补齐内部 Agent 真正上线前最关键的两类能力：鲁棒性和安全边界。

---

### Day 43 — 分析溯源展示

**主任务：** Agent 回复附加"分析过程"：

```
✅ 结论：1月北京GMV 3,240万，环比 +8.3%

📋 分析过程
├── Step 1: 查询1月北京GMV → 32,400,000元
├── Step 2: 查询12月北京GMV → 29,900,000元
└── Step 3: 计算环比 → (32400000-29900000)/29900000 = 8.36%

💻 执行代码（展开）
  bj_jan = gmv_tool.query(date="2024-01", region="北京")
  bj_dec = gmv_tool.query(date="2023-12", region="北京")
  growth = (bj_jan - bj_dec) / bj_dec
```

---

### Day 44 — OpenTelemetry 追踪集成

**主任务：**
- 使用 `observation/` 模块生成分析请求的 Trace
- 配置 Jaeger 本地实例，可视化查看调用链
- 把 Trace ID 附在 Agent 回复中
- 为快路径、深路径、触发路径分别打 Trace 标签，后续方便定位慢请求和异常链路

**预期产出：** Jaeger UI 截图，展示一次复杂多步分析的完整 Trace

---

### Day 44.5 — 安全边界设计

**主任务：**
- 设计并实现 `ToolAccessPolicy`：
  - 白名单 Tool 才允许被代码执行调用
  - 不同 analysis space 只能访问各自允许的 Tool
- 设计只读数据访问约束：
  - 默认禁止更新/删除类 SQL
  - Connector 层做只读校验，不把约束只放在 Prompt 里
- 设计 Prompt Injection / 越权问数的最小防线：
  - 拒绝"忽略之前规则""打印系统提示词""直接访问所有表"之类输入
  - 对高风险输入打安全日志

**预期产出：**
- 一份 `SECURITY.md` 草稿
- 一份最小可用的 Tool 白名单 / SQL 只读校验实现方案

**额外要求：**
- 为每个 Tool 补一份标准 schema：
  - 输入参数
  - 输出结构
  - 业务口径
  - 风险级别
  - 所属 analysis space
  - 是否允许快路径调用
  - 是否允许 trigger 调用
- 增加 Tool 审计日志设计：记录谁在什么问题下调用了哪些 Tool

---

### Day 45 — Demo UI（Streamlit）

**主任务：** 用 Streamlit 搭一个 Web 界面：

```
侧边栏：Analysis Space 切换 / 数据源配置 / Experience 列表 / Session 历史
主区域：对话输入框 + 回复展示（支持图表、代码块、分析步骤）
分析轨迹栏：展示当前问题走的是快路径还是深路径，列出 Tool 链和中间结论
底部状态栏：响应时间 / FastIntent 是否命中 / Token 消耗
```

Streamlit 通过 HTTP API 调用 AssistantAgent，保持独立不耦合

**部署视角要求：**
- UI 和后端 API 端口分离，避免后面上服务器时重新拆结构
- 明确是否允许 UI 与后端同机部署（第一版允许）

**额外要求：**
- 增加 3 种输出视图切换：
  - 管理层摘要版
  - 运营结论版
  - 分析师详细版
- 同一结果至少支持：文本摘要 + 图表 + 可展开分析步骤

---

### Day 46 — 演示数据集入库

**主任务：**
- 下载 Olist 数据集并清洗
- 导入 MySQL（脚本放 `demo-data/init.sql`）
- 验证所有 Tool 在该数据集上正常工作
- 加一轮安全回归：
  - 恶意自然语言输入不会越过 Tool 白名单
  - 直接让 Agent "删表/改数据" 时会被拒绝
  - analysis space 切换后不会串用不该暴露的 Tool

---

### Day 46.5 — 鲁棒性与降级机制

**主任务：**
- 设计 `AnalysisFallbackPolicy`：
  - 快路径失败时是否自动切深路径
  - 深路径超时/报错时如何降级成只返回部分结果
- 增加最小可用缓存：
  - 高频日报类问题缓存最近一次结果
  - 指标词典、semantic model、verified cases 做内存缓存
- 增加并发与超时保护：
  - 同一 Session 内限制并行归因任务数
  - 深路径单次执行超时后自动中断并给出可解释错误
- 为 Trigger 任务设计幂等保护：
  - 同一异常窗口避免重复推送

**预期产出：**
- 一份 `ROBUSTNESS.md` 草稿
- 快路径/深路径/trigger 三类降级策略文档

**额外要求：**
- 增加产品成功指标定义，至少包括：
  - 高频问题平均响应时间
  - 快路径命中率
  - 深路径成功率
  - 歧义澄清准确率
  - 复杂归因平均耗时
  - Trigger 防重复推送效果
- 这些指标统一收口到 `evaluation_summary.md`
- 补一条“数据侧成功指标”：
  - ads 快照覆盖了多少高频问题
  - 深度问题落到 dwd 下钻的比例
  - 非结构化知识补充命中的典型场景

---

### Day 47 — 录制 5 个电商场景 Demo GIF

> **录制原则：** 每个 Demo 开头用字幕标注"这是什么场景 / 对应哪类用户 / 现有工具为什么做不到"，
> 让看 GitHub 的面试官一眼懂业务价值，不只是技术演示。

| # | Demo | 时长 | 场景说明 | 对标差异 |
|---|------|------|---------|---------|
| 1 | **电商多步归因** | 2min | "华东上周 GMV 跌了，帮我查一下原因" → 自动拆城市→查转化率→看退款率→出归因报告 | 京东 JoyAgent DataAgent（Text2SQL）只能出一条 SQL，做不了这个归因链 |
| 2 | **多轮追问** | 2min | Q1"上月北京GMV" → Q2"那上海呢" → Q3"哪个涨得快" → Q4"涨幅来自哪些品类" | Snowflake Cortex 官方文档：完全无状态，每次需要重新说背景 |
| 3 | **指标歧义消解** | 1min | "活跃用户这周怎么样" → 触发 DAU/MAU 澄清选择 → 执行分析 | 阿里 Quick BI / Coze：AI 直接猜一个，结果可能完全不对 |
| 4 | **FastIntent 速度对比** | 1min | 同一问题"昨天全站GMV"：第一次 5.2s（LLM推理），之后 0.3s（FastIntent命中）；屏幕并排展示时间差 | 所有 NL2SQL 产品每次都是全量推理，没有推理模板复用 |
| 5 | **主动监控全链路** | 2.5min | 人工让上海 GMV 跌 30% → Agent 自动发现 → 归因分析（5步代码执行）→ 飞书收到卡片（含图表和建议动作）| 阿里 Quick BI / FineBI：有预警但只是发数字，没有自动归因和建议 |

**录制技巧：**
- Demo 1/5 用 Streamlit UI 录（展示完整分析轨迹栏）
- Demo 4 用分屏录制（左边第一次，右边 FastIntent 命中）
- 每个 GIF 控制在 30s 内用于 README，完整版视频用于面试演示
- 字幕用黑底白字，标清"vs JoyAgent DataAgent"或"vs Snowflake Cortex"

---

### Day 48 — README 整理

**README 结构：**
```markdown
# 数据分析 Agent（基于 AssistantAgent）

> 一句话介绍

## 为什么做这个（痛点背景，3段，有数据）
## 核心能力（6条，每条一句话 + GIF）
## 快速开始（5行命令跑起来）
## 部署到服务器（Docker / 环境变量 / 健康检查）
## 技术架构（架构图）
## 数据底座（raw / dwd / dim / ads + 日级快照）
## 能力对比（vs Coze / Dify / Databricks Genie）
## 安全与鲁棒性（只读执行、Tool 白名单、降级策略）
## 评测与自演进（verified cases、bad cases、经验沉淀闭环）
## 开发日志（链接 DEVLOG.md）
## 相关博客（链接）
```

**额外要求：**
- 在 README 里补一张 Agent 编排图：
  - 输入
  - 意图识别
  - analysis space
  - 快/深路径路由
  - Tool 执行
  - Trace / 评测 / 回流
- 明确说明这个项目不是普通问数工具，而是带评测闭环的业务分析 Agent
- 补一张“数据加工与分析分层图”，清楚展示：
  - raw 层保留原始数据
  - dwd/dim 层支持归因分析
  - ads 层支撑日报/周报/高频查询
  - 非结构化知识层补充指标/活动规则语义

---

### Day 49 — PR 提交 + 社区互动

**主任务：**
- 提交至少 1 个有质量的 PR（Bug 修复或文档改进）
- 在 AssistantAgent GitHub Discussions 分享项目
- 关注 PR Review 意见，及时回应

---

## Week 8：输出沉淀 + 部署交付（Days 50–56）

> **目标：** 把做的东西变成面试资产和技术影响力，并完成一版能在 Linux 服务器上运行的部署交付。
> 不只讲业务价值，也要能证明这是一个接近内部可用形态的系统，而不是脆弱 demo。

---

### Day 50 — 发布博客 1

**《NL2SQL vs Text2Code：我用 GPT-4 只解出 6% 的企业 SQL 题》**
核心数据：Spider 2.0 / BIRD 基准 + Text2Code 的绕过逻辑

---

### Day 51 — 发布博客 2

**《多轮数据分析对话：我是怎么让 AI 记住我们聊过什么的》**

---

### Day 52 — 发布博客 3

**《FastIntent：不是缓存，是推理模板——重复分析从 5s 到 0.3s》**

---

### Day 53 — 发布博客 4

**《从洞察到行动：一个数据异常是如何自动触发飞书通知的》**

---

### Day 54 — 生产配置整理

**主任务：**
- 新建 `application-prod.yml`，明确生产环境配置项：
  - 端口
  - 日志目录
  - 数据库连接
  - DashScope / LLM API Key
  - 飞书 Webhook
  - 超时 / 限流 / 缓存策略
- 约定配置优先级：
  - 环境变量 > `application-prod.yml` 默认值
- 产出 `.env.example` 或配置模板文档

**预期产出：**
- 一份可复制到服务器的最小生产配置说明

**额外要求：**
- 增加 AI Coding 协作记录：
  - 哪些模块由自己主导设计
  - 哪些环节借助 AI Coding 提速
  - AI 帮了什么，哪些关键边界仍然必须人工拍板
- 为后续面试准备一段"我如何用 AI Coding 加速完成这个项目"说明

---

### Day 55 — Docker 化与单机部署编排

**主任务：**
- 编写 `Dockerfile`：
  - 基于 Java 17 运行时
  - 拷贝打包后的 Spring Boot JAR
  - 暴露后端端口
- 编写 `docker-compose.yml`：
  - app 服务
  - 可选 mysql 服务（如果演示环境同机）
  - 可选 streamlit/ui 服务
- 增加健康检查：
  - 应用启动成功探针
  - 数据库连通性检查

**原则：**
- 第一版以单机 Docker 部署为准，不强行上 K8s
- 先保证“能稳定上线演示服务器”，再考虑更重的编排

**预期产出：**
- 本地可用 `docker compose up` 拉起完整演示环境

---

### Day 56 — 服务器上线演练 + 面试讲解材料整理

**主任务：**
- 在一台 Linux 服务器上完成上线演练：
  - 上传镜像 / 代码
  - 配置环境变量
  - 启动服务
  - 验证 API / UI / 飞书推送
- 补齐运维脚本：
  - `start-prod.sh`
  - `stop-prod.sh`
  - `logs.sh`
- 做一轮上线后检查清单：
  - 健康检查通过
  - 日志正常落盘
  - 数据库可连
  - 快路径 / 深路径各跑 1 个用例
  - Trigger 能触发 1 次日报或异常推送
- 把上线过程中遇到的配置、依赖、网络、权限、日志问题逐条写进 `DEVLOG.md`

**面试材料同时补充：**

**3 分钟版（自我介绍）：**
> "我做了一个面向电商平台的运营数据分析 Agent，对标阿里/京东内部的分析工具，
> 基于阿里开源的 AssistantAgent 框架。
>
> 做之前我研究了京东 2025 年 9 月刚开源的 JoyAgent DataAgent——
> 那是目前开源里做得最认真的数据分析 Agent，但它走的是 Text2SQL 路线。
> 我选的是 Text2Code 路线，两条路的核心差别在于：
> SQL 只能做单次查询，而代码可以写多步推理——
> 比如'华东 GMV 为什么跌了'这个问题，正确答案需要先拆城市、
> 再查转化率、再看退款率，最后对比活动日历，这是 SQL 做不了的。
>
> 具体做了四件事：
> 第一，Text2Code 多步归因，GraalVM 沙箱执行 Python 代码，中间结果可以传递；
> 第二，会话记忆，Session 上下文持久化，支持'那上海呢'这类追问，
>   Snowflake Cortex 官方文档明确说他们做不到这个；
> 第三，电商指标词典，处理'活跃用户'/'转化率'这类阿里/京东/拼多多口径不统一的问题；
> 第四，主动监控，GMV 跌超阈值时自动触发归因链，推送飞书，
>   这个方向瓴羊 VP 在 InfoQ 说是行业最前沿的未解决课题，现有 BI 工具基本做不到。
>
> 最后部署到了服务器，不只是本地 Demo。
> 过程中给 AssistantAgent 框架提了 Issue 和 PR。"

**与 JoyAgent DataAgent 的面试对比话术：**

> 面试官可能会问："京东不是也做了 DataAgent 吗，你和他们有什么区别？"

标准回答：
```
"对，我专门研究过京东的 JoyAgent DataAgent，GAIA 榜准确率 67%，是目前开源里做得最认真的。
 但它是 Text2SQL 路线，我是 Text2Code 路线，有三个实质差别：

 第一，多步推理。电商里的'为什么 GMV 跌了'需要 4-5 步分析才能得出结论，
 SQL 是单次查询，做不了；Python 代码可以 for loop + 条件判断，自然支持。

 第二，会话记忆。用户问完'北京'再问'那上海呢'，JoyAgent 是无状态的，
 每次请求独立；我在 Session 层做了上下文持久化，支持真正的多轮对话。

 第三，主动触发。JoyAgent 是被动问答产品，没有监控触发能力；
 我用 AssistantAgent 的 Trigger 模块做了异常检测自动归因。

 我不是说我比京东强，是技术路径选择不同，解决的问题场景也不同。"
```

**技术深度版提纲：**
- 框架选型理由（为什么 AssistantAgent 而不是 LangChain/AutoGen/JoyAgent 框架）
- GraalVM 沙箱执行机制（安全隔离、超时控制）
- Text2Code vs Text2SQL 实测差异（用多步归因 Demo 讲）
- 多轮对话的状态设计（Session 结构、上下文压缩）
- 电商指标词典设计（阿里/京东/拼多多口径差异的处理）
- FastIntent 语义匹配（Embedding 选型、阈值调优过程）
- 主动监控的触发机制（事件驱动 vs 定时轮询的权衡、大促期阈值动态调整）
- 安全边界设计（Tool 白名单、只读约束、Prompt Injection 最小防线）
- 鲁棒性设计（快/深路径降级、缓存、超时、幂等、防重复推送）
- 服务器部署设计（为什么先选单机 Docker、配置分层、健康检查）
- DEVLOG 里挑 3 个最有代表性的踩坑（有具体代码和解法）

**额外要求：**
- 为 3 类岗位各写一版项目介绍：
  - AI 应用开发岗
  - 智能体 / 数据智能平台岗
  - 业务产品技术岗
- 每版都明确：
  - 这个项目证明了什么能力
  - 哪些点重点讲
  - 哪些点弱化不讲
- 单独整理一页"开源参考映射"：
  - OpenDevin / OpenHands 类借鉴了什么
  - Data Agent / GenBI 类借鉴了什么
  - 为什么最后收敛成电商运营分析 Agent
- 单独整理一段“我在这个项目里体现了哪些 big data 能力”：
  - 数仓分层建模
  - 数据清洗与快照生成
  - 调度与日报任务
  - 混合检索增强
  - 评测数据回流
- 注意讲法：强调这些能力是为 Agent 产品服务，而不是单独炫技术栈

---

### Day 57 — 压力测试 + Demo 彩排

**主任务：**
- 准备 10 道面试可能追问的技术细节问题，写出答案
- 重新演练现场 Demo（5 分钟内演示 3 个核心场景）
- 检查 GitHub 主页是否整洁（置顶仓库，README 有图有 GIF）
- 增加一轮可靠性压测：
  - 连续 30 次高频问数是否稳定
  - 5 次复杂归因里超时/失败比例多少
  - Trigger 连跑一小时是否出现重复推送或状态脏写
- 增加一轮安全测试：
  - 10 条越权 / 注入 / 删改数据类输入全部被拦截

**预期产出：**
- 一张"业务能力 / 性能 / 安全 / 鲁棒性"四象限自评表，作为最终项目答辩材料

---

### Day 58 — Release v1.0 + 收尾

**主任务：**
- 打 `v1.0.0` Tag
- 写 Release Notes（功能列表 + 技术亮点 + 博客链接）
- 在 Release Notes 里补一段部署说明（运行环境、启动命令、核心环境变量）
- 最终 DEVLOG 整理
- 发帖分享（朋友圈 / 掘金 / 即刻 + GitHub 链接）

---

## 每日工作节奏建议

```
代码日（Week 2–6 主基调）
  30min  复习 DEVLOG，确认今天要读懂的问题和要解决的业务问题
  60min  读源码 / 配置 / 现有实现，先写出“框架原来怎么做”
  2-3h   主任务编码，只补框架覆盖不了的最小自定义逻辑
  30min  测试 + 写 DEVLOG（源码答案、业务影响、框架边界、面试可讲点）
  晚上   复盘今天的设计取舍，准备明天要问源码的 1-3 个问题

源码阅读日（Week 1 + 各周读源码天）
  专注读代码，画图，做笔记，不要强行写业务代码
  当天至少沉淀 1 条"源码理解记录"到 DEVLOG，并明确它如何影响本项目设计

博客写作（穿插周末）
  草稿先写完，以"能发出去"为标准，不要追求完美
```

**DEVLOG 每日最小模板：**

```markdown
### [YYYY-MM-DD] 标题

**今天要读懂的问题：**
1. 
2. 

**源码里的答案：**
- 文件：
- 关键类 / 方法：
- 我的理解：

**业务问题映射：**
- 这个机制解决了电商分析 Agent 的什么问题？

**框架支持 / 框架边界：**
- 已支持：
- 不足或未确认：

**今天做了什么：**
- 

**验证结果：**
- 

**设计取舍：**
- 

**后续可讲价值（面试/博客）：**
- 
```

**每日完成定义：**
- 只写代码、不回答当天读懂问题，不算完成
- 只写源码答案、不落到业务问题，不算完成
- 只写“用了什么技术”，不写“为什么这样设计”，不算完成
- 发现框架能力边界时必须明确写出，不允许用自定义逻辑悄悄绕过去

---

## 每周 Checkpoint 自评

| Week | 自评问题 |
|------|---------|
| W1 | 能流畅讲解 GraalCodeExecutor 的执行链路吗？ |
| W2 | Demo 1（多步推理）能跑通并有截图/GIF 吗？ |
| W3 | 5 个多轮对话场景全通过了吗？ |
| W4 | 歧义触发准确率 > 80% 了吗？ |
| W5 | FastIntent 命中 30/50 题以上？有性能对比数据？ |
| W6 | 飞书能收到异常预警吗？有完整链路 GIF？ |
| W7 | README 有图，5 分钟能演示 3 个场景吗？ |
| W8 | 3 篇博客发出去了吗？PR 提了吗？ |

---

## 项目最终 4 条主线

这 4 条是后面写简历、讲项目、做面试回答时最该反复强调的内容：

1. **产品问题与业务价值**
   这是一个面向电商内部运营场景的数据分析 Agent，核心解决业务提需求到分析结论链路长、重复分析人效低的问题。

2. **核心 Agent 设计与差异化路线**
   项目以 Text2Code 替代纯 NL2SQL，围绕复杂归因构建多轮上下文、语义层、快/深路径和 Tool 化分析动作，让 Agent 不只是问数，而是能完成连续分析。

3. **数据底座与上线闭环**
   项目按真实数据产品思路补齐 raw/dwd/dim/ads 分层、指标快照、混合分析、触发推送与服务器部署，使其从本地 Demo 走向可演示、可评测、可上线。

4. **评测驱动与轻量自演进**
   系统不是回答一次就结束，而是通过 verified cases、bad cases、evaluation summary、Experience 沉淀和 semantic model 修正，让每次成功/失败分析都能反哺系统，逐步形成可复用、可迭代的业务分析能力。

---

*最后更新：2026-05-18*
