# Order Extra 模板管理接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 wms-cashier 补上 order-extra 模板管理接口，让 owner 能在线创建/修改/停用模板，打通此前因模板表恒为空而跑不通的票据链路。

**Architecture:** 在既有的 `OrderExtraController` / `OrderExtraService` / `OrderExtraMapper` 三层上扩展。schema 校验逻辑从 `OrderExtraService` 抽出为独立的 `OrderExtraSchemaValidator`，供"写模板时校验 schema 结构"和"填单时校验 payload"两处复用。模板更新走同行 UPDATE，仅当 schema 实际变化时 version+1。

**Tech Stack:** Java 21, Spring Boot 3.2.4, MyBatis, PostgreSQL (jsonb), TestContainers, JUnit 5, Mockito, AssertJ

**Spec:** `docs/superpowers/specs/2026-07-17-order-extra-template-admin-design.md`

## Global Constraints

- 分支必须是 `develop`，提交前用 `git branch --show-current` 确认（根 CLAUDE.md 规定，禁止直接提交 main）
- 时间字段一律用 `Instant`，数据库存 UTC+0
- 统一响应用 `ApiRes<T>`（`top.flyingjack.common.dto.ApiRes`），静态方法：`ApiRes.success(T data)` / `ApiRes.success()`
- 错误处理沿用现有写法：`org.springframework.util.Assert` 抛 `IllegalArgumentException`，由 common-lib 的 `GlobalExceptionHandler` 映射为 `INVALID_PARAM` → 400。**不要**为本次改动新增 `SysErrorCode` 枚举（与 `OrderService`/`GroupService`/`AuthorityService` 保持局部一致）
- Group 隔离：每个 mapper 查询都必须带 `securityContext.currentGroupId()`
- 写操作权限：`@PreAuthorize("hasRole('OWNER')")` 打在 **service 方法**上，与 `GroupService` 一致。方法级安全已由 `ResourceServerConfig` 的 `@EnableMethodSecurity(prePostEnabled = true)` 启用
- 模板 `code` 创建后不可修改
- 支持的字段类型枚举，逐字复制：`text` / `textarea` / `number` / `boolean` / `date` / `datetime` / `select`
- 运行测试：`cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=<TestClass>`

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `src/main/java/top/flyingjack/cashier/service/OrderExtraSchemaValidator.java` | 创建 | schema 结构校验 + payload 校验，无状态 `@Component` |
| `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateReq.java` | 创建 | 模板写入请求体 |
| `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateEnabledReq.java` | 创建 | 启用/停用请求体 |
| `src/main/java/top/flyingjack/cashier/mapper/OrderExtraMapper.java` | 修改 | 新增 4 个方法，删除 `findEnabledTemplates` |
| `src/main/resources/mapper/OrderExtraMapper.xml` | 修改 | 对应 SQL |
| `src/main/java/top/flyingjack/cashier/service/OrderExtraService.java` | 修改 | 模板 CRUD 编排 + 版本判定；移出校验逻辑 |
| `src/main/java/top/flyingjack/cashier/controller/OrderExtraController.java` | 修改 | 新增 5 个端点 |
| `src/test/java/top/flyingjack/cashier/service/OrderExtraSchemaValidatorTest.java` | 创建 | 校验器单测 |
| `src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java` | 修改 | 适配签名变化 + 模板 CRUD 单测 |
| `src/test/java/top/flyingjack/cashier/controller/OrderExtraControllerTest.java` | 修改 | 新端点 controller 测试 |
| `src/test/java/top/flyingjack/cashier/integration/OrderExtraIntegrationTest.java` | 创建 | TestContainers：jsonb 往返、唯一约束、版本落库 |
| `src/test/java/top/flyingjack/cashier/security/OrderExtraTemplateSecurityTest.java` | 创建 | `@PreAuthorize` 生效验证 |
| `pom.xml` | 修改 | 新增 `spring-security-test`（test scope） |
| `API.md` | 修改 | 管理接口文档 |

---

### Task 1: OrderExtraSchemaValidator（抽出校验 + 收紧 select options）

把 `OrderExtraService` 的私有校验方法搬到独立组件，新增 schema 结构校验，并收紧 select 取值。

**Files:**
- Create: `src/main/java/top/flyingjack/cashier/service/OrderExtraSchemaValidator.java`
- Create: `src/test/java/top/flyingjack/cashier/service/OrderExtraSchemaValidatorTest.java`
- Modify: `src/main/java/top/flyingjack/cashier/service/OrderExtraService.java`（删除 `validatePayload` / `validateFieldType`，改为委托）
- Modify: `src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java`（构造函数多一个参数）

**Interfaces:**
- Produces:
  - `OrderExtraSchemaValidator.validateSchema(JsonNode schema)` → `void`，非法时抛 `IllegalArgumentException`
  - `OrderExtraSchemaValidator.validatePayload(JsonNode schema, JsonNode payload)` → `void`，非法时抛 `IllegalArgumentException`
