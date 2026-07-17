# Order Extra 模板管理接口设计

日期：2026-07-17
模块：wms-cashier
分支：develop

## 背景

票据系统（order-extra）的读写链路已经写完：`OrderExtraController` / `OrderExtraService` /
`OrderExtraMapper` 支持按模板读取和填写订单附加数据，`wms_order_extra_template` 和
`wms_order_extra` 两张表也已加入 `schema.sql`。

但整条链路**跑不通**：`OrderExtraMapper.insertTemplate` 定义了却没有任何调用方——没有管理端
接口，没有 seed SQL，也没有迁移脚本。实际部署后 `wms_order_extra_template` 是空表，导致：

- `GET /order-extra/templates` 返回 `[]`，前端渲染不出任何表单
- `PUT /order/{orderId}/extra/{templateCode}` 在 `findTemplate()` 的 `Assert.notNull` 必然失败，
  返回 400 "template not found"

本设计补上模板管理接口，让 owner 能在线创建和维护模板，打通整条链路。

## 目标

1. Owner 可以创建、修改、停用、重新启用 order-extra 模板
2. 模板 schema 在写入时就被严格校验，而不是等到收银员填单时才暴露问题
3. 历史订单的 `template_version` 快照保持可追溯

## 非目标

- 不引入模板的历史版本回溯（旧版 schema 定义不保留，只保留版本号）
- 不改造现有的错误码写法（见"错误处理"）
- 不收紧 payload 中未声明字段的处理（见"显式决策"）

## 接口

| Method | Path | 权限 | 说明 |
|---|---|---|---|
| `POST` | `/order-extra/templates` | OWNER | 创建模板 |
| `PUT` | `/order-extra/templates/{code}` | OWNER | 改 name/schema，schema 变则 version+1 |
| `DELETE` | `/order-extra/templates/{code}` | OWNER | 停用（enabled=false） |
| `PUT` | `/order-extra/templates/{code}/enabled` | OWNER | 启用/停用切换 |
| `GET` | `/order-extra/templates?includeDisabled=true` | OWNER | 管理页列表，含停用模板 |
| `GET` | `/order-extra/templates` | 任意登录 | 已有接口，只返回 enabled |
| `GET` | `/order-extra/templates/{code}` | 任意登录 | 已有接口，只返回 enabled |

读取接口（不带 `includeDisabled`）保持对任意登录用户开放——收银员填单时必须能拿到模板。
只有 `includeDisabled=true` 时才要求 OWNER。

### 条件式权限的落地方式

`GET /order-extra/templates` 是同一个端点，但权限随 `includeDisabled` 变化，而
`@PreAuthorize` 是方法级注解。采用 SpEL 引用方法参数：

```java
@PreAuthorize("!#includeDisabled or hasRole('OWNER')")
public List<OrderExtraTemplateDto> getTemplates(boolean includeDisabled)
```

`includeDisabled` 缺省为 `false`（`@RequestParam(defaultValue = "false")`），此时表达式短路为
放行，行为与现有接口完全一致。

SpEL 的 `#paramName` 依赖编译期保留参数名（`-parameters`）。`spring-boot-starter-parent`
默认开启该编译参数，但实现时须以一条"非 owner 带 `includeDisabled=true` 被拒"的测试
确认其确实生效——若参数名未保留，表达式会静默求值失败而非报错，导致权限形同虚设。
若确认未生效，退路是改用 `@PreAuthorize("!#p0 or hasRole('OWNER')")` 按位置引用。

### 为什么管理页列表用查询参数而不是 `/templates/all`

`/order-extra/templates/all` 会与已有的 `/order-extra/templates/{code}` 冲突：Spring 让字面量
路径优先于路径变量，因此一个 code 恰好叫 `all` 的模板将永远无法通过 `/{code}` 访问。
查询参数没有这个问题。

### 请求体

`POST /order-extra/templates`：

```json
{
  "code": "invoice",
  "name": "发票信息",
  "schema": {
    "fields": [
      { "key": "invoiceTitle", "label": "发票抬头", "type": "text", "required": true },
      { "key": "payMethod", "label": "支付方式", "type": "select",
        "options": ["现金", "微信", "支付宝"], "required": false }
    ]
  }
}
```

`PUT /order-extra/templates/{code}`：同上但不含 `code`（`code` 由路径提供且不可改）。

`PUT /order-extra/templates/{code}/enabled`：

```json
{ "enabled": true }
```

响应约定：

- `POST` / `PUT /{code}` / `PUT /{code}/enabled` → `ApiRes<OrderExtraTemplateDto>`，
  返回写入后的模板（便于前端拿到最新 version，无需再发一次 GET）
- `DELETE` → `ApiRes<Void>`
- `GET` 系列 → 维持现有签名

## 版本语义

`wms_order_extra` 会快照 `template_version`，因此版本号必须能反映"这条数据是按哪一版 schema 填的"。

- 更新走**同一行 UPDATE**，不插新行（`UNIQUE (group_id, code)` 约束不变）
- **仅当 schema 实际发生变化时** version += 1
- 只改 `name`（如"发票信息"→"增值税发票"）**不**动版本号

"schema 是否变化"的判定：把库中的 `schema_json` 与新提交的 schema 都用
`objectMapper.readTree` 解析为 `JsonNode` 后比较。`JsonNode.equals` 做的是结构化相等比较，
不受键顺序和空白影响，避免格式化差异造成版本号误增。

版本号误增的后果是：历史快照里的版本号跳变，但 schema 其实没差别，追溯时反而误导。

## 不可变性与冲突

