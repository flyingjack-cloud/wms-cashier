# wms-cashier 架构与迁移指南

## 概述

`wms-cashier` 是从旧单体 `wms_backend` 拆分出的 WMS 微服务，负责门店管理、商品、订单、分类、权限等业务逻辑。作为 OAuth2 Resource Server，它验证由 `auth-service` 签发的 JWT，不处理登录或注册流程。

- **端口：** 8081（dev）
- **技术栈：** Java 21 / Spring Boot 3.2.4 / MyBatis 3.0.3 / PostgreSQL

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

## 本地开发

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

## 待办事项（部署前必须完成）

- [ ] 在 `k8s-gitops` 仓库中为 `wms-cashier` 创建 K8s 部署清单（`base/` + `overlays/{beta,prod}/`）
- [ ] 在 K8s Secret 中配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- [ ] 在 Istio VirtualService 中为 `wms-cashier` 添加路由规则（如 `/api/wms/**` → `wms-cashier:8081`）
- [ ] 执行数据迁移 SQL（需先完成 auth-service 用户迁移并生成 `user_id_mapping`）
- [ ] 验证迁移数据完整性（见上方验证 SQL）
- [ ] 在 ArgoCD 中创建 wms-cashier Application 并指向 `k8s-gitops/wms-cashier/overlays/{profile}/`
