# 小票/A4 打印布局模板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 wms-cashier 新增按打印机类型（A4/热敏小票机）配置的小票排版模板管理接口，以及供前端布局编辑器使用的可用字段清单接口。

**Architecture:** 新增 `wms_receipt_template` 表（`group_id` + `printer_type` 唯一），独立的 `ReceiptLayoutValidator` 校验 grid 式 `layout` JSON（`page`/`rows`/`columns`），`ReceiptTemplateService` 提供 CRUD + 字段清单（后者只读复用既有 `OrderExtraMapper.findTemplates` 展开动态字段）。整体分层与命名严格照抄已完成的 `order-extra` 模板管理功能。

**Tech Stack:** Java 21, Spring Boot 3.2.4, MyBatis, PostgreSQL (jsonb), TestContainers, JUnit 5, Mockito, AssertJ, spring-security-test

**Spec:** `docs/superpowers/specs/2026-07-18-receipt-template-design.md`

## Global Constraints

- 分支必须是 `develop`，每个任务提交前用 `git branch --show-current` 确认
- 时间字段一律用 `Instant`
- 统一响应用 `ApiRes<T>`（`top.flyingjack.common.dto.ApiRes`），静态方法：`ApiRes.success(T data)` / `ApiRes.success()`
- 错误处理：`org.springframework.util.Assert` 抛 `IllegalArgumentException` → `GlobalExceptionHandler` → 400。**不要**新增 `SysErrorCode`
- Group 隔离：每个 mapper 查询都必须带 `securityContext.currentGroupId()`
- 写操作权限：`@PreAuthorize("hasRole('OWNER')")` 打在 service 方法上。方法级安全已由 `ResourceServerConfig` 的 `@EnableMethodSecurity(prePostEnabled = true)` 启用
- `printer_type` 是唯一键，创建后不可改；一个 group 对每个 `printer_type` 有且只有一行（`UNIQUE(group_id, printer_type)`），不是一对多
- **不设 `version` 列**——布局不会被历史订单快照引用，打印永远用当前配置
- `printer_type` 支持值（Java 枚举 `ReceiptPrinterType`）：`A4`、`THERMAL_58`、`THERMAL_80`
- `layout.rows[].columns[].type` 支持值（封闭枚举）：`text`、`label`、`image`、`divider`、`table`
- 固定字段注册表（9 个，`type=text` 时 `field` 若不是 `extra.*` 前缀就必须匹配这里）：
  `store.storeName`、`store.address`、`order.sellingTime`、`order.brand`、`order.model`、
  `order.imei`、`order.sellingPrice`、`order.cost`、`cashier.printedBy`
- `extra.<templateCode>.<key>` 前缀只校验格式（两段非空、`.` 分隔），不校验该 group 是否真的存在这个 order-extra 模板/字段
- `type=label` 时 `field` 存的是**字面文本**（如 `"IMEI:"`），不是数据绑定路径；`type=text` 时 `field` 才是绑定路径
- `type=image`/`type=divider` 时 `field` 可选，不做任何格式或注册表校验
- `spring-security-test` 依赖已存在于 `pom.xml`（前一个功能加的），本计划不需要再加
- Maven 测试命令（从 `flyingjack-cloud` 根目录跑，不是 `wms-cashier` 目录）：
  `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=<TestClass>`

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `src/main/java/top/flyingjack/cashier/service/ReceiptFixedFields.java` | 创建 | 固定字段 → 展示名注册表，供校验器与字段清单接口共用的唯一数据源 |
| `src/main/java/top/flyingjack/cashier/service/ReceiptLayoutValidator.java` | 创建 | `layout` 结构与字段引用校验 |
| `src/main/java/top/flyingjack/cashier/entity/ReceiptPrinterType.java` | 创建 | 打印机类型枚举 |
| `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplate.java` | 创建 | 实体，对齐 `wms_receipt_template` 表结构 |
| `src/main/java/top/flyingjack/cashier/mapper/ReceiptTemplateMapper.java` + `resources/mapper/ReceiptTemplateMapper.xml` | 创建 | CRUD SQL |
| `src/main/resources/schema.sql` / `src/test/resources/schema-test.sql` | 修改 | 新增 `wms_receipt_template` 表 |
| `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateDto.java` | 创建 | 响应 DTO |
| `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateReq.java` | 创建 | 写请求 DTO |
| `src/main/java/top/flyingjack/cashier/entity/AvailableFieldDto.java` | 创建 | 字段清单里固定字段一项的形状 |
| `src/main/java/top/flyingjack/cashier/entity/AvailableExtraFieldDto.java` | 创建 | 字段清单里 extra 字段一项的形状 |
| `src/main/java/top/flyingjack/cashier/entity/AvailableFieldsDto.java` | 创建 | 字段清单接口的完整响应形状 |
| `src/main/java/top/flyingjack/cashier/service/ReceiptTemplateService.java` | 创建 | 编排：权限、group 隔离、冲突检查、字段清单组装 |
| `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateEnabledReq.java` | 创建 | 启用/停用请求 DTO |
| `src/main/java/top/flyingjack/cashier/controller/ReceiptTemplateController.java` | 创建 | 7 个端点，薄层委托 |
| `README.md` | 修改 | 数据库表结构 + API 参考新增章节 |

---

### Task 1: ReceiptFixedFields 注册表 + ReceiptLayoutValidator

**Files:**
- Create: `src/main/java/top/flyingjack/cashier/service/ReceiptFixedFields.java`
- Create: `src/main/java/top/flyingjack/cashier/service/ReceiptLayoutValidator.java`
- Test: `src/test/java/top/flyingjack/cashier/service/ReceiptLayoutValidatorTest.java`

**Interfaces:**
- Produces:
  - `ReceiptFixedFields.LABELS` → `Map<String, String>`（字段名 → 中文展示名，只读，9 条）
  - `ReceiptLayoutValidator.validateLayout(JsonNode layout)` → `void`，非法时抛 `IllegalArgumentException`
