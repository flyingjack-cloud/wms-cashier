package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.MeCount;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;
import top.flyingjack.cashier.service.MerchandiseService;
import top.flyingjack.common.dto.ApiRes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/merchandise")
public class MerchandiseController {
    private final MerchandiseService merchandiseService;

    public MerchandiseController(MerchandiseService merchandiseService) {
        this.merchandiseService = merchandiseService;
    }

    @GetMapping
    public ResponseEntity<ApiRes<Map<String, Object>>> getMerchandise(
            @RequestParam(defaultValue = "false") boolean sold,
            @RequestParam int limit, @RequestParam int offset) {
        if (limit > 999 || limit <= 0 || offset < 0) {
            return ResponseEntity.badRequest().body(ApiRes.fail(org.springframework.http.HttpStatus.BAD_REQUEST));
        }
        int count = merchandiseService.getMerchandiseCount(sold);
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        data.put("merchandise", merchandiseService.getMerchandiseByPage(limit, offset, sold));
        return ResponseEntity.ok(ApiRes.success(data));
    }

    @GetMapping("/cate")
    public ResponseEntity<ApiRes<List<Merchandise>>> getMerchandiseByCateId(
            @RequestParam("cate_id") int cateId, @RequestParam(defaultValue = "false") boolean sold) {
        return ResponseEntity.ok(ApiRes.success(merchandiseService.getMerchandiseByCateId(cateId, sold)));
    }

    @PostMapping
    public ResponseEntity<ApiRes<Void>> insertMerchandise(
            @RequestParam("cate_id") int cateId, @RequestParam BigDecimal cost,
            @RequestParam BigDecimal price, @RequestParam("imei_list") List<String> imeiList,
            @RequestParam("create_time") Instant createTime) {
        merchandiseService.insertMerchandise(cateId, cost, price, imeiList, createTime);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiRes<Void>> updateMerchandise(@PathVariable int id,
            @RequestParam BigDecimal cost, @RequestParam BigDecimal price, @RequestParam String imei) {
        merchandiseService.updateMerchandise(id, cost, price, imei);
        return ResponseEntity.ok(ApiRes.success());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiRes<Void>> deleteMerchandise(@PathVariable int id) {
        merchandiseService.deleteMerchandise(id);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiRes<List<MerchandiseWithCategoryDto>>> search(
            @RequestParam String text, @RequestParam(defaultValue = "false") boolean sold) {
        return ResponseEntity.ok(ApiRes.success(merchandiseService.searchMerchandise(text, sold)));
    }

    @GetMapping("/account")
    public ResponseEntity<ApiRes<List<MeCount>>> account() {
        return ResponseEntity.ok(ApiRes.success(merchandiseService.accountMerchandises()));
    }
}
