# 小票/A4 打印布局模板设计

日期：2026-07-18
模块：wms-cashier
分支：develop

## 背景

收银系统目前只支持通过 `order-extra` 存储订单的动态业务数据（发票信息等），但打印小票/A4 单据
时，"打印成什么样"（字段位置、样式、纸张类型）完全是前端硬编码的。不同门店可能同时有 A4 打印机
和小型热敏票据机，两种设备的排版需求不同；随着更多店铺接入，硬编码排版无法扩展。

本设计为每个 group 按打印机类型配置可视化的字段布局模板，供前端在打印时按布局渲染订单/店铺/操
作员/extra 数据。

## 目标

1. Owner 可以为每种 `printer_type` 配置一份 grid 式的字段布局（`page`/`rows`/`columns`）
2. 布局写入时做结构与字段引用的校验，避免打印那一刻才发现渲染不出来
3. 提供一个字段清单接口，让前端在编辑布局时能选择合法的可绑定字段，而不是手填字符串

## 非目标

- **不做打印时的数据合并/取数接口**。本设计只交付模板的增删改查和字段清单；给定 orderId 之后如
  何把 layout 与 group/order/extra/profile 的真实数据合并渲染成可打印内容，由前端负责（前端已经
  能拿到这些数据源）。
- **不做布局的历史版本追溯**。不同于 order-extra 的 `template_version`（历史订单快照要对应当时
  的 schema），打印布局不会被任何历史数据引用——打印永远使用当前配置，因此不设 `version` 列，也
  不需要"改了布局要不要升版本"的判断逻辑。
- **不做店铺 logo 上传**。`wms_group` 目前没有 logo 字段；`image` 类型 column 的 `field` 先留给
  前端自行传 URL/base64，不接入固定字段注册表。
- **不做纸张尺寸的按店自定义**。同一个 `printer_type` 对所有店铺来说纸张规格是固定的（由后端
  Java 枚举定义），不作为 `layout` 里可配置的内容。

## 数据表

```sql
CREATE TABLE IF NOT EXISTS wms_receipt_template (
    id           BIGSERIAL PRIMARY KEY,
    group_id     INT NOT NULL,
    printer_type VARCHAR(32) NOT NULL,
    layout       JSONB NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, printer_type)
);
```

一个 group 对每个 `printer_type` **有且只有一行**——不是"一种打印机对应多份模板"，多样性来自同
一店铺可能同时拥有多种打印机类型（A4 + 热敏），而不是同一打印机类型下的多个版式选择。这与
`wms_order_extra_template` 的 `(group_id, code)` 唯一约束是同一思路，只是键从任意 `code`
收窄成了固定枚举 `printer_type`。

不设 `version` 列（原因见"非目标"）。

## printer_type 枚举

Java 端硬编码枚举（`ReceiptPrinterType`），数据库列存其 `name()`：

```java
public enum ReceiptPrinterType {
    A4,
    THERMAL_58,
    THERMAL_80
}
```

初版给这三个值，后续按需追加，不做成开放字符串——开放字符串没有后端校验，容易前后端对不上导致
数据静默存错。

## layout JSON 结构

```json
{
  "page": { },
  "rows": [
    {
      "height": "",
      "columns": [
        { "span": 1, "type": "text", "field": "order.imei", "style": {} }
      ]
    }
  ]
}
```

### 顶层字段

| 字段 | 类型 | 校验 |
|---|---|---|
| `page` | object | 存在则必须是 JSON 对象；内部结构不校验（页边距/方向等由前端自行约定，类似 `style`） |
| `rows` | array | 必须是**非空**数组 |

### `rows[]`

| 字段 | 类型 | 校验 |
|---|---|---|
| `height` | 任意 | 不校验，透传给前端渲染器 |
| `columns` | array | 必须是**非空**数组 |

### `rows[].columns[]`

| 字段 | 类型 | 校验 |
|---|---|---|
| `span` | 任意 | 不校验（前端 grid 系统自行解释） |
| `type` | string | 必须 ∈ `{text, label, image, divider, table}`，否则 400 |
| `field` | string | 见下表，按 `type` 决定必填性与含义 |
| `style` | object | 存在则必须是 JSON 对象；内部结构不校验 |

### `field` 按 `type` 的语义

| `type` | `field` 必填 | 含义 |
|---|---|---|
| `text` | 是 | **数据绑定路径**，必须匹配固定字段注册表，或 `extra.<templateCode>.<key>` 前缀（宽松校验，见下） |
| `label` | 是 | **字面文本本身**（不是绑定路径），例如 `"IMEI:"`。复用 `field` 键而非新增 `text` 键 |
| `image` | 否 | 若提供，视为字面 URL/data URI，不做任何格式或注册表校验 |
| `divider` | 否 | 忽略 |
| `table` | 否 | 初版不细化其 `field`/内部结构语义（无具体使用场景），只要求 `columns` 本身结构合法；细化留到有真实需求时再做 |

