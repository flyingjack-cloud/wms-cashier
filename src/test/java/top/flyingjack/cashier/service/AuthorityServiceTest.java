package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.mapper.AuthorityMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorityServiceTest {

    @Mock AuthorityMapper authorityMapper;
    @Mock WmsUserProfileMapper profileMapper;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks AuthorityService authorityService;

    @Test
    void updatePermissions_deletesOldAndInsertsNew() {
        when(securityContext.currentUserId()).thenReturn(1L);
        when(securityContext.currentGroupId()).thenReturn(5);
        WmsUserProfile targetProfile = new WmsUserProfile(2L, 5, "ROLE_STAFF");
        when(profileMapper.findByUserId(2L)).thenReturn(targetProfile);

        authorityService.updatePermissions(2L, true, false, true);

        verify(authorityMapper).deleteAllPermissions(2L);
        verify(authorityMapper).insertAuthority(2L, "PERMISSION:shopping");
        verify(authorityMapper, never()).insertAuthority(2L, "PERMISSION:inventory");
        verify(authorityMapper).insertAuthority(2L, "PERMISSION:statistic");
    }
}
