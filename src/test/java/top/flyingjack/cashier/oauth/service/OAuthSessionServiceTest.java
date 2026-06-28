package top.flyingjack.cashier.oauth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.oauth.dto.OAuthSession;
import top.flyingjack.common.cache.CacheService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSessionServiceTest {

    @Mock
    private CacheService cacheService;

    private OAuthSessionService service;

    private static final long SESSION_TTL = 7 * 24 * 3600L;

    @BeforeEach
    void setUp() {
        service = new OAuthSessionService(cacheService);
    }

    @Test
    void createSession_storesInRedisAndReturnsId() {
        String sessionId = service.createSession("rt-token", 42L);

        assertThat(sessionId).isNotBlank();
        verify(cacheService).set(
                eq("wms:oauth:session:" + sessionId),
                eq(new OAuthSession("rt-token", 42L)),
                eq(SESSION_TTL)
        );
    }

    @Test
    void getSession_returnsStoredSession() {
        OAuthSession stored = new OAuthSession("rt-token", 42L);
        when(cacheService.get("wms:oauth:session:abc")).thenReturn(stored);

        OAuthSession result = service.getSession("abc");

        assertThat(result).isEqualTo(stored);
    }

    @Test
    void getSession_returnsNullWhenNotFound() {
        when(cacheService.get(anyString())).thenReturn(null);

        assertThat(service.getSession("unknown")).isNull();
    }

    @Test
    void rotateSession_updatesTokenAndResetsTtl() {
        OAuthSession old = new OAuthSession("old-rt", 42L);
        when(cacheService.get("wms:oauth:session:abc")).thenReturn(old);

        service.rotateSession("abc", "new-rt");

        verify(cacheService).set(
                eq("wms:oauth:session:abc"),
                eq(new OAuthSession("new-rt", 42L)),
                eq(SESSION_TTL)
        );
    }

    @Test
    void deleteSession_removesKey() {
        service.deleteSession("abc");
        verify(cacheService).del("wms:oauth:session:abc");
    }
}