这里的关键设计原则：`field` 在 `text` 类型下是"数据从哪来"，在 `label` 类型下是"文本本身"——
同一个 JSON 键在不同 `type` 下含义不同，前端渲染器按 `type` 分发解释，后端校验器也按 `type`
分支校验。

### 固定字段注册表（封闭，严格校验）

`type=text` 时，`field` 必须是以下九个值之一，或匹配 `extra.*` 前缀：

```
store.storeName       -- wms_group.store_name
store.address         -- wms_group.address
order.sellingTime     -- wms_order.selling_time
order.brand           -- 分类树中 parent_id = 0 的祖先分类名
order.model           -- wms_merchandise 所属分类（可能就是叶子分类）名
order.imei            -- wms_merchandise.imei
order.sellingPrice    -- wms_order.selling_price
order.cost            -- wms_merchandise.cost
cashier.printedBy     -- 当前登录用户（打印操作人）的 nickname，前端从自己 session 的
                          profile 里取，不来自 wms_order（wms_order 不记录创建者）
```

这九个字段永远是"固定要显示"的语义（由用户在设计讨论中明确），因此列成闭集，跟 order-extra
的 `type` 枚举一样在写入模板时就拦住拼写错误，而不是等到打印才发现渲染是空的。

### `extra.*` 前缀（开放，宽松校验）

`field` 形如 `extra.<templateCode>.<key>` 时，只校验**格式**（两段非空、用 `.` 分隔），
不校验 `templateCode`/`key` 是否对应该 group 真实存在的 order-extra 模板与字段。

原因与 order-extra 对 payload 未声明字段的宽容原则一致：模板是动态的，随时可能被 owner
新增/停用/改名，如果布局校验绑死"当前必须存在"，那么停用一个 order-extra 模板会连带把引用
它的布局配置变成不合法状态，属于过度耦合。宽松校验的代价是：如果 `templateCode` 拼错，
打印时该字段就是空——这个风险由 `GET /receipt-templates/fields` 接口来缓解（见下）。

## API

| Method | Path | 权限 |
|---|---|---|
| `GET` | `/receipt-templates?includeDisabled=false` | 任意登录；`includeDisabled=true` 需 `ROLE_OWNER` |
| `GET` | `/receipt-templates/{printerType}` | 任意登录 |
| `GET` | `/receipt-templates/fields` | 任意登录 |
| `POST` | `/receipt-templates` | `ROLE_OWNER` |
| `PUT` | `/receipt-templates/{printerType}` | `ROLE_OWNER` |
| `DELETE` | `/receipt-templates/{printerType}` | `ROLE_OWNER`（软删除） |
| `PUT` | `/receipt-templates/{printerType}/enabled` | `ROLE_OWNER` |

读接口全部对任意登录用户开放（`includeDisabled=true` 除外）——收银员打印时需要能拿到布局，
跟 order-extra 的读权限模型一致。条件式权限的实现方式复用 order-extra 已验证过的模式：
`@PreAuthorize("!#includeDisabled or hasRole('OWNER')")`。

### 请求体

`POST /receipt-templates`：

```json
{
  "printerType": "A4",
  "layout": {
    "rows": [
      { "columns": [
        { "span": 1, "type": "label", "field": "IMEI:", "style": {"bold": true} },
        { "span": 2, "type": "text", "field": "order.imei", "style": {} }
      ]}
    ]
  }
}
```

`PUT /receipt-templates/{printerType}`：同上但不含 `printerType`（路径提供，创建后不可改，
是与 `wms_order_extra_template.code` 同等地位的唯一键）。

`PUT /receipt-templates/{printerType}/enabled`：

```json
{ "enabled": true }
```

### 响应

- `POST` / `PUT .../{printerType}` / `PUT .../enabled` → `ApiRes<ReceiptTemplateDto>`
- `DELETE` → `ApiRes<Void>`
- `GET` 系列 → 见下

`ReceiptTemplateDto` 字段：`id`、`printerType`、`layout`（`JsonNode`）、`enabled`。

### 冲突与不可变性

`POST` 一个已存在的 `printerType`（无论 enabled 与否）返回 400，提示改用启用接口——跟
order-extra 的 `code` 冲突处理完全一致，包括对并发重复创建的兜底（`insertTemplate` 失败时捕获
`DuplicateKeyException` 转成 `IllegalArgumentException`，避免暴露成 500）。

### `GET /receipt-templates/fields`

返回固定字段注册表 + 当前 group 已启用的 order-extra 模板动态展开出的字段，供前端在布局编辑器
里做字段选择器：