- Consumes: 无（第一个任务）

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/top/flyingjack/cashier/service/OrderExtraSchemaValidatorTest.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderExtraSchemaValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderExtraSchemaValidator validator = new OrderExtraSchemaValidator();

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void validateSchema_acceptsValidSchema() {
        JsonNode schema = json("{\"fields\":["
                + "{\"key\":\"invoiceTitle\",\"label\":\"发票抬头\",\"type\":\"text\",\"required\":true},"
                + "{\"key\":\"payMethod\",\"type\":\"select\",\"options\":[\"现金\",\"微信\"]}"
                + "]}");

        assertThatCode(() -> validator.validateSchema(schema)).doesNotThrowAnyException();
    }

    @Test
    void validateSchema_rejectsNonObject() {
        assertThatThrownBy(() -> validator.validateSchema(json("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema must be a JSON object");
    }

    @Test
    void validateSchema_rejectsMissingFields() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"foo\":1}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields must be an array");
    }

    @Test
    void validateSchema_rejectsEmptyFields() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"fields\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields cannot be empty");
    }

    @Test
    void validateSchema_rejectsBlankKey() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template field key cannot be empty");
    }

    @Test
    void validateSchema_rejectsDuplicateKey() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"fields\":["
                + "{\"key\":\"a\",\"type\":\"text\"},{\"key\":\"a\",\"type\":\"number\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate template field key: a");
    }

    @Test
    void validateSchema_rejectsUnsupportedType() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"foo\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported field type: foo");
    }

    @Test
    void validateSchema_rejectsSelectWithoutOptions() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"select\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select field must have options: a");
    }

    @Test
    void validateSchema_rejectsSelectWithEmptyOptions() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"select\",\"options\":[]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select field must have options: a");
    }

    @Test
    void validatePayload_rejectsMissingRequiredField() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\",\"required\":true}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field: a");
    }

    @Test
    void validatePayload_rejectsBlankRequiredText() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\",\"required\":true}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"   \"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field: a");
    }

    @Test
    void validatePayload_rejectsWrongNumberType() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"number\"}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field must be number: a");
    }

    @Test
    void validatePayload_rejectsWrongBooleanType() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"boolean\"}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field must be boolean: a");
    }

    @Test
    void validatePayload_acceptsSelectValueInOptions() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"payMethod\",\"type\":\"select\","
                + "\"options\":[\"现金\",\"微信\"]}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"payMethod\":\"微信\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePayload_rejectsSelectValueNotInOptions() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"payMethod\",\"type\":\"select\","
                + "\"options\":[\"现金\",\"微信\"]}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"payMethod\":\"比特币\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid option for field: payMethod");
    }

    @Test
    void validatePayload_toleratesUndeclaredFields() {
        // 显式决策：schema 未声明的字段原样保留，不报错（见 spec"显式决策"章节）
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"a\":\"x\",\"b\":\"y\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePayload_skipsTypeCheckForNullValue() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"number\",\"required\":false}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"a\":null}")))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraSchemaValidatorTest`
Expected: 编译失败 —— `cannot find symbol: class OrderExtraSchemaValidator`

- [ ] **Step 3: 写实现**

创建 `src/main/java/top/flyingjack/cashier/service/OrderExtraSchemaValidator.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Component
public class OrderExtraSchemaValidator {
    private static final Set<String> SUPPORTED_TYPES =
            Set.of("text", "textarea", "number", "boolean", "date", "datetime", "select");

    public void validateSchema(JsonNode schema) {
        Assert.isTrue(schema != null && schema.isObject(), "schema must be a JSON object");
        JsonNode fields = schema.path("fields");
        Assert.isTrue(fields.isArray(), "template fields must be an array");
        Assert.isTrue(!fields.isEmpty(), "template fields cannot be empty");
        Set<String> keys = new HashSet<>();
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Assert.hasText(key, "template field key cannot be empty");
            Assert.isTrue(keys.add(key), "duplicate template field key: " + key);
            String type = field.path("type").asText("text");
            Assert.isTrue(SUPPORTED_TYPES.contains(type), "unsupported field type: " + type);
            if ("select".equals(type)) {
                assertSelectOptions(key, field);
            }
        }
    }

    public void validatePayload(JsonNode schema, JsonNode payload) {
        JsonNode fields = schema.path("fields");
        Assert.isTrue(fields.isArray(), "template fields must be an array");
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Assert.hasText(key, "template field key cannot be empty");
            JsonNode value = payload.get(key);
            boolean required = field.path("required").asBoolean(false);
            if (required) {
                Assert.isTrue(value != null && !value.isNull()
                                && !(value.isTextual() && !StringUtils.hasText(value.asText())),
                        "missing required field: " + key);
            }
            if (value != null && !value.isNull()) {
                validateFieldType(key, field, value);
            }
        }
    }

    private void validateFieldType(String key, JsonNode field, JsonNode value) {
        switch (field.path("type").asText("text")) {
            case "number":
                Assert.isTrue(value.isNumber(), "field must be number: " + key);
                break;
            case "boolean":
                Assert.isTrue(value.isBoolean(), "field must be boolean: " + key);
                break;
            case "select":
                Assert.isTrue(value.isTextual(), "field must be text: " + key);
                validateSelectValue(key, field, value);
                break;
            case "text":
            case "textarea":
            case "date":
            case "datetime":
                Assert.isTrue(value.isTextual(), "field must be text: " + key);
                break;
            default:
                break;
        }
    }

    private void validateSelectValue(String key, JsonNode field, JsonNode value) {
        JsonNode options = assertSelectOptions(key, field);
        for (JsonNode option : options) {
            if (option.asText().equals(value.asText())) {
                return;
            }
        }
        throw new IllegalArgumentException("invalid option for field: " + key);
    }

    private JsonNode assertSelectOptions(String key, JsonNode field) {
        JsonNode options = field.path("options");
        Assert.isTrue(options.isArray() && !options.isEmpty(), "select field must have options: " + key);
        return options;
    }
}
```

注意 `validateSelectValue` 复用 `assertSelectOptions` 而非静默跳过：手工 INSERT 绕过管理接口的模板若缺 options，应在填单时明确报错而不是放行。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraSchemaValidatorTest`
Expected: PASS，17 个测试全绿

