package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Notice;
import top.flyingjack.cashier.mapper.NoticeMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock NoticeMapper noticeMapper;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks NoticeService noticeService;

    @Test
    void latestNotice_returnsGroupScoped() {
        when(securityContext.currentGroupId()).thenReturn(1);
        Notice notice = new Notice();
        notice.setId(1); notice.setType("system"); notice.setContent("公告内容");
        notice.setGroupId(1); notice.setCreatedAt(Instant.now());
        when(noticeMapper.findLatestByGroupAndType(1, "system")).thenReturn(notice);

        Notice result = noticeService.latest("system");

        assertThat(result.getContent()).isEqualTo("公告内容");
    }
}
