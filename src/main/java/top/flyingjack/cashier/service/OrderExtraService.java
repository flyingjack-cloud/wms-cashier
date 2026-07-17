package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.OrderExtraTemplateDto;
import top.flyingjack.cashier.mapper.OrderExtraMapper;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;
import java.util.Map;

@Service
public class OrderExtraService {
    private final OrderExtraMapper orderExtraMapper;
    private final OrderMapper orderMapper;
    private final WmsSecurityContext securityContext;
    private final ObjectMapper objectMapper;

    public OrderExtraService(OrderExtraMapper orderExtraMapper, OrderMapper orderMapper,
                             WmsSecurityContext securityContext, ObjectMapper objectMapper) {
        this.orderExtraMapper = orderExtraMapper;
        this.orderMapper = orderMapper;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
    }

    public List<OrderExtraTemplateDto> getTemplates() {
        return orderExtraMapper.findEnabledTemplates(securityContext.currentGroupId()).stream()
                .map(this::toTemplateDto)
                .toList();
    }

    public OrderExtraTemplateDto getTemplate(String code) {
        OrderExtraTemplate template = findTemplate(code);
        return toTemplateDto(template);
    }

    @Transactional
    public void saveExtra(int orderId, String templateCode, Map<String, Object> payload) {
        Assert.isTrue(orderId > 0, "invalid order id");
        int groupId = securityContext.currentGroupId();
        Assert.isTrue(orderMapper.findMeIdById(orderId, groupId) != null, "order not found: " + orderId);

        OrderExtraTemplate template = findTemplate(templateCode);
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        Assert.isTrue(payloadNode.isObject(), "payload must be a JSON object");
        validatePayload(template, payloadNode);

        OrderExtra extra = new OrderExtra();
        extra.setGroupId(groupId);
        extra.setOrderId(orderId);
        extra.setTemplateId(template.getId());
        extra.setTemplateCode(template.getCode());
        extra.setTemplateName(template.getName());
        extra.setTemplateVersion(template.getVersion());
        extra.setPayload(writeJson(payloadNode));
        orderExtraMapper.upsertExtra(extra);
    }

    public List<OrderExtraDto> getExtras(int orderId) {
        int groupId = securityContext.currentGroupId();
        Assert.isTrue(orderMapper.findMeIdById(orderId, groupId) != null, "order not found: " + orderId);
        return orderExtraMapper.findExtrasByOrderId(groupId, orderId).stream()
                .map(this::toExtraDto)
                .toList();
    }

    public OrderExtraDto getExtra(int orderId, String templateCode) {
        int groupId = securityContext.currentGroupId();
        Assert.isTrue(orderMapper.findMeIdById(orderId, groupId) != null, "order not found: " + orderId);
        OrderExtra extra = orderExtraMapper.findExtraByOrderIdAndCode(groupId, orderId, templateCode);
        Assert.notNull(extra, "order extra not found: " + templateCode);
        return toExtraDto(extra);
    }

    private OrderExtraTemplate findTemplate(String code) {
        Assert.hasText(code, "template code cannot be empty");
        OrderExtraTemplate template = orderExtraMapper.findEnabledTemplateByCode(securityContext.currentGroupId(), code);
        Assert.notNull(template, "template not found: " + code);
        return template;
    }

    private void validatePayload(OrderExtraTemplate template, JsonNode payload) {
        JsonNode fields = readJson(template.getSchemaJson()).path("fields");
        Assert.isTrue(fields.isArray(), "template fields must be an array");
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Assert.hasText(key, "template field key cannot be empty");
            JsonNode value = payload.get(key);
            boolean required = field.path("required").asBoolean(false);
            if (required) {
                Assert.isTrue(value != null && !value.isNull()
                                && !(value.isTextual() && !org.springframework.util.StringUtils.hasText(value.asText())),
                        "missing required field: " + key);
            }
            if (value != null && !value.isNull()) {
                validateFieldType(key, field.path("type").asText("text"), value);
            }
        }
    }

    private void validateFieldType(String key, String type, JsonNode value) {
        switch (type) {
            case "number":
                Assert.isTrue(value.isNumber(), "field must be number: " + key);
                break;
            case "boolean":
                Assert.isTrue(value.isBoolean(), "field must be boolean: " + key);
                break;
            case "text":
            case "textarea":
            case "date":
            case "datetime":
            case "select":
                Assert.isTrue(value.isTextual(), "field must be text: " + key);
                break;
            default:
                break;
        }
    }

    private OrderExtraTemplateDto toTemplateDto(OrderExtraTemplate template) {
        OrderExtraTemplateDto dto = new OrderExtraTemplateDto();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setName(template.getName());
        dto.setVersion(template.getVersion());
        dto.setSchema(readJson(template.getSchemaJson()));
        return dto;
    }

    private OrderExtraDto toExtraDto(OrderExtra extra) {
        OrderExtraDto dto = new OrderExtraDto();
        dto.setOrderId(extra.getOrderId());
        dto.setTemplateCode(extra.getTemplateCode());
        dto.setTemplateName(extra.getTemplateName());
        dto.setTemplateVersion(extra.getTemplateVersion());
        dto.setPayload(readJson(extra.getPayload()));
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
            throw new IllegalArgumentException("invalid payload", e);
        }
    }
}
