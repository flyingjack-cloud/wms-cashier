package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.flyingjack.cashier.entity.Group;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.AuthorityMapper;
import top.flyingjack.cashier.mapper.GroupMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;
import top.flyingjack.common.dto.ApiRes;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceTest {

    @Mock GroupMapper groupMapper;
    @Mock WmsUserProfileMapper profileMapper;
    @Mock AuthorityMapper authorityMapper;
    @Mock AuthServiceClient authServiceClient;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks GroupService groupService;

    @Test
    void createGroup_failsIfAlreadyInGroup() {
        WmsUserProfile profile = new WmsUserProfile(1L, 5, "ROLE_OWNER");
        when(securityContext.currentUserId()).thenReturn(1L);
        when(securityContext.currentProfile()).thenReturn(profile);

        assertThatThrownBy(() -> groupService.createGroup("我的店", null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createGroup_succeeds_whenDefaultGroup() {
        WmsUserProfile profile = new WmsUserProfile(1L, 0, "ROLE_DEFAULT");
        when(securityContext.currentUserId()).thenReturn(1L);
        when(securityContext.currentProfile()).thenReturn(profile);
        doAnswer(inv -> {
            Group g = inv.getArgument(0); g.setId(1); return null;
        }).when(groupMapper).insertGroup(any());

        groupService.createGroup("我的店", "北京", "13800138000", Instant.now());

        verify(groupMapper).updateGroupOfUser(1L, 1);
        verify(profileMapper).updateGroupAndRole(1L, 1, "ROLE_OWNER");
    }
}
