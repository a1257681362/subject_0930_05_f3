# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## 植物工厂种植批次台账

在骨架上新增植物工厂生产台账（包 `com.evops.plant`），先跑通：建档 → 播种 → 养护 → 采收 → 质检；销售结算后续再接，本阶段不提供结算接口。

业务表（见 `schema.sql`，版本 `pf-ledger-1`）：

- `t_pf_greenhouse` 温室：光源/光周期/PPFD、营养液循环模式/EC/pH/循环间隔
- `t_pf_zone` 设备分区（隶属温室）
- `t_pf_variety` 品种、`t_pf_tray` 播种盘
- `t_pf_batch` 种植批次：关联品种、播种盘、计划采收日；批次号唯一（序列 `seq_pf_batch_no` + `uk_pf_batch_no`）
- `t_pf_batch_relocation` 转棚留痕（只追加，含起止棚位/原因/操作者）
- `t_pf_care_log` 养护记录（LIGHT 光照 / NUTRIENT 营养液循环 / EQUIPMENT 设备）
- `t_pf_harvest_order` 采收单：等级（A/B/C）、重量、去向；采收单号唯一（`seq_pf_harvest_no` + `uk_pf_harvest_no`）
- `t_pf_quality_check` 质检：一张采收单至多一条（`uk_pf_qc_harvest`），结论回写采收单
- `t_pf_operator` 操作者：0=系统（定时任务兜底），保证任何记录都不丢操作者

接口（HTTP Basic，账号 bootstrap/bootstrap；健康检查除外）：

- 建档：`POST/GET /api/pf/greenhouses`、`POST/GET /api/pf/greenhouses/{id}/zones`、`/api/pf/varieties`、`/api/pf/trays`
- 播种：`POST /api/pf/batches`；养护：`POST /api/pf/batches/{id}/care-logs`；转棚：`POST /api/pf/batches/{id}/relocations`
- 采收：`POST /api/pf/harvests/batches/{batchId}`；质检：`POST /api/pf/harvests/{id}/quality-check`
- 起步核对：`POST /api/pf/startup/run`（手动触发定时任务）、`GET /api/pf/startup/batch`

定时任务 `StartupBatchJob`：启动即执行、之后每 6 小时巡检，幂等确保"植物工厂种植批次台账起步"批次（固定编号 `PFB-STARTUP-0001`，`startup_flag=Y`）存在且仅一条。重启/多次触发只回查跳过，不多写记录；任务线程无登录主体时操作者兜底为系统(0)。

## 植物光配方

按品种+生长阶段配置光谱比例（红/蓝/远红，合计 100）、亮度（PPFD）与光时段，定时任务按棚区整点下发到灯控设备（版本 `pf-light-1`）。

业务表：

- `t_pf_light_recipe` 配方版本主表：同一 `recipe_code` 逐版递增，任一时间至多一个 PUBLISHED
- `t_pf_light_recipe_stage` 阶段明细：`day_from <= 播种后天数 < day_to`（`day_to` 空=开放段）
- `t_pf_light_device` 灯控设备（隶属分区）
- `t_pf_light_dispatch` 整点下发单（历史快照）：参数全量冗余，每棚区每生效整点至多一单，仅参数切换时建单
- `t_pf_light_dispatch_device` 设备执行项（重试队列）：FAILED/RETRYING 到点重发，超 12 次置 DEAD
- `t_pf_light_override` 人工临时调整：**结束时间与审批人必填**，窗口外自动失效

统一时间口径（`LightTimeRules`，全部左闭右开 `[start, end)`，由 `LightTimeRulesTest` 锁定）：

- 阶段归属：播种当天为第 0 天，临界日归下一阶段
- 整点生效：生效时刻 = 不小于当前时间的最近整点（10:00:00.000 当拍生效，10:00:00.001 归 11:00）
- 光时段：`light_on <= t < light_off` 开灯，支持跨夜（22:00–06:00）
- 调整窗口：`start_time <= t < end_time`，到点自动失效；窗口内不含整点的调整直接拒绝（永不生效）

核心规则：

- **新版本只影响未开始阶段**：批次当前阶段已有下发记录即钉住其配方版本，参数从不可变阶段表重读；未开始阶段才取当前 PUBLISHED 版本
- **历史执行保留快照**：下发单冗余全部参数，配方后续变更不影响已生成记录；生效前的 PENDING 单允许随最新发布/调整刷新，生效后留痕不可改
- **人工临时调整**：覆盖参数位但保留配方钉版，结束/撤销后自动回到原配方版本；同棚区同时只允许一条生效中调整
- 定时任务 `LightDispatchJob` 每 5 分钟整（`0 0/5 * * * ?`）执行：建单（幂等）→ 到点下发/重试 → 过期调整归置；`effective_at <= now` 才下发，切换只在整点生效

接口：

- 配方：`POST/GET /api/pf/light-recipes`、`GET /{id}/stages`、`POST /{id}/publish`（旧发布版自动归档）
- 临时调整：`POST/GET /api/pf/light-overrides`、`POST /{id}/revoke`
- 下发：`GET /api/pf/light-dispatches`、`GET /{id}/devices`、`GET /retry-queue`、`POST /run`（手动触发一拍）

