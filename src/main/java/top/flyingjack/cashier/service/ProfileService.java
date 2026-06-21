package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

@Service
public class ProfileService {
    private final WmsUserProfileMapper profileMapper;
    private final WmsSecurityContext securityContext;

    public ProfileService(WmsUserProfileMapper profileMapper, WmsSecurityContext securityContext) {
        this.profileMapper = profileMapper;
        this.securityContext = securityContext;
    }

    public WmsUserProfile getProfile() {
        return profileMapper.findByUserId(securityContext.currentUserId());
    }

    public void updateNickname(String nickname) {
        profileMapper.updateNickname(securityContext.currentUserId(), nickname);
    }
}
