package top.flyingjack.cashier.oauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.flyingjack.cashier.oauth.dto.OAuthSession;
import top.flyingjack.common.cache.CacheService;

import java.util.UUID;

@Service
public class OAuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(OAuthSessionService.class);
    private static final String KEY_PREFIX = "wms:oauth:session:";
    private static final long SESSION_TTL = 7 * 24 * 3600L;

    private final CacheService cacheService;

    public OAuthSessionService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public String createSession(String refreshToken, Long userId) {
        String sessionId = UUID.randomUUID().toString();
        cacheService.set(KEY_PREFIX + sessionId, new OAuthSession(refreshToken, userId), SESSION_TTL);
        return sessionId;
    }

    public OAuthSession getSession(String sessionId) {
        Object raw = cacheService.get(KEY_PREFIX + sessionId);
        if (raw instanceof OAuthSession session) return session;
        if (raw != null) {
            log.warn("Unexpected type for session key {}: {}", sessionId, raw.getClass().getName());
        }
        return null;
    }

    public void rotateSession(String sessionId, String newRefreshToken) {
        OAuthSession current = getSession(sessionId);
        if (current == null) return;
        cacheService.set(KEY_PREFIX + sessionId,
                new OAuthSession(newRefreshToken, current.userId()),
                SESSION_TTL);
    }

    public void deleteSession(String sessionId) {
        cacheService.del(KEY_PREFIX + sessionId);
    }
}
