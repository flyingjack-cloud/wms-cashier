package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.Notice;
import top.flyingjack.cashier.service.NoticeService;
import top.flyingjack.common.dto.ApiRes;

@RestController
@RequestMapping("/notice")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/")
    public ResponseEntity<ApiRes<Notice>> getLatestNotice(@RequestParam String type) {
        return ResponseEntity.ok(ApiRes.success(noticeService.latest(type)));
    }
}
