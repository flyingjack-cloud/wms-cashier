package top.flyingjack.cashier.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;

@Component
public class WmsSecurityContext {
    private final WmsUserProfileMapper profileMapper;

    public WmsSecurityContext(WmsUserProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public Long currentUserId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Long.parseLong(jwt.getSubject());
    }

    public WmsUserProfile currentProfile() {
        return profileMapper.findByUserId(currentUserId());
    }

    public int currentGroupId() {
        WmsUserProfile profile = currentProfile();
        return profile != null ? profile.getGroupId() : 0;
    }
}