- [ ] **Step 5: 让 OrderExtraService 委托给校验器**

修改 `OrderExtraService.java`：

1. 加字段与构造参数：

```java
    private final OrderExtraSchemaValidator schemaValidator;

    public OrderExtraService(OrderExtraMapper orderExtraMapper, OrderMapper orderMapper,
                             WmsSecurityContext securityContext, ObjectMapper objectMapper,
                             OrderExtraSchemaValidator schemaValidator) {
        this.orderExtraMapper = orderExtraMapper;
        this.orderMapper = orderMapper;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }
```

2. `saveExtra` 中把 `validatePayload(template, payloadNode);` 替换为：

```java
        schemaValidator.validatePayload(readJson(template.getSchemaJson()), payloadNode);
```

3. **删除** `OrderExtraService` 中的 `private void validatePayload(...)` 和 `private void validateFieldType(...)` 两个方法整体，以及不再需要的 `import org.springframework.util.StringUtils`（原代码用的是全限定名 `org.springframework.util.StringUtils.hasText`，删掉方法即可，无 import 需清理）。

- [ ] **Step 6: 修复既有单测的构造调用**

修改 `src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java` 的 `setUp()`：

```java
    @BeforeEach
    void setUp() {
        orderExtraMapper = mock(OrderExtraMapper.class);
        orderMapper = mock(OrderMapper.class);
        securityContext = mock(WmsSecurityContext.class);
        orderExtraService = new OrderExtraService(orderExtraMapper, orderMapper, securityContext,
                new ObjectMapper(), new OrderExtraSchemaValidator());
        when(securityContext.currentGroupId()).thenReturn(1);
    }
```

用真实 validator 而非 mock —— 它是无状态纯函数，mock 反而会掩盖校验行为。

- [ ] **Step 7: 运行两个测试类确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest='OrderExtraSchemaValidatorTest,OrderExtraServiceTest'`
Expected: PASS，全绿（`saveExtra_rejectsMissingRequiredField` 仍应通过，证明搬迁未改变行为）

- [ ] **Step 8: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/service/OrderExtraSchemaValidator.java \
        src/main/java/top/flyingjack/cashier/service/OrderExtraService.java \
        src/test/java/top/flyingjack/cashier/service/OrderExtraSchemaValidatorTest.java \
        src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java
git commit -m "refactor: extract OrderExtraSchemaValidator and enforce select options"
```

---

### Task 2: Mapper 新增方法 + SQL + 集成测试

补齐模板管理所需的 mapper 方法，并用 TestContainers 覆盖此前零集成测试的 jsonb / 约束 / upsert 路径。

**Files:**
- Modify: `src/main/java/top/flyingjack/cashier/mapper/OrderExtraMapper.java`
- Modify: `src/main/resources/mapper/OrderExtraMapper.xml`
- Modify: `src/main/java/top/flyingjack/cashier/service/OrderExtraService.java`（跟上 `findEnabledTemplates` 的删除）
- Modify: `src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java`（同上）
- Create: `src/test/java/top/flyingjack/cashier/integration/OrderExtraIntegrationTest.java`

**Interfaces:**
- Consumes: `OrderExtraTemplate` / `OrderExtra` 实体（已存在，字段见 `entity/` 包）
- Produces:
  - `OrderExtraMapper.findTemplates(int groupId, boolean includeDisabled)` → `List<OrderExtraTemplate>`
  - `OrderExtraMapper.findTemplateByCode(int groupId, String code)` → `OrderExtraTemplate`（不过滤 enabled，无则 null）
  - `OrderExtraMapper.updateTemplate(OrderExtraTemplate template)` → `void`
  - `OrderExtraMapper.updateEnabled(int groupId, String code, boolean enabled)` → `void`
  - `findEnabledTemplates(int)` **被删除**，调用方改用 `findTemplates(groupId, false)`

- [ ] **Step 1: 写失败的集成测试**

创建 `src/test/java/top/flyingjack/cashier/integration/OrderExtraIntegrationTest.java`：

