package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.SystemAuthority;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.mapper.AuthorityMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

@Service
public class AuthorityService {
    private final AuthorityMapper authorityMapper;
    private final WmsUserProfileMapper profileMapper;
    private final WmsSecurityContext securityContext;

    public AuthorityService(AuthorityMapper authorityMapper,
                             WmsUserProfileMapper profileMapper,
                             WmsSecurityContext securityContext) {
        this.authorityMapper = authorityMapper;
        this.profileMapper = profileMapper;
        this.securityContext = securityContext;
    }

    public String getRole() {
        return profileMapper.findByUserId(securityContext.currentUserId()).getRole();
    }

    public List<String> getPermissions() {
        return authorityMapper.findAuthoritiesByUserId(securityContext.currentUserId());
    }

    public List<String> getPermissionsByUserId(Long userId) {
        WmsUserProfile target = profileMapper.findByUserId(userId);
        Assert.isTrue(target != null && target.getGroupId() == securityContext.currentGroupId(),
                "target user is not in your group");
        return authorityMapper.findAuthoritiesByUserId(userId);
    }

    @Transactional
    public void updatePermissions(Long userId, boolean shopping, boolean inventory, boolean statistics) {
        WmsUserProfile target = profileMapper.findByUserId(userId);
        Assert.isTrue(target != null && target.getGroupId() == securityContext.currentGroupId(),
                "target user is not in your group");
        authorityMapper.deleteAllPermissions(userId);
        if (shopping)    authorityMapper.insertAuthority(userId, SystemAuthority.Permission.SHOPPING.value());
        if (inventory)   authorityMapper.insertAuthority(userId, SystemAuthority.Permission.INVENTORY.value());
        if (statistics)  authorityMapper.insertAuthority(userId, SystemAuthority.Permission.STATISTICS.value());
    }
}
