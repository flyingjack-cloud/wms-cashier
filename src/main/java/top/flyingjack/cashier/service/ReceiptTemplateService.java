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