```java
package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.OrderExtraMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderExtraIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired OrderExtraMapper orderExtraMapper;

    private static final String SCHEMA =
            "{\"fields\":[{\"key\":\"invoiceTitle\",\"type\":\"text\",\"required\":true}]}";

    private OrderExtraTemplate template(int groupId, String code) {
        OrderExtraTemplate t = new OrderExtraTemplate();
        t.setGroupId(groupId);
        t.setCode(code);
        t.setName("发票信息");
        t.setVersion(1);
        t.setSchemaJson(SCHEMA);
        t.setEnabled(true);
        return t;
    }

    @Test
    void insertTemplate_roundTripsJsonbAndAssignsId() {
        OrderExtraTemplate t = template(900, "invoice");
        orderExtraMapper.insertTemplate(t);

        assertThat(t.getId()).isGreaterThan(0);

        OrderExtraTemplate found = orderExtraMapper.findTemplateByCode(900, "invoice");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("发票信息");
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getSchemaJson()).contains("invoiceTitle");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void insertTemplate_rejectsDuplicateCodeInSameGroup() {
        orderExtraMapper.insertTemplate(template(901, "invoice"));

        assertThatThrownBy(() -> orderExtraMapper.insertTemplate(template(901, "invoice")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertTemplate_allowsSameCodeInDifferentGroups() {
        orderExtraMapper.insertTemplate(template(902, "invoice"));
        orderExtraMapper.insertTemplate(template(903, "invoice"));

        assertThat(orderExtraMapper.findTemplateByCode(902, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplateByCode(903, "invoice")).isNotNull();
    }

    @Test
    void updateTemplate_persistsNameSchemaAndVersion() {
        OrderExtraTemplate t = template(904, "invoice");
        orderExtraMapper.insertTemplate(t);

        t.setName("增值税发票");
        t.setSchemaJson("{\"fields\":[{\"key\":\"taxNo\",\"type\":\"text\"}]}");
        t.setVersion(2);
        orderExtraMapper.updateTemplate(t);

        OrderExtraTemplate found = orderExtraMapper.findTemplateByCode(904, "invoice");
        assertThat(found.getName()).isEqualTo("增值税发票");
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getSchemaJson()).contains("taxNo").doesNotContain("invoiceTitle");
    }

    @Test
    void updateEnabled_hidesTemplateFromEnabledQueries() {
        orderExtraMapper.insertTemplate(template(905, "invoice"));

        orderExtraMapper.updateEnabled(905, "invoice", false);

        assertThat(orderExtraMapper.findEnabledTemplateByCode(905, "invoice")).isNull();
        assertThat(orderExtraMapper.findTemplateByCode(905, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplates(905, false)).isEmpty();
        assertThat(orderExtraMapper.findTemplates(905, true)).hasSize(1);
    }

    @Test
    void updateEnabled_reenablesTemplate() {
        orderExtraMapper.insertTemplate(template(906, "invoice"));
        orderExtraMapper.updateEnabled(906, "invoice", false);

        orderExtraMapper.updateEnabled(906, "invoice", true);

        assertThat(orderExtraMapper.findEnabledTemplateByCode(906, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplates(906, false)).hasSize(1);
    }

    @Test
    void upsertExtra_insertsThenUpdatesOnConflict() {
        OrderExtra extra = new OrderExtra();
        extra.setGroupId(907);
        extra.setOrderId(1);
        extra.setTemplateId(null);
        extra.setTemplateCode("invoice");
        extra.setTemplateName("发票信息");
        extra.setTemplateVersion(1);
        extra.setPayload("{\"invoiceTitle\":\"甲公司\"}");
        orderExtraMapper.upsertExtra(extra);

        extra.setPayload("{\"invoiceTitle\":\"乙公司\"}");
        extra.setTemplateVersion(2);
        orderExtraMapper.upsertExtra(extra);

        List<OrderExtra> found = orderExtraMapper.findExtrasByOrderId(907, 1);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getPayload()).contains("乙公司");
        assertThat(found.get(0).getTemplateVersion()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraIntegrationTest`
Expected: 编译失败 —— `cannot find symbol: method findTemplateByCode` / `findTemplates` / `updateTemplate` / `updateEnabled`

- [ ] **Step 3: 加 mapper 接口方法**

修改 `src/main/java/top/flyingjack/cashier/mapper/OrderExtraMapper.java`，整个接口替换为：

```java
package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraTemplate;

import java.util.List;

@Mapper
public interface OrderExtraMapper {
    List<OrderExtraTemplate> findTemplates(@Param("groupId") int groupId,
                                           @Param("includeDisabled") boolean includeDisabled);
    OrderExtraTemplate findEnabledTemplateByCode(@Param("groupId") int groupId, @Param("code") String code);
    OrderExtraTemplate findTemplateByCode(@Param("groupId") int groupId, @Param("code") String code);
    void insertTemplate(OrderExtraTemplate template);
    void updateTemplate(OrderExtraTemplate template);
    void updateEnabled(@Param("groupId") int groupId, @Param("code") String code,
                       @Param("enabled") boolean enabled);
    void upsertExtra(OrderExtra extra);
    List<OrderExtra> findExtrasByOrderId(@Param("groupId") int groupId, @Param("orderId") int orderId);
    OrderExtra findExtraByOrderIdAndCode(@Param("groupId") int groupId, @Param("orderId") int orderId,
                                         @Param("templateCode") String templateCode);
}
```

`findEnabledTemplates` 已删除 —— 由 `findTemplates(groupId, false)` 取代，避免两条几乎相同的 SQL。

- [ ] **Step 4: 改 SQL**

修改 `src/main/resources/mapper/OrderExtraMapper.xml`。把原 `findEnabledTemplates` 的 `<select>` 块**整体替换**为下面的 `findTemplates`，并在 `insertTemplate` 之后追加 `updateTemplate` / `updateEnabled` / `findTemplateByCode`：

```xml
    <select id="findTemplates" resultType="top.flyingjack.cashier.entity.OrderExtraTemplate">
        SELECT id, group_id, code, name, version, schema_json::text AS schema_json,
               enabled, created_at, updated_at
        FROM wms_order_extra_template
        WHERE group_id = #{groupId}
        <if test="!includeDisabled">
            AND enabled = TRUE
        </if>
        ORDER BY id
    </select>

    <select id="findTemplateByCode" resultType="top.flyingjack.cashier.entity.OrderExtraTemplate">
        SELECT id, group_id, code, name, version, schema_json::text AS schema_json,
               enabled, created_at, updated_at
        FROM wms_order_extra_template
        WHERE group_id = #{groupId} AND code = #{code}
    </select>

    <update id="updateTemplate">
        UPDATE wms_order_extra_template
        SET name = #{name},
            schema_json = #{schemaJson}::jsonb,
            version = #{version},
            updated_at = NOW()
        WHERE id = #{id} AND group_id = #{groupId}
    </update>

    <update id="updateEnabled">
        UPDATE wms_order_extra_template
        SET enabled = #{enabled},
            updated_at = NOW()
        WHERE group_id = #{groupId} AND code = #{code}
    </update>
```