- `code` 创建后不可修改——它是 `wms_order_extra` 快照的关联键
- `POST` 一个已存在的 code（无论 enabled 与否）返回 400，提示改用启用接口，
  而不是静默覆盖已有模板

## Schema 校验

模板写入（POST/PUT）时严格校验 schema 结构，不合法直接 400：

- 必须是 `{ "fields": [...] }`
- `fields` 必须是**非空**数组
- 每项的 `key` 非空，且在同一模板内唯一
- `type` 必须属于已支持枚举：`text` / `textarea` / `number` / `boolean` / `date` /
  `datetime` / `select`
- `type` 为 `select` 时必须带**非空** `options` 数组

## Payload 校验的收紧：select options

现状：`validateFieldType` 对 `select` 只检查值是字符串，**不检查值是否在 `options` 内**。
前端下拉框只是 UI 约束，不是安全边界——绕过前端直接调接口传任意字符串即可写入脏数据。

本次收紧：`select` 字段的值必须存在于模板声明的 `options` 中，否则 400。

无兼容性代价：当前生产库中不存在任何模板（正是本设计要解决的阻塞问题），因此不存在
任何已在使用 select 的调用方。等有真实脏数据后再收紧才会有兼容包袱。

## 代码结构

Schema 校验被两处复用——写模板时校验 schema 结构本身，填单时校验 payload 是否符合 schema。
`OrderExtraService` 当前 165 行，直接塞入模板 CRUD 与 schema 校验会奔向 300 行。

因此抽出独立的 `OrderExtraSchemaValidator`（`@Component`）：

- `validateSchema(JsonNode schema)` — 模板写入时调用，规则见"Schema 校验"
- `validatePayload(JsonNode schema, JsonNode payload)` — 从 `OrderExtraService` 的私有方法
  搬迁而来，行为不变，另加 select options 检查

`OrderExtraService` 保留编排职责（权限、group 隔离、版本判定、DTO 转换）。

### Mapper 新增

`OrderExtraMapper` 补充：

- `updateTemplate(OrderExtraTemplate)` — 更新 name / schema_json / version / updated_at
- `updateEnabled(@Param("groupId") int, @Param("code") String, @Param("enabled") boolean)`
- `findTemplateByCode(@Param("groupId") int, @Param("code") String)` — **不过滤 enabled**，
  供更新和创建冲突检查使用（已有的 `findEnabledTemplateByCode` 会过滤，不适用）
- `findTemplates(@Param("groupId") int, @Param("includeDisabled") boolean)`

已有的 `insertTemplate` 终于会有调用方。

### 新增 DTO

`OrderExtraTemplateReq`：`code`（POST 用）、`name`、`schema`（`JsonNode`）。

## 权限

写操作用 `@PreAuthorize("hasRole('OWNER')")` 打在 service 方法上，与 `GroupService` 现有写法
一致。模板是商户级配置，改错影响所有收银员，权限收紧最安全。

Group 隔离沿用现有做法：每个查询都带 `securityContext.currentGroupId()`。

## 错误处理

沿用现有写法：`Assert` 抛 `IllegalArgumentException` → common-lib 的 `GlobalExceptionHandler`
→ `ErrorCode.INVALID_PARAM` → 400。

这与根 CLAUDE.md"错误码必须定义在 SysErrorCode"的约定有出入，但 `OrderService` /
`GroupService` / `AuthorityService` 全部如此。保持局部一致优于在本次改动中单独立规矩；
若要改造应另开独立 PR 统一处理。

## 显式决策：payload 中未声明的字段仍被容忍

`validatePayload` 只遍历 schema 声明的 `fields`，payload 中多余的 key 原样入库。
API.md 现有示例已体现这一行为（PUT 示例 body 含 `customerName` / `serialNo`，
而示例 schema 只声明了 `invoiceTitle` / `taxNo`）。

本次**有意保持**该行为，并将在 API.md 中写明，避免日后被误当作 bug。
这与 select options 的收紧不矛盾：后者约束的是"已声明字段的取值范围"，前者关乎
"是否接受未声明字段"，是两个独立维度。

## 测试

单元测试（mock）：

- `OrderExtraSchemaValidatorTest` — schema 结构校验各拒绝分支；payload 校验各类型分支，
  含 select options 命中与未命中
- `OrderExtraServiceTest`（扩充）— 创建/更新/停用/启用；版本 +1 与不 +1 两条路径；
  重复 code 冲突；非 owner 调用写操作被拒；非 owner 带 `includeDisabled=true` 被拒
  （该用例同时验证 SpEL 参数名解析确实生效，见"条件式权限的落地方式"）

集成测试（TestContainers，`WmsCashierIntegrationTest` 或新建 `OrderExtraIntegrationTest`）：

- jsonb 往返：`schema_json::jsonb` 写入与 `::text` 读回
- `UNIQUE (group_id, code)` 约束生效
- 版本号 +1 与不 +1 两条路径落库正确
- 停用后 `findEnabledTemplateByCode` 返回 null
- `upsertExtra` 的 `ON CONFLICT` 更新路径

集成测试同时填补 order-extra 此前零集成测试覆盖的缺口——`::jsonb` 转换、
`ON CONFLICT` upsert、`payload::text` 回读映射这些风险最高的 SQL 此前从未在真实
Postgres 上执行过。

## 文档

同步更新 `API.md`：新增管理接口章节；在 order-extra 章节写明未声明字段的容忍行为
与 select options 的约束。

## 附带说明

工作区中 `MerchandiseMapper.xml` 的 `accountByGroup` 改动（按品牌聚合）与本设计无关，
建议单独提交。