```json
{
  "fixed": [
    { "field": "store.storeName", "label": "店铺名称" },
    { "field": "store.address", "label": "店铺地址" },
    { "field": "order.sellingTime", "label": "销售时间" },
    { "field": "order.brand", "label": "品牌" },
    { "field": "order.model", "label": "型号" },
    { "field": "order.imei", "label": "IMEI" },
    { "field": "order.sellingPrice", "label": "销售价格" },
    { "field": "order.cost", "label": "成本" },
    { "field": "cashier.printedBy", "label": "打印人" }
  ],
  "extra": [
    {
      "field": "extra.invoice.invoiceTitle",
      "label": "发票信息 - 发票抬头",
      "templateCode": "invoice",
      "key": "invoiceTitle"
    }
  ]
}
```

`fixed` 数组的 `label` 是后端硬编码的中文展示名，与 `field` 常量一一对应，定义在生成该响应的
代码里（不是数据库配置）。

`extra` 数组通过复用已有的 `OrderExtraMapper.findTemplates(groupId, false)` 取当前 group 所有
**启用中**的 order-extra 模板，把每个模板 `schema_json` 里的 `fields` 展开成一条
`extra.<code>.<key>` 记录，`label` 拼接成 `"<模板名> - <字段 label>"`（字段没有 `label` 时退
化用 `key`）。这一步只读，不产生新的持久化状态。

## 代码结构

沿用 order-extra 的分层与命名模式：

| 文件 | 职责 |
|---|---|
| `entity/ReceiptTemplate.java` | 实体，字段对齐表结构 |
| `entity/ReceiptTemplateDto.java` | 响应 DTO |
| `entity/ReceiptTemplateReq.java` | 写请求 DTO（`printerType`、`layout`） |
| `entity/ReceiptTemplateEnabledReq.java` | 启用/停用请求 DTO |
| `entity/ReceiptPrinterType.java` | 打印机类型枚举 |
| `entity/AvailableFieldsDto.java` | `fields` 接口的响应形状（`fixed`/`extra` 两个数组） |
| `mapper/ReceiptTemplateMapper.java` + `resources/mapper/ReceiptTemplateMapper.xml` | CRUD SQL，模式对齐 `OrderExtraMapper` |
| `service/ReceiptLayoutValidator.java` | 独立校验器，仿 `OrderExtraSchemaValidator`：校验 `layout` 结构、`type` 枚举、`field` 语义 |
| `service/ReceiptTemplateService.java` | 编排：权限、group 隔离、冲突检查、字段清单组装（依赖 `OrderExtraMapper` 只读） |
| `controller/ReceiptTemplateController.java` | 7 个端点，薄层委托 |

`ReceiptTemplateService` 依赖 `OrderExtraMapper`（只读，只调 `findTemplates`）来实现字段清单
接口，这是本设计里唯一的跨功能耦合点——耦合方向是单向的（receipt-template → order-extra），
不反向依赖，符合两者本来就有真实业务关联（小票要展示 extra 数据）的现实。

## 权限与错误处理

- 写操作 `@PreAuthorize("hasRole('OWNER')")` 打在 service 方法上，与 `OrderExtraService`/
  `GroupService` 一致。
- Group 隔离：每个查询都带 `securityContext.currentGroupId()`。
- 错误处理沿用 `Assert` → `IllegalArgumentException` → `GlobalExceptionHandler` → 400 的既有
  约定，不新增 `SysErrorCode`。

## 测试

- `ReceiptLayoutValidatorTest`（mock）：结构校验各拒绝分支（`rows`/`columns` 空数组、未知
  `type`）；`field` 语义校验各分支（`text` 必须匹配注册表或 `extra.*`、`label` 允许任意非空
  文本、`image`/`divider` 的 `field` 可选）。
- `ReceiptTemplateServiceTest`（mock）：创建/更新/停用/启用；重复 `printerType` 冲突（含并发
  竞态的 `DuplicateKeyException` 兜底）；字段清单接口正确聚合固定字段与 order-extra 动态字段。
- `ReceiptTemplateIntegrationTest`（TestContainers）：jsonb 往返、`UNIQUE(group_id,
  printer_type)` 约束、停用后 `findEnabled...` 类查询返回 null。
- `ReceiptTemplateSecurityTest`（`@SpringBootTest` + `@WithMockUser`）：验证 `@PreAuthorize`
  在真实 Spring AOP 代理下生效，尤其是 `getTemplates` 的条件式 SpEL——这条已经在 order-extra
  的同类测试里验证过 `spring-security-test` 依赖与参数名解析在本项目构建下工作正常，这次直接
  复用该结论，不需要重新做参数名解析的探测性验证。

## 文档

`README.md` 的"数据库表结构"补充 `wms_receipt_template` 一行；"API 参考"新增 "Receipt
Template `/receipt-templates`" 一节，格式对齐现有 Order Extra 小节。