`updateTemplate` 的 WHERE 同时带 `group_id`，防止跨 group 改写。

- [ ] **Step 5: 跟上 `findEnabledTemplates` 的删除**

上一步删掉了 `findEnabledTemplates`，此刻 `OrderExtraService.getTemplates()` 仍在调用它 —— **主源码编译不过，集成测试无法运行**。本步只做让编译恢复的最小改动，签名变更留给 Task 3。

修改 `OrderExtraService.getTemplates()`：

```java
    public List<OrderExtraTemplateDto> getTemplates() {
        return orderExtraMapper.findTemplates(securityContext.currentGroupId(), false).stream()
                .map(this::toTemplateDto)
                .toList();
    }
```

同步修改 `OrderExtraServiceTest.getTemplates_returnsSchemaAsJson()` 的 mock：

```java
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of(template()));
```

（该测试对 `getTemplates()` 的调用暂不变，Task 3 再改为 `getTemplates(false)`。）

- [ ] **Step 6: 运行集成测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest='OrderExtraIntegrationTest,OrderExtraServiceTest'`
Expected: PASS，集成测试 7 个 + 既有 service 测试全绿。若报 Docker/Testcontainers 版本错误，见 `pom.xml` 中 surefire 的 `api.version` / `DOCKER_HOST` 配置。

- [ ] **Step 7: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/mapper/OrderExtraMapper.java \
        src/main/resources/mapper/OrderExtraMapper.xml \
        src/main/java/top/flyingjack/cashier/service/OrderExtraService.java \
        src/test/java/top/flyingjack/cashier/integration/OrderExtraIntegrationTest.java \
        src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java
git commit -m "feat: add order extra template mapper queries with integration coverage"
```

---

### Task 3: Service 模板 CRUD 与版本判定

**Files:**
- Modify: `src/main/java/top/flyingjack/cashier/service/OrderExtraService.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateReq.java`
- Modify: `src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `OrderExtraSchemaValidator.validateSchema(JsonNode)`；Task 2 的 `findTemplates` / `findTemplateByCode` / `updateTemplate` / `updateEnabled` / `insertTemplate`
- Produces:
  - `OrderExtraService.getTemplates(boolean includeDisabled)` → `List<OrderExtraTemplateDto>`（**签名变更**，原为无参）
  - `OrderExtraService.createTemplate(OrderExtraTemplateReq req)` → `OrderExtraTemplateDto`
  - `OrderExtraService.updateTemplate(String code, OrderExtraTemplateReq req)` → `OrderExtraTemplateDto`
  - `OrderExtraService.setTemplateEnabled(String code, boolean enabled)` → `OrderExtraTemplateDto`
  - `OrderExtraService.disableTemplate(String code)` → `void`
  - `OrderExtraTemplateReq`：`getCode()` / `setCode(String)`、`getName()` / `setName(String)`、`getSchema()` / `setSchema(JsonNode)`

- [ ] **Step 1: 建请求 DTO**

创建 `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateReq.java`：

```java
package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderExtraTemplateReq {
    private String code;
    private String name;
    private JsonNode schema;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public JsonNode getSchema() { return schema; }
    public void setSchema(JsonNode schema) { this.schema = schema; }
}
```

- [ ] **Step 2: 写失败的测试**

在 `OrderExtraServiceTest.java` 中，先把已有的 `getTemplates_returnsSchemaAsJson` 改为新签名：

```java
    @Test
    void getTemplates_returnsSchemaAsJson() {
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of(template()));

        List<OrderExtraTemplateDto> result = orderExtraService.getTemplates(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("invoice");
        assertThat(result.get(0).getSchema().path("fields").get(0).path("key").asText())
                .isEqualTo("invoiceTitle");
    }