- Consumes: 无（第一个任务）

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/top/flyingjack/cashier/service/ReceiptLayoutValidatorTest.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptLayoutValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReceiptLayoutValidator validator = new ReceiptLayoutValidator();

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void receiptFixedFields_containsExactlyNineExpectedKeys() {
        assertThat(ReceiptFixedFields.LABELS.keySet()).containsExactlyInAnyOrder(
                "store.storeName", "store.address",
                "order.sellingTime", "order.brand", "order.model", "order.imei",
                "order.sellingPrice", "order.cost",
                "cashier.printedBy");
    }

    @Test
    void validateLayout_acceptsValidLayout() {
        JsonNode layout = json("{\"page\":{},\"rows\":["
                + "{\"height\":\"30px\",\"columns\":["
                + "{\"span\":1,\"type\":\"label\",\"field\":\"IMEI:\",\"style\":{\"bold\":true}},"
                + "{\"span\":2,\"type\":\"text\",\"field\":\"order.imei\",\"style\":{}}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsTextColumnWithExtraField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":["
                + "{\"span\":1,\"type\":\"text\",\"field\":\"extra.invoice.invoiceTitle\"}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsDividerColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsImageColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"image\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsTableColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"table\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_rejectsNonObject() {
        assertThatThrownBy(() -> validator.validateLayout(json("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layout must be a JSON object");
    }

    @Test
    void validateLayout_rejectsNonObjectPage() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"page\":[],\"rows\":[{\"columns\":[{\"type\":\"divider\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be a JSON object");
    }

    @Test
    void validateLayout_rejectsMissingRows() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"page\":{}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows must be an array");
    }

    @Test
    void validateLayout_rejectsEmptyRows() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows cannot be empty");
    }

    @Test
    void validateLayout_rejectsRowWithMissingColumns() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[{}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns must be an array");
    }

    @Test
    void validateLayout_rejectsRowWithEmptyColumns() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[{\"columns\":[]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns cannot be empty");
    }

    @Test
    void validateLayout_rejectsUnsupportedColumnType() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"chart\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported column type: chart");
    }

    @Test
    void validateLayout_rejectsNonObjectStyle() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"divider\",\"style\":\"bold\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("style must be a JSON object");
    }

    @Test
    void validateLayout_rejectsTextColumnWithoutField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text column must have a field");
    }

    @Test
    void validateLayout_rejectsTextColumnWithUnknownField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\",\"field\":\"order.bogus\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field: order.bogus");
    }

    @Test
    void validateLayout_rejectsTextColumnWithMalformedExtraField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\",\"field\":\"extra.invoice\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field: extra.invoice");
    }

    @Test
    void validateLayout_rejectsLabelColumnWithoutField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"label\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label column must have non-empty field text");
    }

    @Test
    void validateLayout_acceptsLabelColumnWithArbitraryText() {
        JsonNode layout = json("{\"rows\":[{\"columns\":["
                + "{\"type\":\"label\",\"field\":\"合计：\"}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptLayoutValidatorTest`
Expected: 编译失败 —— `cannot find symbol: class ReceiptLayoutValidator` / `class ReceiptFixedFields`

- [ ] **Step 3: 写 ReceiptFixedFields**

创建 `src/main/java/top/flyingjack/cashier/service/ReceiptFixedFields.java`：

```java
package top.flyingjack.cashier.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReceiptFixedFields {
    public static final Map<String, String> LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("store.storeName", "店铺名称");
        labels.put("store.address", "店铺地址");
        labels.put("order.sellingTime", "销售时间");
        labels.put("order.brand", "品牌");
        labels.put("order.model", "型号");
        labels.put("order.imei", "IMEI");
        labels.put("order.sellingPrice", "销售价格");
        labels.put("order.cost", "成本");
        labels.put("cashier.printedBy", "打印人");
        LABELS = Map.copyOf(labels);
    }

    private ReceiptFixedFields() {
    }
}
```

- [ ] **Step 4: 写 ReceiptLayoutValidator**

创建 `src/main/java/top/flyingjack/cashier/service/ReceiptLayoutValidator.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReceiptLayoutValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of("text", "label", "image", "divider", "table");
    private static final Pattern EXTRA_FIELD_PATTERN = Pattern.compile("^extra\\.[^.]+\\.[^.]+$");

    public void validateLayout(JsonNode layout) {
        Assert.isTrue(layout != null && layout.isObject(), "layout must be a JSON object");
        if (layout.has("page")) {
            Assert.isTrue(layout.path("page").isObject(), "page must be a JSON object");
        }
        JsonNode rows = layout.path("rows");
        Assert.isTrue(rows.isArray(), "rows must be an array");
        Assert.isTrue(!rows.isEmpty(), "rows cannot be empty");
        for (JsonNode row : rows) {
            validateRow(row);
        }
    }

    private void validateRow(JsonNode row) {
        JsonNode columns = row.path("columns");
        Assert.isTrue(columns.isArray(), "columns must be an array");
        Assert.isTrue(!columns.isEmpty(), "columns cannot be empty");
        for (JsonNode column : columns) {
            validateColumn(column);
        }
    }

    private void validateColumn(JsonNode column) {
        String type = column.path("type").asText("");
        Assert.isTrue(SUPPORTED_TYPES.contains(type), "unsupported column type: " + type);
        if (column.has("style")) {
            Assert.isTrue(column.path("style").isObject(), "style must be a JSON object");
        }
        switch (type) {
            case "text":
                String textField = column.path("field").isMissingNode() ? null : column.path("field").asText();
                Assert.hasText(textField, "text column must have a field");
                Assert.isTrue(isKnownField(textField), "unknown field: " + textField);
                break;
            case "label":
                String labelText = column.path("field").isMissingNode() ? null : column.path("field").asText();
                Assert.hasText(labelText, "label column must have non-empty field text");
                break;
            default:
                break;
        }
    }

    private boolean isKnownField(String field) {
        return ReceiptFixedFields.LABELS.containsKey(field) || EXTRA_FIELD_PATTERN.matcher(field).matches();
    }
}
```

`column.path("field").isMissingNode() ? null : ...asText()` 而不是直接 `asText(null)`：Jackson 的 `JsonNode.asText(String defaultValue)`
对"字段存在但值是空字符串"和"字段完全不存在"都会走各自分支返回非 null 结果，用 `isMissingNode()` 先判断能让"完全没传 field"和"传了空字符串"都正确落到 `Assert.hasText` 的空文本分支报同一个错，避免 `asText()` 对不存在字段返回 `""` 与 `null` 语义混淆。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptLayoutValidatorTest`
Expected: PASS，19 个测试全绿

- [ ] **Step 6: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/service/ReceiptFixedFields.java \
        src/main/java/top/flyingjack/cashier/service/ReceiptLayoutValidator.java \
        src/test/java/top/flyingjack/cashier/service/ReceiptLayoutValidatorTest.java
git commit -m "feat: add receipt layout validator and fixed field registry"
```

---

### Task 2: ReceiptTemplate 实体 + Mapper + SQL + 集成测试

**Files:**
- Create: `src/main/java/top/flyingjack/cashier/entity/ReceiptPrinterType.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplate.java`
- Create: `src/main/java/top/flyingjack/cashier/mapper/ReceiptTemplateMapper.java`
- Create: `src/main/resources/mapper/ReceiptTemplateMapper.xml`
- Modify: `src/main/resources/schema.sql`
- Modify: `src/test/resources/schema-test.sql`
- Test: `src/test/java/top/flyingjack/cashier/integration/ReceiptTemplateIntegrationTest.java`

**Interfaces:**
- Consumes: 无（不依赖 Task 1）
- Produces:
  - `ReceiptTemplate` 实体：`id`(long)、`groupId`(int)、`printerType`(String)、`layout`(String，jsonb 的文本形式)、`enabled`(boolean)、`createdAt`/`updatedAt`(Instant)
  - `ReceiptTemplateMapper.findTemplates(int groupId, boolean includeDisabled)` → `List<ReceiptTemplate>`
  - `ReceiptTemplateMapper.findTemplateByPrinterType(int groupId, String printerType)` → `ReceiptTemplate`（不过滤 enabled）
  - `ReceiptTemplateMapper.findEnabledTemplateByPrinterType(int groupId, String printerType)` → `ReceiptTemplate`（只返回 enabled）
  - `ReceiptTemplateMapper.insertTemplate(ReceiptTemplate template)` → `void`，写回自增 `id`
  - `ReceiptTemplateMapper.updateTemplate(ReceiptTemplate template)` → `void`
  - `ReceiptTemplateMapper.updateEnabled(int groupId, String printerType, boolean enabled)` → `void`
  - `ReceiptPrinterType` 枚举：`A4`、`THERMAL_58`、`THERMAL_80`

- [ ] **Step 1: 写失败的集成测试**

创建 `src/test/java/top/flyingjack/cashier/integration/ReceiptTemplateIntegrationTest.java`：

```java
package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.ReceiptTemplate;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.ReceiptTemplateMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReceiptTemplateIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired ReceiptTemplateMapper receiptTemplateMapper;

    private static final String LAYOUT =
            "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}";

    private ReceiptTemplate template(int groupId, String printerType) {
        ReceiptTemplate t = new ReceiptTemplate();
        t.setGroupId(groupId);
        t.setPrinterType(printerType);
        t.setLayout(LAYOUT);
        t.setEnabled(true);
        return t;
    }

    @Test
    void insertTemplate_roundTripsJsonbAndAssignsId() {
        ReceiptTemplate t = template(910, "A4");
        receiptTemplateMapper.insertTemplate(t);

        assertThat(t.getId()).isGreaterThan(0);

        ReceiptTemplate found = receiptTemplateMapper.findTemplateByPrinterType(910, "A4");
        assertThat(found).isNotNull();
        assertThat(found.getPrinterType()).isEqualTo("A4");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getLayout()).contains("order.imei");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void insertTemplate_rejectsDuplicatePrinterTypeInSameGroup() {
        receiptTemplateMapper.insertTemplate(template(911, "A4"));

        assertThatThrownBy(() -> receiptTemplateMapper.insertTemplate(template(911, "A4")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertTemplate_allowsDifferentPrinterTypesInSameGroup() {
        receiptTemplateMapper.insertTemplate(template(912, "A4"));
        receiptTemplateMapper.insertTemplate(template(912, "THERMAL_58"));

        assertThat(receiptTemplateMapper.findTemplates(912, false)).hasSize(2);
    }

    @Test
    void insertTemplate_allowsSamePrinterTypeInDifferentGroups() {
        receiptTemplateMapper.insertTemplate(template(913, "A4"));
        receiptTemplateMapper.insertTemplate(template(914, "A4"));

        assertThat(receiptTemplateMapper.findTemplateByPrinterType(913, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplateByPrinterType(914, "A4")).isNotNull();
    }

    @Test
    void updateTemplate_persistsLayout() {
        ReceiptTemplate t = template(915, "A4");
        receiptTemplateMapper.insertTemplate(t);

        t.setLayout("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}");
        receiptTemplateMapper.updateTemplate(t);

        ReceiptTemplate found = receiptTemplateMapper.findTemplateByPrinterType(915, "A4");
        assertThat(found.getLayout()).contains("divider").doesNotContain("order.imei");
    }

    @Test
    void updateEnabled_hidesTemplateFromEnabledQueries() {
        receiptTemplateMapper.insertTemplate(template(916, "A4"));

        receiptTemplateMapper.updateEnabled(916, "A4", false);

        assertThat(receiptTemplateMapper.findEnabledTemplateByPrinterType(916, "A4")).isNull();
        assertThat(receiptTemplateMapper.findTemplateByPrinterType(916, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplates(916, false)).isEmpty();
        assertThat(receiptTemplateMapper.findTemplates(916, true)).hasSize(1);
    }

    @Test
    void updateEnabled_reenablesTemplate() {
        receiptTemplateMapper.insertTemplate(template(917, "A4"));
        receiptTemplateMapper.updateEnabled(917, "A4", false);

        receiptTemplateMapper.updateEnabled(917, "A4", true);

        assertThat(receiptTemplateMapper.findEnabledTemplateByPrinterType(917, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplates(917, false)).hasSize(1);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateIntegrationTest`
Expected: 编译失败 —— `cannot find symbol: class ReceiptTemplate` / `class ReceiptTemplateMapper`

- [ ] **Step 3: 写枚举与实体**

创建 `src/main/java/top/flyingjack/cashier/entity/ReceiptPrinterType.java`：

```java
package top.flyingjack.cashier.entity;

public enum ReceiptPrinterType {
    A4,
    THERMAL_58,
    THERMAL_80
}
```

创建 `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplate.java`：

```java
package top.flyingjack.cashier.entity;

import java.time.Instant;

public class ReceiptTemplate {
    private long id;
    private int groupId;
    private String printerType;
    private String layout;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }
    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

`printerType` 存为 `String`（不是 Java 枚举类型），跟 `OrderExtraTemplate.code` 一样——由 service 层用 `ReceiptPrinterType.valueOf(...)` 校验合法性，mapper/实体层保持简单字符串，不引入 MyBatis 枚举类型处理器（这个代码库目前没有先例）。

- [ ] **Step 4: 写 mapper 接口**

创建 `src/main/java/top/flyingjack/cashier/mapper/ReceiptTemplateMapper.java`：

```java
package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.ReceiptTemplate;

import java.util.List;

@Mapper
public interface ReceiptTemplateMapper {
    List<ReceiptTemplate> findTemplates(@Param("groupId") int groupId,
                                        @Param("includeDisabled") boolean includeDisabled);
    ReceiptTemplate findTemplateByPrinterType(@Param("groupId") int groupId,
                                              @Param("printerType") String printerType);
    ReceiptTemplate findEnabledTemplateByPrinterType(@Param("groupId") int groupId,
                                                     @Param("printerType") String printerType);
    void insertTemplate(ReceiptTemplate template);
    void updateTemplate(ReceiptTemplate template);
    void updateEnabled(@Param("groupId") int groupId, @Param("printerType") String printerType,
                       @Param("enabled") boolean enabled);
}
```

- [ ] **Step 5: 写 SQL**

创建 `src/main/resources/mapper/ReceiptTemplateMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="top.flyingjack.cashier.mapper.ReceiptTemplateMapper">

    <insert id="insertTemplate" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO wms_receipt_template (group_id, printer_type, layout, enabled)
        VALUES (#{groupId}, #{printerType}, #{layout}::jsonb, #{enabled})
    </insert>

    <update id="updateTemplate">
        UPDATE wms_receipt_template
        SET layout = #{layout}::jsonb,
            updated_at = NOW()
        WHERE id = #{id} AND group_id = #{groupId}
    </update>

    <update id="updateEnabled">
        UPDATE wms_receipt_template
        SET enabled = #{enabled},
            updated_at = NOW()
        WHERE group_id = #{groupId} AND printer_type = #{printerType}
    </update>

    <select id="findTemplates" resultType="top.flyingjack.cashier.entity.ReceiptTemplate">
        SELECT id, group_id, printer_type, layout::text AS layout,
               enabled, created_at, updated_at
        FROM wms_receipt_template
        WHERE group_id = #{groupId}
        <if test="!includeDisabled">
            AND enabled = TRUE
        </if>
        ORDER BY id
    </select>

    <select id="findTemplateByPrinterType" resultType="top.flyingjack.cashier.entity.ReceiptTemplate">
        SELECT id, group_id, printer_type, layout::text AS layout,
               enabled, created_at, updated_at
        FROM wms_receipt_template
        WHERE group_id = #{groupId} AND printer_type = #{printerType}
    </select>

    <select id="findEnabledTemplateByPrinterType" resultType="top.flyingjack.cashier.entity.ReceiptTemplate">
        SELECT id, group_id, printer_type, layout::text AS layout,
               enabled, created_at, updated_at
        FROM wms_receipt_template
        WHERE group_id = #{groupId} AND printer_type = #{printerType} AND enabled = TRUE
    </select>

</mapper>
```

- [ ] **Step 6: 加表结构**

修改 `src/main/resources/schema.sql`，在 `wms_order_extra` 表定义之后、`wms_notice` 表定义之前插入：

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

对 `src/test/resources/schema-test.sql` 做**完全相同**的插入（这两个文件截至目前的 `wms_order`/`wms_order_extra*` 表定义是逐字节一致的，插入位置也一致：`wms_order_extra` 之后、`wms_notice` 之前）。

- [ ] **Step 7: 运行集成测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateIntegrationTest`
Expected: PASS，7 个测试全绿。TestContainers 启动 Postgres 容器可能需要 30-90 秒，属正常现象。

- [ ] **Step 8: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/entity/ReceiptPrinterType.java \
        src/main/java/top/flyingjack/cashier/entity/ReceiptTemplate.java \
        src/main/java/top/flyingjack/cashier/mapper/ReceiptTemplateMapper.java \
        src/main/resources/mapper/ReceiptTemplateMapper.xml \
        src/main/resources/schema.sql \
        src/test/resources/schema-test.sql \
        src/test/java/top/flyingjack/cashier/integration/ReceiptTemplateIntegrationTest.java
git commit -m "feat: add receipt template entity, mapper, and schema"
```

---

### Task 3: ReceiptTemplateService（CRUD + 字段清单）

**Files:**
- Create: `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateDto.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateReq.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/AvailableFieldDto.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/AvailableExtraFieldDto.java`
- Create: `src/main/java/top/flyingjack/cashier/entity/AvailableFieldsDto.java`
- Create: `src/main/java/top/flyingjack/cashier/service/ReceiptTemplateService.java`
- Test: `src/test/java/top/flyingjack/cashier/service/ReceiptTemplateServiceTest.java`

**Interfaces:**
- Consumes:
  - Task 1: `ReceiptLayoutValidator.validateLayout(JsonNode)`、`ReceiptFixedFields.LABELS`
  - Task 2: `ReceiptTemplateMapper` 全部方法、`ReceiptTemplate` 实体、`ReceiptPrinterType` 枚举
  - 已有：`top.flyingjack.cashier.mapper.OrderExtraMapper.findTemplates(int groupId, boolean includeDisabled)` → `List<OrderExtraTemplate>`；`OrderExtraTemplate.getCode()`/`getName()`/`getSchemaJson()`
- Produces:
  - `ReceiptTemplateService.getTemplates(boolean includeDisabled)` → `List<ReceiptTemplateDto>`
  - `ReceiptTemplateService.getTemplate(String printerType)` → `ReceiptTemplateDto`
  - `ReceiptTemplateService.createTemplate(ReceiptTemplateReq req)` → `ReceiptTemplateDto`
  - `ReceiptTemplateService.updateTemplate(String printerType, ReceiptTemplateReq req)` → `ReceiptTemplateDto`
  - `ReceiptTemplateService.setTemplateEnabled(String printerType, boolean enabled)` → `ReceiptTemplateDto`
  - `ReceiptTemplateService.disableTemplate(String printerType)` → `void`
  - `ReceiptTemplateService.getAvailableFields()` → `AvailableFieldsDto`
  - `ReceiptTemplateReq`：`getPrinterType()`/`setPrinterType(String)`、`getLayout()`/`setLayout(JsonNode)`
  - `ReceiptTemplateDto`：`getId()`、`getPrinterType()`/`setPrinterType(String)`、`getLayout()`/`setLayout(JsonNode)`、`isEnabled()`/`setEnabled(boolean)`
  - `AvailableFieldDto(String field, String label)`：`getField()`、`getLabel()`
  - `AvailableExtraFieldDto(String field, String label, String templateCode, String key)`：`getField()`、`getLabel()`、`getTemplateCode()`、`getKey()`
  - `AvailableFieldsDto(List<AvailableFieldDto> fixed, List<AvailableExtraFieldDto> extra)`：`getFixed()`、`getExtra()`

- [ ] **Step 1: 写 DTO**

创建 `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateDto.java`：

```java
package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class ReceiptTemplateDto {
    private long id;
    private String printerType;
    private JsonNode layout;
    private boolean enabled;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }
    public JsonNode getLayout() { return layout; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

创建 `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateReq.java`：

```java
package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class ReceiptTemplateReq {
    private String printerType;
    private JsonNode layout;

    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }
    public JsonNode getLayout() { return layout; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
}
```

创建 `src/main/java/top/flyingjack/cashier/entity/AvailableFieldDto.java`：

```java
package top.flyingjack.cashier.entity;

public class AvailableFieldDto {
    private String field;
    private String label;

    public AvailableFieldDto() {
    }

    public AvailableFieldDto(String field, String label) {
        this.field = field;
        this.label = label;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
```

创建 `src/main/java/top/flyingjack/cashier/entity/AvailableExtraFieldDto.java`：

```java
package top.flyingjack.cashier.entity;

public class AvailableExtraFieldDto {
    private String field;
    private String label;
    private String templateCode;
    private String key;

    public AvailableExtraFieldDto() {
    }

    public AvailableExtraFieldDto(String field, String label, String templateCode, String key) {
        this.field = field;
        this.label = label;
        this.templateCode = templateCode;
        this.key = key;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
}
```

创建 `src/main/java/top/flyingjack/cashier/entity/AvailableFieldsDto.java`：

```java
package top.flyingjack.cashier.entity;

import java.util.List;

public class AvailableFieldsDto {
    private List<AvailableFieldDto> fixed;
    private List<AvailableExtraFieldDto> extra;

    public AvailableFieldsDto() {
    }

    public AvailableFieldsDto(List<AvailableFieldDto> fixed, List<AvailableExtraFieldDto> extra) {
        this.fixed = fixed;
        this.extra = extra;
    }

    public List<AvailableFieldDto> getFixed() { return fixed; }
    public void setFixed(List<AvailableFieldDto> fixed) { this.fixed = fixed; }
    public List<AvailableExtraFieldDto> getExtra() { return extra; }
    public void setExtra(List<AvailableExtraFieldDto> extra) { this.extra = extra; }
}
```

- [ ] **Step 2: 写失败的测试**

创建 `src/test/java/top/flyingjack/cashier/service/ReceiptTemplateServiceTest.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import top.flyingjack.cashier.entity.AvailableFieldsDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.ReceiptTemplate;
import top.flyingjack.cashier.entity.ReceiptTemplateDto;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.mapper.OrderExtraMapper;
import top.flyingjack.cashier.mapper.ReceiptTemplateMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReceiptTemplateServiceTest {
    ReceiptTemplateMapper receiptTemplateMapper;
    OrderExtraMapper orderExtraMapper;
    WmsSecurityContext securityContext;
    ReceiptTemplateService receiptTemplateService;

    @BeforeEach
    void setUp() {
        receiptTemplateMapper = mock(ReceiptTemplateMapper.class);
        orderExtraMapper = mock(OrderExtraMapper.class);
        securityContext = mock(WmsSecurityContext.class);
        receiptTemplateService = new ReceiptTemplateService(receiptTemplateMapper, orderExtraMapper,
                securityContext, new ObjectMapper(), new ReceiptLayoutValidator());
        when(securityContext.currentGroupId()).thenReturn(1);
    }

    private JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReceiptTemplateReq req(String printerType, String layout) {
        ReceiptTemplateReq r = new ReceiptTemplateReq();
        r.setPrinterType(printerType);
        r.setLayout(json(layout));
        return r;
    }

    private ReceiptTemplate template() {
        ReceiptTemplate t = new ReceiptTemplate();
        t.setId(7);
        t.setGroupId(1);
        t.setPrinterType("A4");
        t.setEnabled(true);
        t.setLayout("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}");
        return t;
    }

    private static final String VALID_LAYOUT =
            "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}";

    @Test
    void getTemplates_returnsLayoutAsJson() {
        when(receiptTemplateMapper.findTemplates(1, false)).thenReturn(List.of(template()));

        List<ReceiptTemplateDto> result = receiptTemplateService.getTemplates(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrinterType()).isEqualTo("A4");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(0).getLayout().path("rows").get(0).path("columns").get(0)
                .path("field").asText()).isEqualTo("order.imei");
    }

    @Test
    void getTemplate_returnsEnabledTemplateOnly() {
        when(receiptTemplateMapper.findEnabledTemplateByPrinterType(1, "A4")).thenReturn(template());

        ReceiptTemplateDto result = receiptTemplateService.getTemplate("A4");

        assertThat(result.getPrinterType()).isEqualTo("A4");
    }

    @Test
    void getTemplate_rejectsUnknownOrDisabledPrinterType() {
        when(receiptTemplateMapper.findEnabledTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.getTemplate("A4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");
    }

    @Test
    void createTemplate_insertsEnabledTemplate() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT));

        ArgumentCaptor<ReceiptTemplate> captor = ArgumentCaptor.forClass(ReceiptTemplate.class);
        verify(receiptTemplateMapper).insertTemplate(captor.capture());
        ReceiptTemplate saved = captor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(1);
        assertThat(saved.getPrinterType()).isEqualTo("A4");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getLayout()).contains("order.imei");
    }

    @Test
    void createTemplate_rejectsUnsupportedPrinterType() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("DOT_MATRIX", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported printer type: DOT_MATRIX");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsInvalidLayout() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", "{\"rows\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows cannot be empty");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsDuplicatePrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template already exists: A4");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsRaceDuplicateInsert() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate key"))
                .when(receiptTemplateMapper).insertTemplate(any());

        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template already exists: A4");
    }

    @Test
    void updateTemplate_persistsNewLayout() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        ReceiptTemplateDto result = receiptTemplateService.updateTemplate("A4",
                req(null, "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}"));

        ArgumentCaptor<ReceiptTemplate> captor = ArgumentCaptor.forClass(ReceiptTemplate.class);
        verify(receiptTemplateMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getLayout()).contains("divider").doesNotContain("order.imei");
        assertThat(result.getLayout().path("rows").get(0).path("columns").get(0)
                .path("type").asText()).isEqualTo("divider");
    }

    @Test
    void updateTemplate_rejectsUnknownPrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4", req(null, VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");

        verify(receiptTemplateMapper, never()).updateTemplate(any());
    }

    @Test
    void updateTemplate_rejectsInvalidLayout() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4",
                req(null, "{\"rows\":[{\"columns\":[{\"type\":\"chart\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported column type: chart");

        verify(receiptTemplateMapper, never()).updateTemplate(any());
    }

    @Test
    void disableTemplate_setsEnabledFalse() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        receiptTemplateService.disableTemplate("A4");

        verify(receiptTemplateMapper).updateEnabled(1, "A4", false);
    }

    @Test
    void setTemplateEnabled_rejectsUnknownPrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.setTemplateEnabled("A4", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");

        verify(receiptTemplateMapper, never()).updateEnabled(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getAvailableFields_returnsFixedAndExtraFields() {
        OrderExtraTemplate invoiceTemplate = new OrderExtraTemplate();
        invoiceTemplate.setCode("invoice");
        invoiceTemplate.setName("发票信息");
        invoiceTemplate.setSchemaJson("{\"fields\":["
                + "{\"key\":\"invoiceTitle\",\"label\":\"发票抬头\",\"type\":\"text\"},"
                + "{\"key\":\"taxNo\",\"type\":\"text\"}"
                + "]}");
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of(invoiceTemplate));

        AvailableFieldsDto result = receiptTemplateService.getAvailableFields();

        assertThat(result.getFixed()).hasSize(9);
        assertThat(result.getFixed()).extracting("field").contains(
                "store.storeName", "order.imei", "cashier.printedBy");

        assertThat(result.getExtra()).hasSize(2);
        assertThat(result.getExtra().get(0).getField()).isEqualTo("extra.invoice.invoiceTitle");
        assertThat(result.getExtra().get(0).getLabel()).isEqualTo("发票信息 - 发票抬头");
        assertThat(result.getExtra().get(0).getTemplateCode()).isEqualTo("invoice");
        assertThat(result.getExtra().get(0).getKey()).isEqualTo("invoiceTitle");
        assertThat(result.getExtra().get(1).getField()).isEqualTo("extra.invoice.taxNo");
        assertThat(result.getExtra().get(1).getLabel()).isEqualTo("发票信息 - taxNo");
    }

    @Test
    void getAvailableFields_returnsEmptyExtraWhenNoOrderExtraTemplates() {
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of());

        AvailableFieldsDto result = receiptTemplateService.getAvailableFields();

        assertThat(result.getExtra()).isEmpty();
        assertThat(result.getFixed()).hasSize(9);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateServiceTest`
Expected: 编译失败 —— `cannot find symbol: class ReceiptTemplateService`

- [ ] **Step 4: 写 ReceiptTemplateService**

创建 `src/main/java/top/flyingjack/cashier/service/ReceiptTemplateService.java`：

```java
package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.AvailableExtraFieldDto;
import top.flyingjack.cashier.entity.AvailableFieldDto;
import top.flyingjack.cashier.entity.AvailableFieldsDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.ReceiptPrinterType;
import top.flyingjack.cashier.entity.ReceiptTemplate;
import top.flyingjack.cashier.entity.ReceiptTemplateDto;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.mapper.OrderExtraMapper;
import top.flyingjack.cashier.mapper.ReceiptTemplateMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReceiptTemplateService {
    private final ReceiptTemplateMapper receiptTemplateMapper;
    private final OrderExtraMapper orderExtraMapper;
    private final WmsSecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final ReceiptLayoutValidator layoutValidator;

    public ReceiptTemplateService(ReceiptTemplateMapper receiptTemplateMapper, OrderExtraMapper orderExtraMapper,
                                  WmsSecurityContext securityContext, ObjectMapper objectMapper,
                                  ReceiptLayoutValidator layoutValidator) {
        this.receiptTemplateMapper = receiptTemplateMapper;
        this.orderExtraMapper = orderExtraMapper;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
        this.layoutValidator = layoutValidator;
    }

    @PreAuthorize("!#includeDisabled or hasRole('OWNER')")
    public List<ReceiptTemplateDto> getTemplates(boolean includeDisabled) {
        return receiptTemplateMapper.findTemplates(securityContext.currentGroupId(), includeDisabled).stream()
                .map(this::toDto)
                .toList();
    }

    public ReceiptTemplateDto getTemplate(String printerType) {
        Assert.hasText(printerType, "printer type cannot be empty");
        ReceiptTemplate template = receiptTemplateMapper.findEnabledTemplateByPrinterType(
                securityContext.currentGroupId(), printerType);
        Assert.notNull(template, "receipt template not found: " + printerType);
        return toDto(template);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ReceiptTemplateDto createTemplate(ReceiptTemplateReq req) {
        Assert.hasText(req.getPrinterType(), "printer type cannot be empty");
        assertValidPrinterType(req.getPrinterType());
        layoutValidator.validateLayout(req.getLayout());
        int groupId = securityContext.currentGroupId();
        Assert.isNull(receiptTemplateMapper.findTemplateByPrinterType(groupId, req.getPrinterType()),
                "receipt template already exists: " + req.getPrinterType());

        ReceiptTemplate template = new ReceiptTemplate();
        template.setGroupId(groupId);
        template.setPrinterType(req.getPrinterType());
        template.setLayout(writeJson(req.getLayout()));
        template.setEnabled(true);
        try {
            receiptTemplateMapper.insertTemplate(template);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("receipt template already exists: " + req.getPrinterType());
        }
        return toDto(template);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ReceiptTemplateDto updateTemplate(String printerType, ReceiptTemplateReq req) {
        Assert.hasText(printerType, "printer type cannot be empty");
        layoutValidator.validateLayout(req.getLayout());
        int groupId = securityContext.currentGroupId();
        ReceiptTemplate existing = receiptTemplateMapper.findTemplateByPrinterType(groupId, printerType);
        Assert.notNull(existing, "receipt template not found: " + printerType);

        existing.setLayout(writeJson(req.getLayout()));
        receiptTemplateMapper.updateTemplate(existing);
        return toDto(existing);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ReceiptTemplateDto setTemplateEnabled(String printerType, boolean enabled) {
        Assert.hasText(printerType, "printer type cannot be empty");
        int groupId = securityContext.currentGroupId();
        ReceiptTemplate existing = receiptTemplateMapper.findTemplateByPrinterType(groupId, printerType);
        Assert.notNull(existing, "receipt template not found: " + printerType);

        receiptTemplateMapper.updateEnabled(groupId, printerType, enabled);
        existing.setEnabled(enabled);
        return toDto(existing);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void disableTemplate(String printerType) {
        setTemplateEnabled(printerType, false);
    }

    public AvailableFieldsDto getAvailableFields() {
        List<AvailableFieldDto> fixed = new ArrayList<>();
        for (Map.Entry<String, String> entry : ReceiptFixedFields.LABELS.entrySet()) {
            fixed.add(new AvailableFieldDto(entry.getKey(), entry.getValue()));
        }

        List<AvailableExtraFieldDto> extra = new ArrayList<>();
        List<OrderExtraTemplate> orderExtraTemplates =
                orderExtraMapper.findTemplates(securityContext.currentGroupId(), false);
        for (OrderExtraTemplate template : orderExtraTemplates) {
            JsonNode fields = readJson(template.getSchemaJson()).path("fields");
            for (JsonNode field : fields) {
                String key = field.path("key").asText();
                String fieldLabel = field.path("label").asText(key);
                extra.add(new AvailableExtraFieldDto(
                        "extra." + template.getCode() + "." + key,
                        template.getName() + " - " + fieldLabel,
                        template.getCode(),
                        key));
            }
        }
        return new AvailableFieldsDto(fixed, extra);
    }

    private void assertValidPrinterType(String printerType) {
        try {
            ReceiptPrinterType.valueOf(printerType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported printer type: " + printerType);
        }
    }

    private ReceiptTemplateDto toDto(ReceiptTemplate template) {
        ReceiptTemplateDto dto = new ReceiptTemplateDto();
        dto.setId(template.getId());
        dto.setPrinterType(template.getPrinterType());
        dto.setLayout(readJson(template.getLayout()));
        dto.setEnabled(template.isEnabled());
        return dto;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid json", e);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid layout", e);
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateServiceTest`
Expected: PASS，17 个测试全绿

- [ ] **Step 6: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateDto.java \
        src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateReq.java \
        src/main/java/top/flyingjack/cashier/entity/AvailableFieldDto.java \
        src/main/java/top/flyingjack/cashier/entity/AvailableExtraFieldDto.java \
        src/main/java/top/flyingjack/cashier/entity/AvailableFieldsDto.java \
        src/main/java/top/flyingjack/cashier/service/ReceiptTemplateService.java \
        src/test/java/top/flyingjack/cashier/service/ReceiptTemplateServiceTest.java
git commit -m "feat: add receipt template CRUD service and available-fields aggregation"
```

---

### Task 4: Controller

**Files:**
- Create: `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateEnabledReq.java`
- Create: `src/main/java/top/flyingjack/cashier/controller/ReceiptTemplateController.java`
- Test: `src/test/java/top/flyingjack/cashier/controller/ReceiptTemplateControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ReceiptTemplateService` 全部方法
- Produces: HTTP 端点

| Method | Path | 响应 |
|---|---|---|
| `GET` | `/receipt-templates?includeDisabled=false` | `ApiRes<List<ReceiptTemplateDto>>` |
| `GET` | `/receipt-templates/fields` | `ApiRes<AvailableFieldsDto>` |
| `GET` | `/receipt-templates/{printerType}` | `ApiRes<ReceiptTemplateDto>` |
| `POST` | `/receipt-templates` | `ApiRes<ReceiptTemplateDto>` |
| `PUT` | `/receipt-templates/{printerType}` | `ApiRes<ReceiptTemplateDto>` |
| `DELETE` | `/receipt-templates/{printerType}` | `ApiRes<Void>` |
| `PUT` | `/receipt-templates/{printerType}/enabled` | `ApiRes<ReceiptTemplateDto>` |

`/receipt-templates/fields` 是字面量路径段，Spring MVC 对字面量路径的匹配优先级高于 `{printerType}` 路径变量，因此不会被 `getTemplate` 处理器吞掉——这跟 order-extra 模板管理接口设计时确认过的 Spring 路由行为一致。`fields` 本身也不是任何合法的 `ReceiptPrinterType` 枚举值，双重保险。

- [ ] **Step 1: 建 enabled 请求 DTO**

创建 `src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateEnabledReq.java`：

```java
package top.flyingjack.cashier.entity;

public class ReceiptTemplateEnabledReq {
    private boolean enabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

- [ ] **Step 2: 写失败的测试**

创建 `src/test/java/top/flyingjack/cashier/controller/ReceiptTemplateControllerTest.java`：

```java
package top.flyingjack.cashier.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.service.ReceiptTemplateService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceiptTemplateControllerTest {

    @Mock ReceiptTemplateService receiptTemplateService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReceiptTemplateController(receiptTemplateService)).build();
    }

    @Test
    void getTemplates_defaultsToEnabledOnly() throws Exception {
        mockMvc.perform(get("/receipt-templates"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplates(false);
    }

    @Test
    void getTemplates_passesIncludeDisabled() throws Exception {
        mockMvc.perform(get("/receipt-templates").param("includeDisabled", "true"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplates(true);
    }

    @Test
    void getAvailableFields_resolvesToFieldsEndpointNotPrinterTypePath() throws Exception {
        mockMvc.perform(get("/receipt-templates/fields"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getAvailableFields();
        verifyNoMoreInteractions(receiptTemplateService);
    }

    @Test
    void getTemplate_passesPrinterTypeFromPath() throws Exception {
        mockMvc.perform(get("/receipt-templates/A4"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplate("A4");
    }

    @Test
    void createTemplate_passesReqToService() throws Exception {
        mockMvc.perform(post("/receipt-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"printerType\":\"A4\","
                                + "\"layout\":{\"rows\":[{\"columns\":["
                                + "{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReceiptTemplateReq> captor = ArgumentCaptor.forClass(ReceiptTemplateReq.class);
        verify(receiptTemplateService).createTemplate(captor.capture());
        assertThat(captor.getValue().getPrinterType()).isEqualTo("A4");
        assertThat(captor.getValue().getLayout().path("rows").get(0).path("columns").get(0)
                .path("field").asText()).isEqualTo("order.imei");
    }

    @Test
    void updateTemplate_passesPrinterTypeFromPath() throws Exception {
        mockMvc.perform(put("/receipt-templates/A4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReceiptTemplateReq> captor = ArgumentCaptor.forClass(ReceiptTemplateReq.class);
        verify(receiptTemplateService).updateTemplate(eq("A4"), captor.capture());
        assertThat(captor.getValue().getLayout().path("rows").get(0).path("columns").get(0)
                .path("type").asText()).isEqualTo("divider");
    }

    @Test
    void deleteTemplate_disablesIt() throws Exception {
        mockMvc.perform(delete("/receipt-templates/A4"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).disableTemplate("A4");
    }

    @Test
    void setEnabled_passesFlagToService() throws Exception {
        mockMvc.perform(put("/receipt-templates/A4/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).setTemplateEnabled("A4", true);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateControllerTest`
Expected: 编译失败 —— `cannot find symbol: class ReceiptTemplateController`

- [ ] **Step 4: 写 Controller**

创建 `src/main/java/top/flyingjack/cashier/controller/ReceiptTemplateController.java`：

```java
package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.AvailableFieldsDto;
import top.flyingjack.cashier.entity.ReceiptTemplateDto;
import top.flyingjack.cashier.entity.ReceiptTemplateEnabledReq;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.service.ReceiptTemplateService;
import top.flyingjack.common.dto.ApiRes;

import java.util.List;

@RestController
@RequestMapping("/receipt-templates")
public class ReceiptTemplateController {
    private final ReceiptTemplateService receiptTemplateService;

    public ReceiptTemplateController(ReceiptTemplateService receiptTemplateService) {
        this.receiptTemplateService = receiptTemplateService;
    }

    @GetMapping
    public ResponseEntity<ApiRes<List<ReceiptTemplateDto>>> getTemplates(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ResponseEntity.ok(ApiRes.success(receiptTemplateService.getTemplates(includeDisabled)));
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiRes<AvailableFieldsDto>> getAvailableFields() {
        return ResponseEntity.ok(ApiRes.success(receiptTemplateService.getAvailableFields()));
    }

    @GetMapping("/{printerType}")
    public ResponseEntity<ApiRes<ReceiptTemplateDto>> getTemplate(@PathVariable String printerType) {
        return ResponseEntity.ok(ApiRes.success(receiptTemplateService.getTemplate(printerType)));
    }

    @PostMapping
    public ResponseEntity<ApiRes<ReceiptTemplateDto>> createTemplate(@RequestBody ReceiptTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(receiptTemplateService.createTemplate(req)));
    }

    @PutMapping("/{printerType}")
    public ResponseEntity<ApiRes<ReceiptTemplateDto>> updateTemplate(
            @PathVariable String printerType, @RequestBody ReceiptTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(receiptTemplateService.updateTemplate(printerType, req)));
    }

    @DeleteMapping("/{printerType}")
    public ResponseEntity<ApiRes<Void>> deleteTemplate(@PathVariable String printerType) {
        receiptTemplateService.disableTemplate(printerType);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/{printerType}/enabled")
    public ResponseEntity<ApiRes<ReceiptTemplateDto>> setTemplateEnabled(
            @PathVariable String printerType, @RequestBody ReceiptTemplateEnabledReq req) {
        return ResponseEntity.ok(ApiRes.success(
                receiptTemplateService.setTemplateEnabled(printerType, req.isEnabled())));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateControllerTest`
Expected: PASS，全绿

- [ ] **Step 6: 全量测试**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier`
Expected: PASS，全部测试类全绿

- [ ] **Step 7: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/main/java/top/flyingjack/cashier/entity/ReceiptTemplateEnabledReq.java \
        src/main/java/top/flyingjack/cashier/controller/ReceiptTemplateController.java \
        src/test/java/top/flyingjack/cashier/controller/ReceiptTemplateControllerTest.java
git commit -m "feat: expose receipt template admin endpoints"
```

---

### Task 5: 权限验证测试

验证 `@PreAuthorize` 在真实 Spring AOP 代理下生效，尤其是 `getTemplates` 的条件式 SpEL
（`!#includeDisabled or hasRole('OWNER')`）。前面几个任务的单测都是 `new ReceiptTemplateService(...)`
直接构造，绕过了 Spring 安全代理，完全没有验证过权限注解真的在拦截。

`spring-security-test` 依赖已经在 `pom.xml` 里（order-extra 模板管理功能加的），且已经在
`OrderExtraTemplateSecurityTest` 里验证过这个项目构建下参数名解析（`#includeDisabled` 这种按名
引用）工作正常——`-parameters` 编译参数是模块级配置，对同模块所有类一视同仁，这次不需要重新做
探测性验证，直接照抄同款测试结构即可。

**Files:**
- Test: `src/test/java/top/flyingjack/cashier/security/ReceiptTemplateSecurityTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ReceiptTemplateService` 全部方法

- [ ] **Step 1: 写测试**

创建 `src/test/java/top/flyingjack/cashier/security/ReceiptTemplateSecurityTest.java`：

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
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.service.ReceiptTemplateService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReceiptTemplateSecurityTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean WmsSecurityContext securityContext;

    @Autowired ReceiptTemplateService receiptTemplateService;

    private ReceiptTemplateReq req() {
        ReceiptTemplateReq r = new ReceiptTemplateReq();
        r.setPrinterType("A4");
        try {
            r.setLayout(new ObjectMapper().readTree(
                    "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_allowedForNonOwnerWhenIncludeDisabledFalse() {
        when(securityContext.currentGroupId()).thenReturn(970);

        assertThatCode(() -> receiptTemplateService.getTemplates(false)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_deniedForNonOwnerWhenIncludeDisabledTrue() {
        assertThatThrownBy(() -> receiptTemplateService.getTemplates(true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void getTemplates_allowedForOwnerWhenIncludeDisabledTrue() {
        when(securityContext.currentGroupId()).thenReturn(971);

        assertThatCode(() -> receiptTemplateService.getTemplates(true)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void createTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void updateTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4", req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void disableTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.disableTemplate("A4"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void setTemplateEnabled_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.setTemplateEnabled("A4", true))
                .isInstanceOf(AccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

Run: `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier -Dtest=ReceiptTemplateSecurityTest`
Expected: PASS，7 个测试全绿

- [ ] **Step 3: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add src/test/java/top/flyingjack/cashier/security/ReceiptTemplateSecurityTest.java
git commit -m "test: verify receipt template authorization rules"
```

---

### Task 6: README.md 文档

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 补数据库表结构**

在 `README.md` 的"数据库表结构"表格里，`wms_order_extra` 那一行之后、`wms_notice` 那一行之前插入：

```markdown
| `wms_receipt_template` | 小票/A4 打印布局模板（按 group + printer_type） | `id`, `group_id`, `printer_type`, `layout`, `enabled` |
```

- [ ] **Step 2: 追加 API 参考章节**

在 `README.md` 里 `### Order Extra` 小节结尾（`### Profile` 小节开始之前）插入新的一节：

````markdown
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
````

- [ ] **Step 3: 人工核对**

通读改动后的章节，确认：
- 端点路径与 Task 4 实现一致
- `printerType` 枚举值与 `ReceiptPrinterType` 一致
- 固定字段清单与 `ReceiptFixedFields.LABELS` 一致（9 个）
- `column type` 枚举与 `ReceiptLayoutValidator.SUPPORTED_TYPES` 一致

- [ ] **Step 4: 提交**

```bash
cd /home/flyingjack/code/java/flyingjack-cloud/wms-cashier
git branch --show-current   # 必须输出 develop
git add README.md
git commit -m "docs: document receipt template endpoints"
```

---

## 收尾检查

- [ ] `cd /home/flyingjack/code/java/flyingjack-cloud && ./mvnw -o test -pl wms-cashier` 全绿
- [ ] `git log --oneline -6` 显示 6 次提交，均在 `develop`
- [ ] 手工冒烟：用 owner token `POST /receipt-templates` 建一个 `printerType: "A4"` 的模板 →
      `GET /receipt-templates` 能看到它 → `GET /receipt-templates/fields` 能看到 9 个固定字段
      （以及如果该 group 有启用的 order-extra 模板，能看到对应的 `extra.*` 字段）
