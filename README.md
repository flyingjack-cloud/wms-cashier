# wms-cashier

`wms-cashier` 是从旧单体 `wms_backend` 拆分出的 WMS 微服务，负责门店管理、商品、订单、分类、权限等业务逻辑。作为 OAuth2 Resource Server，它验证由 `auth-service` 签发的 JWT，不处理登录或注册流程。

- **端口：** 8081（dev）
- **技术栈：** Java 21 / Spring Boot 3.2.4 / MyBatis 3.0.3 / PostgreSQL
- **Base URL (dev)：** `http://localhost:8081`
- 所有接口都要求合法的 JWT Bearer token（`Authorization: Bearer <token>`），除非文档中特别注明。

## 目录

- [快速开始](#快速开始)
- [架构](#架构)
- [数据库表结构](#数据库表结构)
- [环境变量](#环境变量)
- [API 参考](#api-参考)
- [数据迁移（从 wms_backend）](#数据迁移从-wms_backend)
- [部署](#部署)
- [部署前待办事项](#部署前待办事项)

---

## 快速开始

### 启动依赖

```bash
# 启动 PostgreSQL（使用 Docker）
docker run -d --name wms-pg \
  -e POSTGRES_DB=wms -e POSTGRES_USER=wms -e POSTGRES_PASSWORD=wms \
  -p 5432:5432 postgres:16-alpine

# 启动 auth-service（用于 JWKS 端点）
cd ../auth-service && ./mvnw spring-boot:run -P dev
```

### 运行服务

```bash
export DB_URL=jdbc:postgresql://localhost:5432/wms
export DB_USERNAME=wms
export DB_PASSWORD=wms
# AUTH_ISSUER_URI 不填，默认使用 http://localhost:9001

./mvnw spring-boot:run -pl wms-cashier -P dev
```

### 运行测试

```bash
# 单元测试（无需外部依赖）
./mvnw test -pl wms-cashier -Dtest="*ServiceTest"

# 集成测试（需要 Docker，TestContainers 自动启动 PostgreSQL）
./mvnw test -pl wms-cashier -Dtest="WmsCashierIntegrationTest"

# 全部测试
./mvnw test -pl wms-cashier
```

集成测试通过 `@MockBean AuthServiceClient` 屏蔽 Feign 调用，通过 `@MockBean JwtDecoder` 避免测试启动依赖真实 UAC discovery，TestContainers 自动管理 PostgreSQL 容器生命周期。

---

## 架构

### 请求流

```
Client
  │
  ├─(prod/beta)─▶ Istio IngressGateway (auth.flyingjack.top)
  │                   │ /api/** → strip /api → wms-cashier:8081
  │
  └─(dev)──────▶ wms-cashier:8081 直连
```

### 安全模型

每个请求都携带 Bearer JWT（由 `auth-service` RSA 私钥签发）。

```
请求到达
  │
  ▼
BearerTokenAuthenticationFilter
  │  从 Authorization: Bearer <token> 提取 JWT
  │  通过 issuer-uri 发现 JWKS 端点并验证签名与 issuer
  │
  ▼
CustomJwtAuthenticationConverter
  │  jwt.sub → Long userId
  │  查询 wms_user_profile（不存在则插入默认行 group_id=0, role=ROLE_DEFAULT）
  │  查询 wms_authority（细粒度权限）
  │  组装 JwtAuthenticationToken(authorities)
  │
  ▼
SecurityContextHolder → 业务层可通过 WmsSecurityContext 取 userId / groupId
```

**配置关键项：**

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: ${AUTH_ISSUER_URI:http://localhost:9001}
```

使用 `issuer-uri` 对接 UAC 的 `/.well-known/openid-configuration`，由 Spring Security 自动发现 JWKS 端点，并校验 JWT 的 `iss` claim。

### 角色权限体系

```
ROLE_ADMIN
  └─▶ ROLE_OWNER
        ├─▶ ROLE_STAFF
        ├─▶ PERMISSION:shopping
        ├─▶ PERMISSION:inventory
        └─▶ PERMISSION:statistic
```

- `ROLE_DEFAULT`：新用户未加入任何门店时的初始角色（group_id = 0）。
- 角色存在隐式继承：OWNER 自动拥有 STAFF 及三项 PERMISSION。
- 方法级鉴权通过 `@PreAuthorize("hasRole('OWNER')")` / `@PreAuthorize("hasRole('DEFAULT')")` 声明。

### 服务间通信

`wms-cashier` 通过 OpenFeign 调用 `auth-service` 的内部接口：

```java
@FeignClient(name = "auth-service")          // K8s DNS 解析：http://auth-service:9001
ApiRes<Long> getUserIdByPhone(String phone);  // GET /internal/users/by-phone?phone=
```

- **prod/beta：** K8s ClusterDNS 自动解析 `auth-service` → `auth-service.flyingjack-{profile}.svc.cluster.local`
- **dev：** `application-dev.yml` 中通过 `spring.cloud.openfeign.client.config.auth-service.url` 覆盖为 `http://localhost:9001`

`/internal/**` 端点在 auth-service 侧配置为 `permitAll`，网络层由 Istio NetworkPolicy 保护（仅集群内可达）。

### 数据层

```
Controller → Service → Mapper Interface → MyBatis XML → PostgreSQL
```

- 所有时间字段使用 `Instant`，数据库列类型 `TIMESTAMPTZ`（UTC+0 存储）。
- `group_id` 是多租户隔离键，所有写操作的 SQL 均带 `AND group_id = #{groupId}` 过滤，防止跨租户数据访问。
- MyBatis 开启 `map-underscore-to-camel-case`，无需手动映射列名。

---

## 数据库表结构

| 表 | 说明 | 关键列 |
|---|---|---|
| `wms_user_profile` | 用户在 WMS 内的身份 | `user_id BIGINT PK`, `group_id`, `role` |
| `wms_authority` | 细粒度权限（PERMISSION:*） | `(user_id, authority) PK` |
| `wms_group` | 门店/用户组 | `id SERIAL PK`, `store_name` |
| `wms_join_request` | 加入门店申请 | `user_id PK`, `group_id` |
| `wms_category` | 商品分类（支持父子层级） | `id`, `group_id`, `parent_id` |
| `wms_merchandise` | 商品 | `id`, `group_id`, `cate_id`, `cost`, `price`, `sold` |
| `wms_order` | 销售订单 | `id`, `group_id`, `me_id`, `selling_price`, `is_returned` |
| `wms_order_extra_template` | 订单附加数据模板（票据等动态字段定义） | `id`, `group_id`, `code`, `version`, `schema_json`, `enabled` |
| `wms_order_extra` | 订单附加数据（按模板快照的 JSON payload） | `id`, `group_id`, `order_id`, `template_code`, `template_version`, `payload` |
| `wms_receipt_template` | 小票/A4 打印布局模板（按 group + printer_type） | `id`, `group_id`, `printer_type`, `layout`, `enabled` |
| `wms_notice` | 系统通知 | `id`, `group_id`, `type`, `content` |

完整 DDL 见 [`src/main/resources/schema.sql`](src/main/resources/schema.sql)。

---

## 环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `DB_URL` | ✅ | PostgreSQL JDBC URL，如 `jdbc:postgresql://host:5432/wms` |
| `DB_USERNAME` | ✅ | 数据库用户名 |
| `DB_PASSWORD` | ✅ | 数据库密码 |
| `AUTH_ISSUER_URI` | prod/beta | auth-service 地址，如 `https://auth.flyingjack.top`；dev 可不填，默认 `http://localhost:9001` |

K8s 中通过 Pod spec 的 `env` / `envFrom` 字段（引用 Secret 或 ConfigMap）注入为 OS 环境变量，Spring 通过 `${ENV_VAR}` 占位符读取。不使用 spring-cloud-kubernetes-client-config，不得硬编码在代码或 Dockerfile 中。

---

## API 参考

Base URL (dev): `http://localhost:8081`

All endpoints require a valid JWT Bearer token (`Authorization: Bearer <token>`) unless noted otherwise.

### Timestamps

All timestamps — in query/form params, JSON request bodies, and responses alike — should be sent as an **ISO-8601 string with a `Z` suffix** (e.g. `"2025-01-01T00:00:00Z"`), denoting UTC (zero offset). This is the canonical format in every direction: a field looks the same whether you're sending it or receiving it, and whether it's a query param (e.g. `GET /order/range?start=2025-01-01T00:00:00Z`) or a JSON body field (e.g. `POST /order/batch`'s `sellingTime`). Equivalent ISO-8601 offset strings are accepted by the backend because fields are parsed as `Instant`, but clients should still use the `Z` form documented here.

The backend stores and handles time exclusively as `Instant` — a single, timezone-free point on the UTC timeline. There is no offset/timezone concept anywhere in this API; converting to a user's local time is entirely the frontend's responsibility.

Never send an offset-less local string (e.g. `"2025-01-01T00:00:00"`, no `Z`/offset) in a JSON body — it's rejected outright since it can't be resolved to an unambiguous instant. A bare epoch number in a JSON body is **not** rejected, but don't send one: Jackson silently parses it as epoch *seconds* rather than milliseconds, producing the wrong instant (this is exactly the bug that prompted this convention). Query/form params are more lenient purely as a side effect of the framework: a bare number there *is* still accepted, and correctly treated as epoch *milliseconds*, for backward compatibility with older clients — but ISO-8601 is the canonical, documented format going forward; new code should always send strings in both places.

All responses use the unified wrapper:
```json
{
  "code": 200,
  "message": "OK",
  "data": ...,
  "timestamp": "2025-01-01T00:00:00Z"
}
```

---

### Category `/category`

#### GET `/category/parent/{parentId}`
Get sub-categories by parent ID.

| | |
|---|---|
| Auth | Required |
| Path | `parentId: int` |

**Response** `data: Category[]`
```json
[{ "id": 1, "groupId": 10, "parentId": 0, "name": "Electronics" }]
```

---

#### GET `/category/{id}`
Get a single category by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: Category`

---

#### POST `/category/`
Create a new category.

| | |
|---|---|
| Auth | Required |
| Params | `parentId: int`, `name: string` |

**Response** `data: int` — new category ID

---

#### DELETE `/category/{id}`
Delete a category by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

---

### Group `/group`

#### GET `/group/`
Get the group that the current user belongs to.

| | |
|---|---|
| Auth | Required |

**Response** `data: Group`
```json
{ "id": 1, "storeName": "My Store", "address": "123 St", "contact": "138xxxx", "createdAt": "2025-01-01T00:00:00Z" }
```

---

#### POST `/group/`
Create a new group (store).

| | |
|---|---|
| Auth | Required |
| Params | `storeName: string`, `address?: string`, `contact?: string`, `createTime?: string (ISO-8601 UTC)` |

**Response** `data: null`

---

#### PUT `/group/storename`
Update the store name.

| | |
|---|---|
| Auth | Required |
| Params | `storeName: string` |

**Response** `data: null`

---

#### PUT `/group/address`
Update the store address.

| | |
|---|---|
| Auth | Required |
| Params | `address: string` |

**Response** `data: null`

---

#### PUT `/group/contact`
Update the store contact.

| | |
|---|---|
| Auth | Required |
| Params | `contact: string` |

**Response** `data: null`

---

#### GET `/group/staffs`
Get all staff members in the group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |

**Response** `data: WmsUserProfile[]`

---

#### DELETE `/group/staff`
Remove a staff member from the group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: null`

---

#### POST `/group/join/id`
Submit a join request by group ID.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |
| Params | `groupId: int` |

**Response** `data: null`

---

#### POST `/group/join/phone`
Submit a join request by the owner's phone number.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |
| Params | `phone: string` |

**Response** `data: null`

---

#### GET `/group/join/`
Get the group that the current user has a pending join request for.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |

**Response** `data: Group`

---

#### DELETE `/group/join/delete`
Cancel the current user's own join request.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |

**Response** `data: null`

---

#### DELETE `/group/join/delete/id`
Reject a specific user's join request (owner action).

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: null`

---

#### GET `/group/join/users`
Get all users with a pending join request for the owner's group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |

**Response** `data: WmsUserProfile[]`

---

#### POST `/group/join/agree`
Approve a user's join request and assign permissions.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long`, `shopping: boolean`, `inventory: boolean`, `statistics: boolean` |

**Response** `data: null`

---

#### PUT `/group/permissions`
Update an existing staff member's permissions.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long`, `shopping: boolean`, `inventory: boolean`, `statistics: boolean` |

**Response** `data: null`

---

#### GET `/group/permissions`
Get a staff member's permission list.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: string[]` — e.g. `["PERMISSION:shopping", "PERMISSION:inventory", "PERMISSION:statistic"]`

---

### Merchandise `/merchandise`

#### GET `/merchandise`
Get paginated merchandise list.

| | |
|---|---|
| Auth | Required |
| Params | `sold?: boolean (default false)`, `limit: int (1–999)`, `offset: int (≥0)` |

**Response** `data: { count: int, merchandise: MerchandiseWithCategoryDto[] }` — each item is a `Merchandise` plus a nested `category` object (`null` if the category was since deleted)
```json
{
  "count": 42,
  "merchandise": [
    {
      "id": 1, "groupId": 10, "cateId": 2, "cost": "100.00", "price": "150.00", "imei": "123456789", "sold": false, "createdAt": "2025-01-01T00:00:00Z",
      "category": { "id": 2, "groupId": 10, "parentId": 0, "name": "手机" }
    }
  ]
}
```

---

#### GET `/merchandise/cate`
Get merchandise under a category.

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int`, `sold?: boolean (default false)` |

**Response** `data: Merchandise[]`

---

#### POST `/merchandise`
Add merchandise (supports batch via IMEI list).

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int`, `cost: decimal`, `price: decimal`, `imei_list: string[]`, `create_time: string (ISO-8601 UTC)` |

**Response** `data: null`

---

#### PUT `/merchandise/{id}`
Update merchandise cost, price, and IMEI.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |
| Params | `cost: decimal`, `price: decimal`, `imei: string` |

**Response** `data: null`

---

#### DELETE `/merchandise/{id}`
Delete merchandise by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

---

#### GET `/merchandise/search`
Search merchandise by IMEI.

| | |
|---|---|
| Auth | Required |
| Params | `text: string`, `sold?: boolean (default false)` |

**Response** `data: MerchandiseWithCategoryDto[]` — each item is a `Merchandise` plus a nested `category` object (`null` if the category was since deleted)

---

#### GET `/merchandise/account`
Get merchandise count statistics grouped by category.

| | |
|---|---|
| Auth | Required |

**Response** `data: MeCount[]`

---

### Notice `/notice`

#### GET `/notice/`
Get the latest notice of a given type.

| | |
|---|---|
| Auth | Required |
| Params | `type: string` |

**Response** `data: Notice`

---

### Order `/order`

#### POST `/order`
Create a single order. Marks the referenced merchandise as `sold`.

| | |
|---|---|
| Auth | Required |
| Params | `me_id: int`, `selling_price: decimal`, `selling_time?: string (ISO-8601 UTC)`, `remark: string` |

**Response** `data: int` — new order ID

**Errors** `400` — `me_id` doesn't reference an existing merchandise in the current group, or that merchandise is already `sold`

---

#### POST `/order/batch`
Batch create orders. Marks each referenced merchandise as `sold`.

| | |
|---|---|
| Auth | Required |
| Body | `Order[]` |

```json
[
  {
    "groupId": 10,
    "meId": 5,
    "sellingPrice": "150.00",
    "sellingTime": "2025-01-01T00:00:00Z",
    "remark": "cash",
    "returned": false
  }
]
```

**Response** `data: int[]` — new order IDs, in the same order as the request body

**Errors** `400` — any order's `meId` doesn't reference an existing merchandise in the current group, that merchandise is already `sold`, or the same `meId` appears more than once in the batch

---

#### GET `/order/range`
Get paginated orders within a time range.

| | |
|---|---|
| Auth | Required |
| Params | `start: string (ISO-8601 UTC)`, `end: string (ISO-8601 UTC)`, `limit: int (1–999)`, `offset: int (≥0)` |

**Response** `data: { count: int, orders: OrderListItemDto[] }` — each item is an `Order` plus a nested `merchandise` object (`null` if the merchandise record was since deleted), which itself nests a `category` object (`null` if the category was since deleted)
```json
{
  "count": 12,
  "orders": [
    {
      "id": 1, "groupId": 10, "meId": 2, "sellingPrice": "150.00", "sellingTime": "2025-01-01T00:00:00Z", "remark": "备注", "returned": false,
      "merchandise": {
        "id": 2, "groupId": 10, "cateId": 1, "cost": "100.00", "price": "150.00", "imei": "123456789", "sold": true, "createdAt": "2025-01-01T00:00:00Z",
        "category": { "id": 1, "groupId": 10, "parentId": 0, "name": "手机" }
      }
    }
  ]
}
```

---

#### PUT `/order/return/{id}`
Mark an order as returned. Marks the referenced merchandise as not `sold` again.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

**Errors** `400` — `id` doesn't reference an existing order in the current group

---

### Order Extra `/order-extra`, `/order/{orderId}/extra`

Order extra data is stored separately from `wms_order`, so existing order creation, return, and inventory flows are unchanged. Templates define dynamic fields; each order stores a JSON payload snapshot for the chosen template.

#### GET `/order-extra/templates`
Get enabled order-extra templates for the current group.

| | |
|---|---|
| Auth | Required; `includeDisabled=true` requires `ROLE_OWNER` |
| Query | `includeDisabled: boolean` — default `false`. When `true`, also returns disabled templates for admin use |

**Response** `data: OrderExtraTemplateDto[]`

```json
[
  {
    "id": 1,
    "code": "invoice",
    "name": "发票信息",
    "version": 1,
    "schema": {
      "fields": [
        { "key": "invoiceTitle", "label": "发票抬头", "type": "text", "required": true },
        { "key": "taxNo", "label": "税号", "type": "text", "required": false }
      ]
    },
    "enabled": true
  }
]
```

---

#### GET `/order-extra/templates/{code}`
Get one enabled order-extra template by code.

| | |
|---|---|
| Auth | Required |
| Path | `code: string` |

**Response** `data: OrderExtraTemplateDto`

---

#### POST `/order-extra/templates`
Create an order-extra template.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Body | `code: string`, `name: string`, `schema: object` |

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

**Response** `data: OrderExtraTemplateDto` — the created template, `version` starts at `1`.

**Errors** `400` — `code` already exists in the group (use the enable endpoint instead of
recreating), or the schema fails validation (see "Template schema rules" below)

---

#### PUT `/order-extra/templates/{code}`
Update a template's name and schema. `code` is immutable — it is the key that
`wms_order_extra` snapshots reference.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `code: string` |
| Body | `name: string`, `schema: object` |

`version` is incremented **only when the schema actually changes**. Renaming the template alone
leaves `version` untouched. Comparison is structural, so key order and whitespace differences do
not trigger a bump.

**Response** `data: OrderExtraTemplateDto` — the updated template, including the resulting `version`

**Errors** `400` — template doesn't exist, or the schema fails validation

---

#### DELETE `/order-extra/templates/{code}`
Disable a template. This is a soft delete: the row is kept with `enabled = false`, and existing
order-extra data is untouched. Disabled templates are rejected by
`PUT /order/{orderId}/extra/{templateCode}` and hidden from `GET /order-extra/templates`.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `code: string` |

**Response** `data: null`

**Errors** `400` — template doesn't exist

---

#### PUT `/order-extra/templates/{code}/enabled`
Enable or disable a template.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `code: string` |
| Body | `enabled: boolean` |

```json
{ "enabled": true }
```

**Response** `data: OrderExtraTemplateDto`

**Errors** `400` — template doesn't exist

---

#### Template schema rules

A template's `schema` must be an object with a non-empty `fields` array. Each field:

| Key | Required | Notes |
|---|---|---|
| `key` | yes | Non-empty, unique within the template |
| `type` | yes | One of `text`, `textarea`, `number`, `boolean`, `date`, `datetime`, `select` |
| `label` | no | Display label for the frontend |
| `required` | no | Defaults to `false` |
| `options` | only when `type` is `select` | Non-empty array of allowed values |

Payload validation on `PUT /order/{orderId}/extra/{templateCode}`:

- Fields marked `required` must be present and non-blank
- Values must match the declared `type`
- For `select` fields, the value must be one of the declared `options` — the frontend dropdown is
  a UI affordance, not the enforcement point
- **Fields not declared in the schema are accepted and stored as-is.** This is intentional, not an
  oversight: the payload is a snapshot. Validation constrains the values of declared fields; it does
  not restrict which fields may be sent.

---

#### PUT `/order/{orderId}/extra/{templateCode}`
Create or update an order's extra data for a template. The body must be a JSON object and is validated against the current template schema.

| | |
|---|---|
| Auth | Required |
| Path | `orderId: int`, `templateCode: string` |
| Body | dynamic JSON object |

```json
{
  "invoiceTitle": "上海某某公司",
  "taxNo": "9131...",
  "customerName": "张三",
  "serialNo": "SN123456"
}
```

**Response** `data: null`

**Errors** `400` — order doesn't exist in the current group, template doesn't exist or isn't enabled, or payload fails template validation

---

#### GET `/order/{orderId}/extra`
Get all extra data for one order.

| | |
|---|---|
| Auth | Required |
| Path | `orderId: int` |

**Response** `data: OrderExtraDto[]`

```json
[
  {
    "orderId": 123,
    "templateCode": "invoice",
    "templateName": "发票信息",
    "templateVersion": 1,
    "payload": {
      "invoiceTitle": "上海某某公司",
      "taxNo": "9131...",
      "serialNo": "SN123456"
    }
  }
]
```

---

#### GET `/order/{orderId}/extra/{templateCode}`
Get one order-extra payload by template code.

| | |
|---|---|
| Auth | Required |
| Path | `orderId: int`, `templateCode: string` |

**Response** `data: OrderExtraDto`

---

### Receipt Template `/receipt-templates`

Templates control how a receipt/A4 document is laid out when printed. Each group has at most one
template per `printerType`; the template stores a grid-style layout (`page`/`rows`/`columns`), not
resolved data — the client is responsible for merging the layout with real order/group/profile data
at print time.

#### GET `/receipt-templates`
Get enabled receipt templates for the current group.

| | |
|---|---|
| Auth | Required; `includeDisabled=true` requires `ROLE_OWNER` |
| Query | `includeDisabled: boolean` — default `false` |

**Response** `data: ReceiptTemplateDto[]`

```json
[
  {
    "id": 1,
    "printerType": "A4",
    "layout": {
      "rows": [
        { "columns": [
          { "span": 1, "type": "label", "field": "IMEI:", "style": { "bold": true } },
          { "span": 2, "type": "text", "field": "order.imei", "style": {} }
        ]}
      ]
    },
    "enabled": true
  }
]
```

---

#### GET `/receipt-templates/{printerType}`
Get one enabled receipt template by printer type.

| | |
|---|---|
| Auth | Required |
| Path | `printerType: string` — one of `A4`, `THERMAL_58`, `THERMAL_80` |

**Response** `data: ReceiptTemplateDto`

---

#### GET `/receipt-templates/fields`
Get the fields available for binding in a layout editor: fixed fields that are always resolvable,
plus fields dynamically expanded from the group's enabled order-extra templates.

| | |
|---|---|
| Auth | Required |

**Response** `data: AvailableFieldsDto`

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

---

#### POST `/receipt-templates`
Create a receipt template.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Body | `printerType: string`, `layout: object` |

```json
{
  "printerType": "A4",
  "layout": {
    "rows": [
      { "columns": [
        { "span": 1, "type": "label", "field": "IMEI:", "style": { "bold": true } },
        { "span": 2, "type": "text", "field": "order.imei", "style": {} }
      ]}
    ]
  }
}
```

**Response** `data: ReceiptTemplateDto` — the created template

**Errors** `400` — `printerType` already exists in the group (use the enable endpoint instead of
recreating), `printerType` isn't a supported value, or the layout fails validation (see "Receipt
layout rules" below)

---

#### PUT `/receipt-templates/{printerType}`
Update a template's layout. `printerType` is immutable — one row per `(group, printerType)`.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `printerType: string` |
| Body | `layout: object` |

**Response** `data: ReceiptTemplateDto`

**Errors** `400` — template doesn't exist, or the layout fails validation

---

#### DELETE `/receipt-templates/{printerType}`
Disable a template. Soft delete: the row is kept with `enabled = false`.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `printerType: string` |

**Response** `data: null`

**Errors** `400` — template doesn't exist

---

#### PUT `/receipt-templates/{printerType}/enabled`
Enable or disable a template.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Path | `printerType: string` |
| Body | `enabled: boolean` |

**Response** `data: ReceiptTemplateDto`

**Errors** `400` — template doesn't exist

---

#### Receipt layout rules

A template's `layout` must be a JSON object with a non-empty `rows` array. `page` is an optional
free-form object (margins, orientation, etc. — not validated). Each row:

| Key | Required | Notes |
|---|---|---|
| `height` | no | Not validated, passed through to the frontend renderer |
| `columns` | yes | Non-empty array |

Each column:

| Key | Required | Notes |
|---|---|---|
| `span` | no | Not validated, interpreted by the frontend's grid system |
| `type` | yes | One of `text`, `label`, `image`, `divider`, `table` |
| `field` | depends on `type` — see below | |
| `style` | no | Free-form object if present, not validated |

`field`'s meaning depends on `type`:

- `type: "text"` — `field` is a **data-binding path**, required. Must be one of the nine fixed
  fields (`store.storeName`, `store.address`, `order.sellingTime`, `order.brand`, `order.model`,
  `order.imei`, `order.sellingPrice`, `order.cost`, `cashier.printedBy`) or match
  `extra.<templateCode>.<key>` (loosely validated — only the two-segment shape is checked, not
  whether that order-extra template/field currently exists in the group)
- `type: "label"` — `field` is **literal text** to print as-is (e.g. `"IMEI:"`), required,
  non-blank
- `type: "image"` / `type: "divider"` — `field` optional, not validated
- `type: "table"` — internal structure not yet defined; only requires the column itself to be
  structurally valid

`cashier.printedBy` resolves to the nickname of whoever is currently printing, not the order's
original seller — `wms_order` doesn't record who created it.

---

### Profile `/profile`

#### GET `/profile/`
Get the current user's profile.

| | |
|---|---|
| Auth | Required |

**Response** `data: WmsUserProfile`
```json
{ "userId": 1, "groupId": 10, "role": "ROLE_OWNER", "nickname": "Jack" }
```

---

#### GET `/profile/role`
Get the current user's role.

| | |
|---|---|
| Auth | Required |

**Response** `data: string` — e.g. `"ROLE_OWNER"`

---

#### GET `/profile/permissions`
Get the current user's permission list.

| | |
|---|---|
| Auth | Required |

**Response** `data: string[]` — e.g. `["PERMISSION:shopping", "PERMISSION:inventory", "PERMISSION:statistic"]`

---

#### PUT `/profile/nickname`
Update the current user's nickname.

| | |
|---|---|
| Auth | Required |
| Params | `nickname: string` |

**Response** `data: null`

---

## 数据迁移（从 wms_backend）

### 背景

旧系统 `wms_backend` 使用 `INT` 类型的 userId，与 auth-service 的新 `BIGINT` userId 不兼容。迁移需要通过 `user_id_mapping` 表完成类型转换。

### 前置条件

1. `auth-service` 数据库中已完成用户迁移，并生成 `user_id_mapping(old_int_id INT, new_bigint_id BIGINT)`。
2. 新 `wms-cashier` 数据库已通过 `schema.sql` 建表（服务首次启动时 `CREATE TABLE IF NOT EXISTS` 自动执行）。
3. 旧库与新库之间可通过 `dblink` 或数据导出（CSV）互通数据。

### 迁移步骤

完整迁移 SQL 见 [`docs/migration/V2__wms_cashier_data_migration.sql`](../docs/migration/V2__wms_cashier_data_migration.sql)。

**执行顺序（有依赖关系，不可乱序）：**

```
1. wms_group          — 无 user_id FK，直接复制
2. wms_user_profile   — 通过 user_id_mapping 转换 userId
3. wms_authority      — 通过 user_id_mapping 转换 userId（仅迁移 PERMISSION:* 细粒度权限）
4. wms_category       — 无 user_id FK，直接复制
5. wms_merchandise    — 无 user_id FK，直接复制
6. wms_order          — 无 user_id FK，直接复制
7. wms_join_request   — 通过 user_id_mapping 转换 userId
8. wms_notice         — 无 user_id FK，直接复制
```

所有 INSERT 均带 `ON CONFLICT DO NOTHING`，迁移可重复执行（幂等）。

### 迁移后验证

```sql
-- 验证用户数一致
SELECT COUNT(*) FROM wms_user_profile;                    -- 应与旧库 user 数量一致

-- 验证 group_id=0 的用户（未加入门店）是否合理
SELECT COUNT(*) FROM wms_user_profile WHERE group_id = 0;

-- 验证商品总数
SELECT COUNT(*) FROM wms_merchandise;

-- 验证订单总数
SELECT COUNT(*) FROM wms_order;

-- 检查孤立权限（user_id 不在 wms_user_profile 中）
SELECT a.user_id FROM wms_authority a
LEFT JOIN wms_user_profile p ON a.user_id = p.user_id
WHERE p.user_id IS NULL;
```

---

## 部署

### Beta / Prod（Kubernetes + ArgoCD）

本服务通过 ArgoCD GitOps 部署，k8s 配置由 `k8s-gitops` 仓库管理。**Secrets 不进 GitOps 仓库**，需在目标集群手动提前创建；ArgoCD 只管理 Deployment / ConfigMap / Service 等无密态资源。

所有敏感配置通过 `envFrom: secretRef` 注入为 OS 环境变量，服务启动时由 Spring Boot 读取，不依赖 Spring Cloud Kubernetes API。

| 环境 | 访问方式 | TLS |
|---|---|---|
| beta | 公网，Istio Gateway，HTTPS，`api.flyingjack.top/cashier` | cert-manager 自动签发/续签 |
| prod | 公网，Istio Gateway，HTTPS，`api.flyingjack.top/cashier` | cert-manager 自动签发/续签 |

#### 手动创建 Secrets（首次部署或凭据轮换时执行）

本服务需两组 Secret，通过 `envFrom: secretRef` 直接注入为 OS 环境变量，无需特殊标签。

##### Beta 环境

```bash
# 切换到目标命名空间（如不存在先创建）
kubectl create namespace flyingjack-beta --dry-run=client -o yaml | kubectl apply -f -

# 数据库连接凭据
kubectl create secret generic cashier-connect \
  --from-literal=DB_URL=jdbc:postgresql://beta.flyingcloud.local:5432/wms_cashier \
  --from-literal=DB_USERNAME=postgres \
  --from-literal=DB_PASSWORD=<实际密码> \
  -n flyingjack-beta \
  --dry-run=client -o yaml | kubectl apply -f -

# Redis 凭据
kubectl create secret generic cashier-cache-secret \
  --from-literal=REDIS_HOST=<beta Redis地址> \
  --from-literal=REDIS_PASSWORD=<Redis密码，无密码则留空字符串> \
  -n flyingjack-beta \
  --dry-run=client -o yaml | kubectl apply -f -
```

##### Prod 环境

```bash
kubectl create namespace flyingjack-prod --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic cashier-connect \
  --from-literal=DB_URL=jdbc:postgresql://prod.flyingcloud.local:5432/wms_cashier \
  --from-literal=DB_USERNAME=<prod数据库用户> \
  --from-literal=DB_PASSWORD=<prod数据库密码> \
  -n flyingjack-prod \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic cashier-cache-secret \
  --from-literal=REDIS_HOST=<prod Redis地址> \
  --from-literal=REDIS_PASSWORD=<prod Redis密码> \
  -n flyingjack-prod \
  --dry-run=client -o yaml | kubectl apply -f -
```

##### 镜像仓库拉取凭据（仅认证 registry 需要）

使用无认证的 registry:2 时跳过此步骤。如果 registry 需要认证（如 Harbor），需在每个命名空间创建拉取凭据，并在 `deployment-patch.yaml` 中补充 `imagePullSecrets`：

```bash
kubectl create secret docker-registry harbor-pull-secret \
  --docker-server=<registry地址> \
  --docker-username=<用户名> \
  --docker-password=<密码> \
  -n flyingjack-beta   # prod 环境替换命名空间重复执行
```

#### 验证 Secrets 是否正确

##### Beta 环境

```bash
# 检查 secret 存在
kubectl get secret cashier-connect cashier-cache-secret -n flyingjack-beta

# 查看 secret 的 key（不显示值）
kubectl get secret cashier-connect -n flyingjack-beta -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
kubectl get secret cashier-cache-secret -n flyingjack-beta -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
```

##### Prod 环境

```bash
kubectl get secret cashier-connect cashier-cache-secret -n flyingjack-prod

kubectl get secret cashier-connect -n flyingjack-prod -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
kubectl get secret cashier-cache-secret -n flyingjack-prod -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
```

#### AUTH_ISSUER_URI 说明

`AUTH_ISSUER_URI` 不是敏感信息，注入方式与其他服务不同：直接写在 `k8s-gitops/wms-cashier/overlays/{profile}/kustomization.yaml` 的 `configMapGenerator` 里，不需要手动创建 Secret。

- 该值必须与 auth-service 在对应环境下签发 JWT 时实际使用的 issuer 完全一致，否则 JWKS 校验会失败。
- beta 当前值为 `http://100.107.74.15:30880`（auth-service beta 的 Tailscale NodePort 地址，见 `auth-service/DEPLOY.md`）。
- prod 当前值为 `https://auth.flyingjack.top`。
- 若 auth-service 的部署地址发生变化，需同步更新此处，并 `kubectl rollout restart deployment/wms-cashier-v1 -n <namespace>`。

#### 前置：确认 api.flyingjack.top HTTPS 已就绪

本服务通过共享 Gateway 暴露在 `https://api.flyingjack.top`，TLS 证书由 `shared-networking` 统一管理。**首次在新集群部署前，需确认 `shared-networking` 已部署且证书已签发**，步骤见 `k8s-gitops/shared-networking/DEPLOY.md`。

#### ArgoCD Application 创建

> **前置条件**：Namespace 须提前手动创建，见 `k8s-gitops/shared/DEPLOY.md`。ArgoCD Application 不负责创建 Namespace，防止 auto-prune 误删命名空间。

在 ArgoCD 所在集群执行（或通过 ArgoCD UI 导入）：

```bash
kubectl apply -f - <<EOF
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: wms-cashier-beta
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/flyingjack-cloud/k8s-gitops
    targetRevision: main
    path: wms-cashier/overlays/beta
  destination:
    server: https://kubernetes.default.svc
    namespace: flyingjack-beta
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
EOF
```

prod 环境将 `beta` 替换为 `prod` 重复执行。

#### 部署验证（Smoke Test）

本服务所有接口均要求 OAuth2 Bearer Token（`anyRequest().authenticated()`，无匿名端点），因此无法用业务接口直接验证返回数据，只能验证路由是否打通。

```
GET https://api.flyingjack.top/cashier/profile/
```

预期返回：HTTP `401 Unauthorized`（未携带 token）

验证点：
- 返回 401 说明 **Istio `/cashier/` 前缀路由**正常（前缀已被剥除，请求正确到达 wms-cashier）
- 若返回 502/503，检查 Pod 是否正常运行：`kubectl get pods -n flyingjack-beta`（或 `flyingjack-prod`）
- 若返回 404，检查 VirtualService 路由规则是否已被 ArgoCD 同步
- 携带合法 token 重试应返回 200 及 profile 数据，用于验证 JWKS 校验（即 `AUTH_ISSUER_URI` 配置正确）是否生效

#### 注意事项

- Secret 变更后需手动 `kubectl rollout restart deployment/wms-cashier-v1 -n <namespace>` 触发 Pod 重启以读取新值。
- prod 环境禁止直接 `kubectl apply`，所有变更须通过 ArgoCD 同步。

### 局域网部署（本地 Docker，无需镜像仓库）

这套脚本用于本地开发环境，不依赖镜像仓库。它会在本机执行 Maven 构建和
`docker build`，把镜像、运行配置和远端部署脚本打成一个 tar 包，然后通过
SCP 上传至目标服务器并运行。`wms-cashier` 默认映射业务端口 `8086` 和管理端口
`8081`，并通过 `SPRING_APPLICATION_JSON` 指向局域网 auth-service。默认使用
`Dockerfile.lan` 的 JRE 基础镜像缩小
传输包，不改变项目现有 `Dockerfile` 的构建行为。

#### 准备配置

```bash
cp .env.lan.example .env.lan
chmod 600 .env.lan
```

修改 `.env.lan`。其中 `MAVEN_*`、`IMAGE_*`、`DOCKER_*`、容器/端口、
`HEALTHCHECK_*`、`DEPLOY_*` 和 `BUNDLE_PATH` 只供部署脚本使用，其余变量会写入
包内的 `app.env` 并传给 Spring Boot 容器。

`.env.lan` 会作为 Bash env 文件加载，因此带空格或 shell 特殊字符的值应使用
单引号，例如 `DB_PASSWORD='password with spaces'`。该文件包含密钥且不会被 Git
跟踪；镜像包同样包含运行时密钥，应只在可信局域网中传输并妥善删除。

本机需要 Maven、Docker、tar、ssh 和 scp。目标服务器需要 Bash、Docker 和 tar；
若开启健康检查，还需要 curl。建议提前配置 SSH key 登录：

```bash
ssh-copy-id deploy@192.168.31.100
```

#### 使用

构建可通过 SCP 传输的镜像包：

```bash
./scripts/lan-deploy.sh build
```

产物位于 `dist/lan/*.tar.gz`。只上传并部署最近一次构建的包：

```bash
./scripts/lan-deploy.sh deploy
```

一次完成构建、上传和部署：

```bash
./scripts/lan-deploy.sh all
```

也可以指定另一个配置文件：

```bash
./scripts/lan-deploy.sh all .env.lan.test-server
```

若希望手工 SCP，先上传包并在目标服务器执行：

```bash
mkdir -p /tmp/wms-cashier-lan
tar -xzf flyingjack-wms-cashier-1.0.0-rc.tar.gz -C /tmp/wms-cashier-lan
/tmp/wms-cashier-lan/deploy.sh /tmp/wms-cashier-lan \
  /tmp/wms-cashier-lan/flyingjack-wms-cashier-1.0.0-rc.tar.gz
```

脚本会加载镜像、替换同名旧容器、按 env 启动新容器并等待 Actuator 健康检查。
部署失败时会打印容器最近 100 行日志并保留容器用于排查。

---

## 部署前待办事项

- [ ] 在 `k8s-gitops` 仓库中为 `wms-cashier` 创建 K8s 部署清单（`base/` + `overlays/{beta,prod}/`）
- [ ] 在 K8s Secret 中配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- [ ] 在 Istio VirtualService 中为 `wms-cashier` 添加路由规则（如 `/api/wms/**` → `wms-cashier:8081`）
- [ ] 执行数据迁移 SQL（需先完成 auth-service 用户迁移并生成 `user_id_mapping`）
- [ ] 验证迁移数据完整性（见上方验证 SQL）
- [ ] 在 ArgoCD 中创建 wms-cashier Application 并指向 `k8s-gitops/wms-cashier/overlays/{profile}/`