```

再追加以下测试（需补 import：`top.flyingjack.cashier.entity.OrderExtraTemplateReq`、`com.fasterxml.jackson.databind.JsonNode`、`com.fasterxml.jackson.core.JsonProcessingException`）：

```java
    private JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private OrderExtraTemplateReq req(String code, String name, String schema) {
        OrderExtraTemplateReq r = new OrderExtraTemplateReq();
        r.setCode(code);
        r.setName(name);
        r.setSchema(json(schema));
        return r;
    }

    @Test
    void createTemplate_insertsWithVersionOne() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(null);

        orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}"));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).insertTemplate(captor.capture());
        OrderExtraTemplate saved = captor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(1);
        assertThat(saved.getCode()).isEqualTo("invoice");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getSchemaJson()).contains("\"key\":\"a\"");
    }

    @Test
    void createTemplate_rejectsDuplicateCode() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        assertThatThrownBy(() -> orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template already exists: invoice");

        verify(orderExtraMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsInvalidSchema() {
        assertThatThrownBy(() -> orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields cannot be empty");

        verify(orderExtraMapper, never()).insertTemplate(any());
    }

    @Test
    void updateTemplate_bumpsVersionWhenSchemaChanges() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        OrderExtraTemplateDto result = orderExtraService.updateTemplate("invoice",
                req(null, "发票信息", "{\"fields\":[{\"key\":\"newKey\",\"type\":\"text\"}]}"));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void updateTemplate_keepsVersionWhenOnlyNameChanges() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        // schema 与 template() 的 schemaJson 语义相同，仅键顺序与空白不同
        String sameSchema = "{\"fields\":[ "
                + "{\"type\":\"text\",\"key\":\"invoiceTitle\",\"required\":true,\"label\":\"发票抬头\"},"
                + "{\"required\":false,\"key\":\"taxNo\",\"type\":\"text\",\"label\":\"税号\"} ]}";

        OrderExtraTemplateDto result = orderExtraService.updateTemplate("invoice",
                req(null, "增值税发票", sameSchema));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getName()).isEqualTo("增值税发票");
        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void updateTemplate_rejectsUnknownCode() {
        when(orderExtraMapper.findTemplateByCode(1, "ghost")).thenReturn(null);

        assertThatThrownBy(() -> orderExtraService.updateTemplate("ghost",
                req(null, "x", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template not found: ghost");

        verify(orderExtraMapper, never()).updateTemplate(any());
    }

    @Test
    void disableTemplate_setsEnabledFalse() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        orderExtraService.disableTemplate("invoice");

        verify(orderExtraMapper).updateEnabled(1, "invoice", false);
    }

    @Test
    void setTemplateEnabled_rejectsUnknownCode() {
        when(orderExtraMapper.findTemplateByCode(1, "ghost")).thenReturn(null);

        assertThatThrownBy(() -> orderExtraService.setTemplateEnabled("ghost", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template not found: ghost");

        verify(orderExtraMapper, never()).updateEnabled(anyInt(), anyString(), anyBoolean());
    }
```

`updateTemplate_keepsVersionWhenOnlyNameChanges` 是版本语义的核心断言：schema 语义相同但格式不同时**不得**升版本。它同时验证了用 `JsonNode.equals` 而非字符串比较的必要性。

- [ ] **Step 3: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraServiceTest`
Expected: 编译失败 —— `cannot find symbol: method createTemplate`

- [ ] **Step 4: 写实现**

修改 `OrderExtraService.java`：

1. 补 import：

```java
import org.springframework.security.access.prepost.PreAuthorize;
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
```

2. 把 `getTemplates()` 替换为：

```java
    @PreAuthorize("!#includeDisabled or hasRole('OWNER')")
    public List<OrderExtraTemplateDto> getTemplates(boolean includeDisabled) {
        return orderExtraMapper.findTemplates(securityContext.currentGroupId(), includeDisabled).stream()
                .map(this::toTemplateDto)
                .toList();
    }
```

3. 在 `getTemplate(String code)` 之后追加模板 CRUD：

```java
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public OrderExtraTemplateDto createTemplate(OrderExtraTemplateReq req) {
        Assert.hasText(req.getCode(), "template code cannot be empty");
        Assert.hasText(req.getName(), "template name cannot be empty");
        schemaValidator.validateSchema(req.getSchema());
        int groupId = securityContext.currentGroupId();
        Assert.isNull(orderExtraMapper.findTemplateByCode(groupId, req.getCode()),
                "template already exists: " + req.getCode());

        OrderExtraTemplate template = new OrderExtraTemplate();
        template.setGroupId(groupId);
        template.setCode(req.getCode());
        template.setName(req.getName());
        template.setVersion(1);
        template.setSchemaJson(writeJson(req.getSchema()));
        template.setEnabled(true);
        orderExtraMapper.insertTemplate(template);
        return toTemplateDto(template);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public OrderExtraTemplateDto updateTemplate(String code, OrderExtraTemplateReq req) {
        Assert.hasText(code, "template code cannot be empty");
        Assert.hasText(req.getName(), "template name cannot be empty");
        schemaValidator.validateSchema(req.getSchema());
        int groupId = securityContext.currentGroupId();
        OrderExtraTemplate existing = orderExtraMapper.findTemplateByCode(groupId, code);
        Assert.notNull(existing, "template not found: " + code);

        // 结构化比较：不受键顺序与空白影响，避免纯格式改动误升版本
        boolean schemaChanged = !readJson(existing.getSchemaJson()).equals(req.getSchema());
        existing.setName(req.getName());
        existing.setSchemaJson(writeJson(req.getSchema()));
        if (schemaChanged) {
            existing.setVersion(existing.getVersion() + 1);
        }
        orderExtraMapper.updateTemplate(existing);
        return toTemplateDto(existing);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public OrderExtraTemplateDto setTemplateEnabled(String code, boolean enabled) {
        Assert.hasText(code, "template code cannot be empty");
        int groupId = securityContext.currentGroupId();
        OrderExtraTemplate existing = orderExtraMapper.findTemplateByCode(groupId, code);
        Assert.notNull(existing, "template not found: " + code);

        orderExtraMapper.updateEnabled(groupId, code, enabled);
        existing.setEnabled(enabled);
        return toTemplateDto(existing);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void disableTemplate(String code) {
        setTemplateEnabled(code, false);
    }
```

注意 `disableTemplate` 内部直接调用 `setTemplateEnabled` 是普通方法调用（不走代理），因此 `setTemplateEnabled` 的 `@PreAuthorize` 不会二次生效 —— 但 `disableTemplate` 自身已带同样的注解，权限不会被绕过。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraServiceTest`
Expected: PASS，全绿

- [ ] **Step 6: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateReq.java \
        src/main/java/top/flyingjack/cashier/service/OrderExtraService.java \
        src/test/java/top/flyingjack/cashier/service/OrderExtraServiceTest.java
git commit -m "feat: add order extra template CRUD service with version bump semantics"
```

---

### Task 4: Controller 端点

**Files:**
- Modify: `src/main/java/top/flyingjack/cashier/controller/OrderExtraController.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateEnabledReq.java`
- Modify: `src/test/java/top/flyingjack/cashier/controller/OrderExtraControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的 `createTemplate` / `updateTemplate` / `setTemplateEnabled` / `disableTemplate` / `getTemplates(boolean)`
- Produces: HTTP 端点，见下表

| Method | Path | 响应 |
|---|---|---|
| `GET` | `/order-extra/templates?includeDisabled=false` | `ApiRes<List<OrderExtraTemplateDto>>` |
| `POST` | `/order-extra/templates` | `ApiRes<OrderExtraTemplateDto>` |
| `PUT` | `/order-extra/templates/{code}` | `ApiRes<OrderExtraTemplateDto>` |
| `DELETE` | `/order-extra/templates/{code}` | `ApiRes<Void>` |
| `PUT` | `/order-extra/templates/{code}/enabled` | `ApiRes<OrderExtraTemplateDto>` |

- [ ] **Step 1: 建 enabled 请求 DTO**

创建 `src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateEnabledReq.java`：

```java
package top.flyingjack.cashier.entity;

public class OrderExtraTemplateEnabledReq {
    private boolean enabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

- [ ] **Step 2: 写失败的测试**

在 `OrderExtraControllerTest.java` 追加（需补 import：`org.springframework.http.MediaType` 已有；新增 `static ...MockMvcRequestBuilders.post`、`static ...MockMvcRequestBuilders.delete`、`top.flyingjack.cashier.entity.OrderExtraTemplateReq`、`org.mockito.ArgumentCaptor`）：

```java
    @Test
    void getTemplates_defaultsToEnabledOnly() throws Exception {
        mockMvc.perform(get("/order-extra/templates"))
                .andExpect(status().isOk());

        verify(orderExtraService).getTemplates(false);
    }

    @Test
    void getTemplates_passesIncludeDisabled() throws Exception {
        mockMvc.perform(get("/order-extra/templates").param("includeDisabled", "true"))
                .andExpect(status().isOk());

        verify(orderExtraService).getTemplates(true);
    }

    @Test
    void createTemplate_passesReqToService() throws Exception {
        mockMvc.perform(post("/order-extra/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invoice\",\"name\":\"发票信息\","
                                + "\"schema\":{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderExtraTemplateReq> captor =
                ArgumentCaptor.forClass(OrderExtraTemplateReq.class);
        verify(orderExtraService).createTemplate(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("invoice");
        assertThat(captor.getValue().getName()).isEqualTo("发票信息");
        assertThat(captor.getValue().getSchema().path("fields").get(0).path("key").asText())
                .isEqualTo("a");
    }

    @Test
    void updateTemplate_passesCodeFromPath() throws Exception {
        mockMvc.perform(put("/order-extra/templates/invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"增值税发票\","
                                + "\"schema\":{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderExtraTemplateReq> captor =
                ArgumentCaptor.forClass(OrderExtraTemplateReq.class);
        verify(orderExtraService).updateTemplate(eq("invoice"), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("增值税发票");
    }

    @Test
    void deleteTemplate_disablesIt() throws Exception {
        mockMvc.perform(delete("/order-extra/templates/invoice"))
                .andExpect(status().isOk());

        verify(orderExtraService).disableTemplate("invoice");
    }

    @Test
    void setEnabled_passesFlagToService() throws Exception {
        mockMvc.perform(put("/order-extra/templates/invoice/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(orderExtraService).setTemplateEnabled("invoice", true);
    }
```

补 import：`static org.mockito.ArgumentMatchers.eq`。

- [ ] **Step 3: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraControllerTest`
Expected: 编译失败 —— `cannot find symbol: method createTemplate`

- [ ] **Step 4: 写实现**

修改 `OrderExtraController.java`。把已有的 `getTemplates()` 替换为带参版本，并追加 4 个端点：

```java
    @GetMapping("/order-extra/templates")
    public ResponseEntity<ApiRes<List<OrderExtraTemplateDto>>> getTemplates(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getTemplates(includeDisabled)));
    }

    @PostMapping("/order-extra/templates")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> createTemplate(
            @RequestBody OrderExtraTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.createTemplate(req)));
    }

    @PutMapping("/order-extra/templates/{code}")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> updateTemplate(
            @PathVariable String code, @RequestBody OrderExtraTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.updateTemplate(code, req)));
    }

    @DeleteMapping("/order-extra/templates/{code}")
    public ResponseEntity<ApiRes<Void>> deleteTemplate(@PathVariable String code) {
        orderExtraService.disableTemplate(code);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/order-extra/templates/{code}/enabled")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> setTemplateEnabled(
            @PathVariable String code, @RequestBody OrderExtraTemplateEnabledReq req) {
        return ResponseEntity.ok(ApiRes.success(
                orderExtraService.setTemplateEnabled(code, req.isEnabled())));
    }
```

补 import：`top.flyingjack.cashier.entity.OrderExtraTemplateReq`、`top.flyingjack.cashier.entity.OrderExtraTemplateEnabledReq`。（`org.springframework.web.bind.annotation.*` 已是通配 import，无需为新注解补充。）

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraControllerTest`
Expected: PASS，全绿

- [ ] **Step 6: 全量测试**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier`
Expected: PASS，全部测试类全绿

- [ ] **Step 7: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/entity/OrderExtraTemplateEnabledReq.java \
        src/main/java/top/flyingjack/cashier/controller/OrderExtraController.java \
        src/test/java/top/flyingjack/cashier/controller/OrderExtraControllerTest.java
git commit -m "feat: expose order extra template admin endpoints"
```

---

### Task 5: 权限验证测试

验证 `@PreAuthorize` 真的生效。**这个任务不可省略**：SpEL 的 `#includeDisabled` 依赖编译期保留参数名，若未保留，表达式会静默求值失败而非报错，导致权限形同虚设。

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/top/flyingjack/cashier/security/OrderExtraTemplateSecurityTest.java`

**Interfaces:**
- Consumes: Task 3 的全部 service 方法

- [ ] **Step 1: 加 spring-security-test 依赖**

修改 `pom.xml`，在 `<dependencies>` 中追加（当前仓库尚无此依赖，全仓库也无 `@WithMockUser` 用法）：

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

版本由 `spring-boot-starter-parent` 管理，不要写死。

- [ ] **Step 2: 写测试**

创建 `src/test/java/top/flyingjack/cashier/security/OrderExtraTemplateSecurityTest.java`：

```java
package top.flyingjack.cashier.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.service.OrderExtraService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderExtraTemplateSecurityTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean WmsSecurityContext securityContext;

    @Autowired OrderExtraService orderExtraService;

    private OrderExtraTemplateReq req() {
        OrderExtraTemplateReq r = new OrderExtraTemplateReq();
        r.setCode("sec-test");
        r.setName("测试");
        try {
            r.setSchema(new ObjectMapper().readTree("{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_allowedForNonOwnerWhenIncludeDisabledFalse() {
        when(securityContext.currentGroupId()).thenReturn(950);

        // 这条断言同时验证 SpEL 参数名解析生效：若 #includeDisabled 无法解析，
        // 表达式不会短路放行，此调用会被误拒
        assertThatCode(() -> orderExtraService.getTemplates(false)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_deniedForNonOwnerWhenIncludeDisabledTrue() {
        assertThatThrownBy(() -> orderExtraService.getTemplates(true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void getTemplates_allowedForOwnerWhenIncludeDisabledTrue() {
        when(securityContext.currentGroupId()).thenReturn(951);

        assertThatCode(() -> orderExtraService.getTemplates(true)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void createTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.createTemplate(req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void updateTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.updateTemplate("sec-test", req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void disableTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.disableTemplate("sec-test"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void setTemplateEnabled_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.setTemplateEnabled("sec-test", true))
                .isInstanceOf(AccessDeniedException.class);
    }
}
```

`WmsSecurityContext` 用 `@MockBean` 替换，避免测试需要构造真实 JWT 与 profile 行；`@WithMockUser(roles = ...)` 提供角色，`hasRole('OWNER')` 会匹配 `ROLE_OWNER` 权限。

- [ ] **Step 3: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=OrderExtraTemplateSecurityTest`
Expected: PASS，7 个测试全绿。

若 `getTemplates_allowedForNonOwnerWhenIncludeDisabledFalse` 抛 `AccessDeniedException` 或 SpEL 求值异常，说明参数名未保留 —— 改用位置引用：`@PreAuthorize("!#p0 or hasRole('OWNER')")`，重跑。

- [ ] **Step 4: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add pom.xml src/test/java/top/flyingjack/cashier/security/OrderExtraTemplateSecurityTest.java
git commit -m "test: verify order extra template authorization rules"
```

---

### Task 6: API.md 文档

**Files:**
- Modify: `API.md`

- [ ] **Step 1: 改写模板读取接口章节**

在 `API.md` 的 "Order Extra" 章节，把 `### GET /order-extra/templates` 小节的表格替换为：

```markdown
| | |
|---|---|
| Auth | Required；`includeDisabled=true` 时需 `ROLE_OWNER` |
| Query | `includeDisabled: boolean` — 默认 `false`。为 `true` 时同时返回已停用模板，供管理页使用 |
```

- [ ] **Step 2: 追加管理接口小节**

在 `### GET /order-extra/templates/{code}` 小节之后、`### PUT /order/{orderId}/extra/{templateCode}` 之前插入：

````markdown
### POST `/order-extra/templates`
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

### PUT `/order-extra/templates/{code}`
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

### DELETE `/order-extra/templates/{code}`
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

### PUT `/order-extra/templates/{code}/enabled`
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

### Template schema rules

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
````

- [ ] **Step 3: 人工核对**

通读改动后的 Order Extra 章节，确认：
- 端点路径与 Task 4 实现一致
- 字段类型枚举与 `OrderExtraSchemaValidator.SUPPORTED_TYPES` 一致
- 未声明字段的容忍行为已写明

- [ ] **Step 4: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add API.md
git commit -m "docs: document order extra template admin endpoints"
```

---

## 收尾检查

- [ ] `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier` 全绿
- [ ] `git log --oneline -6` 显示 6 次提交，均在 `develop`
- [ ] 手工冒烟：启动服务（`AUTH_ISSUER_URI` 默认 `http://localhost:9001`，端口 8081），用 owner token
      `POST /order-extra/templates` 建一个模板 → `GET /order-extra/templates` 能看到它 →
      `PUT /order/{某真实订单id}/extra/{code}` 填一次 → `GET /order/{id}/extra` 读回。
      这是本计划的根本目的：**打通此前因模板表恒为空而完全跑不通的链路**
- [ ] `MerchandiseMapper.xml` 的品牌聚合改动与本计划无关，单独提交（见 spec"附带说明"）
