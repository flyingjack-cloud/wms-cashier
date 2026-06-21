package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import top.flyingjack.cashier.entity.Notice;
import top.flyingjack.cashier.mapper.NoticeMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

@Service
public class NoticeService {
    private final NoticeMapper noticeMapper;
    private final WmsSecurityContext securityContext;

    public NoticeService(NoticeMapper noticeMapper, WmsSecurityContext securityContext) {
        this.noticeMapper = noticeMapper;
        this.securityContext = securityContext;
    }

    public Notice latest(String type) {
        return noticeMapper.findLatestByGroupAndType(securityContext.currentGroupId(), type);
    }
}
