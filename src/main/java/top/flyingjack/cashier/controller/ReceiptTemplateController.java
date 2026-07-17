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