种子数据：V001 奶油生菜两阶段配方（育苗 0–14 天 150PPFD / 生长 14 天起 220PPFD，已发布 v1）；A 区 LA01/LA02、B 区 LB01 正常，LB02 故障（演示重试队列）。设备下发由 `LightDeviceGateway` 模拟（非 ACTIVE 即失败），接真实灯控协议时替换 `SimulatedLightDeviceGateway`。

## 植物营养液

管理营养液配方（氮磷钾盐类 + 微量元素 + 目标电导值 EC/pH），原料按批号入库并管控效期，水箱补加/排放两步审批、分别记账（版本 `pf-nutrient-1`）。

业务表：

- `t_pf_nutrient_material` 原料目录：`compat_group` 兼容组（CALCIUM 钙系 / PHOS_SULFUR 磷硫系 / NEUTRAL 通用）+ 默认母液罐
- `t_pf_nutrient_stock` 原料批号库存：批号唯一、入库量/余额、生产日期/有效期；余额随补加批准原子扣减
- `t_pf_nutrient_recipe` 营养配方版本主表（target_ec/target_ph，同 code 逐版，至多一个 PUBLISHED）
- `t_pf_nutrient_recipe_item` 配方明细（不可变快照：原料、母液罐 A/B/C、每吨剂量克/吨）
- `t_pf_water_tank` 水箱（容量）
- `t_pf_nutrient_dosing` 补加单 + `t_pf_nutrient_dosing_item` 批号级行（物料/批号/有效期/母液罐/实投克数全量快照）
- `t_pf_nutrient_discharge` 排放单（与补加独立单据）
- `t_pf_nutrient_tank_ledger` 水箱台账（只追加：DOSING 正向 / DISCHARGE 负向，余额逐笔滚算）

核心规则：

- **发布前校验兼容性**（`NutrientRules`，由 `NutrientRulesTest` 锁定）：钙系（硝酸钙）与磷硫系（磷酸二氢钾、硫酸镁）**同母液罐会沉淀**，必须分罐；创建草稿与发布两次校验
- **原料过期不能使用**：有效期当天仍可用，次日起过期；批号在**提交**与**审批**两个时刻各校验一次（提交后过期的批号也无法出库）；过期批号也禁止入库
- **两步审批、审批只完成一半时停留待处理**：补加/排放提交后为 PENDING，**不扣库存、不记台账**；批准才扣减批号库存（条件更新原子扣减，余额不足整笔回滚）并写台账，驳回必填原因
- **提交与审批强制两人**：新增审批账号 `approver/approver`（operator#2 营养液审批员），`POST /{id}/approve|reject` 不得由提交人本人调用
- **补加和排放分别记账**：两类单据各自编号（`ND-`/`NX-` + 序列）、各自列表，台账以 change_type 区分正负向
- **历史批次继续引用当时配方版本**：批次首次批准的补加钉住配方版本，之后只能引用同版（已 ARCHIVED 版仍允许历史批次使用）；未钉版的新批次才取当前 PUBLISHED 版
- **可追溯**：批号 → `GET /stocks/{id}/usage` 反查所有补加行；配方版本 → `GET /nutrient-recipes/{id}/dosings`；批次/水箱 → 单据列表；行内快照物料编码/批号/有效期

接口（HTTP Basic；提交/投加用 `bootstrap/bootstrap`，审批用 `approver/approver`）：

- 原料/批号：`POST/GET /api/pf/nutrient-materials`、`POST /{materialId}/stocks`（入库）、`GET /stocks`、`GET /stocks/{id}`、`GET /stocks/{id}/usage`
- 配方：`POST/GET /api/pf/nutrient-recipes`、`GET /{id}/items`、`POST /{id}/publish`、`GET /{id}/dosings`
- 水箱：`POST/GET /api/pf/water-tanks`、`GET /{id}/balance`、`GET /{id}/ledger`
- 补加：`POST /api/pf/nutrient-dosings`、`GET`（可按 tankId/batchId/status 过滤）、`GET /pending`、`GET /{id}/items`、`POST /{id}/approve`、`POST /{id}/reject`
- 排放：`POST /api/pf/nutrient-discharges`、`GET`、`GET /pending`、`POST /{id}/approve`、`POST /{id}/reject`

种子数据：硝酸钙/硝酸钾/磷酸二氢钾/硫酸镁/EDTA 微肥五种原料；硫酸镁含一条 2025 年到期的**过期批号**演示禁用；NR-V001 奶油生菜配方 v1（EC 1.80/pH 6.00，A 罐钙系、B 罐磷硫系，已发布）；WT-A01/WT-B01 水箱；一张固定编号 `ND-DEMO-PENDING-0001` 的**待处理补加单**演示"审批只完成一半"（PENDING，未扣库存未记账）。含运行态列（库存余额、配方/单据状态）的种子统一用 `INSERT ... WHERE NOT EXISTS`，重启不覆盖业务数据；仅不可变主数据（原料、水箱）用 MERGE。

注意：低熵环境（容器/虚拟机）下 `/dev/random` 会阻塞会话 ID 生成导致请求挂起，`mvn spring-boot:run` 已在 pom 中配置 `-Djava.security.egd=file:/dev/./urandom` 规避；生产部署请按环境自行评估。


