package top.flyingjack.cashier.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.Group;
import top.flyingjack.cashier.entity.SystemAuthority;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.AuthorityMapper;
import top.flyingjack.cashier.mapper.GroupMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;
import top.flyingjack.common.dto.ApiRes;
import top.flyingjack.common.error.ErrorCode;
import top.flyingjack.common.error.exception.BusinessException;

import java.time.Instant;
import java.util.List;

@Service
public class GroupService {
    private final GroupMapper groupMapper;
    private final WmsUserProfileMapper profileMapper;
    private final AuthorityMapper authorityMapper;
    private final AuthServiceClient authServiceClient;
    private final WmsSecurityContext securityContext;

    public GroupService(GroupMapper groupMapper, WmsUserProfileMapper profileMapper,
                        AuthorityMapper authorityMapper, AuthServiceClient authServiceClient,
                        WmsSecurityContext securityContext) {
        this.groupMapper = groupMapper;
        this.profileMapper = profileMapper;
        this.authorityMapper = authorityMapper;
        this.authServiceClient = authServiceClient;
        this.securityContext = securityContext;
    }

    @PreAuthorize("hasRole('DEFAULT')")
    @Transactional
    public void createGroup(String storeName, String address, String contact, Instant createdAt) {
        WmsUserProfile profile = securityContext.currentProfile();
        Assert.isTrue(profile.getGroupId() == 0, "user already in a group");
        Assert.hasText(storeName, "storeName cannot be empty");

        Group group = new Group();
        group.setStoreName(storeName);
        group.setAddress(address != null ? address : "");
        group.setContact(contact != null ? contact : "");
        group.setCreatedAt(createdAt != null ? createdAt : Instant.now());
        groupMapper.insertGroup(group);

        Long userId = securityContext.currentUserId();
        groupMapper.updateGroupOfUser(userId, group.getId());
        profileMapper.updateGroupAndRole(userId, group.getId(), SystemAuthority.Role.OWNER.value());
    }

    public Group getGroupOfCurrentUser() {
        return groupMapper.findByUserId(securityContext.currentUserId());
    }

    @PreAuthorize("hasRole('OWNER')")
    public void updateStoreName(String storeName) {
        groupMapper.updateStoreName(securityContext.currentGroupId(), storeName);
    }

    @PreAuthorize("hasRole('OWNER')")
    public void updateAddress(String address) {
        groupMapper.updateAddress(securityContext.currentGroupId(), address);
    }

    @PreAuthorize("hasRole('OWNER')")
    public void updateContact(String contact) {
        groupMapper.updateContact(securityContext.currentGroupId(), contact);
    }

    @PreAuthorize("hasRole('OWNER')")
    public List<WmsUserProfile> getUsersInGroup() {
        return groupMapper.findProfilesByGroupId(securityContext.currentGroupId());
    }

    @PreAuthorize("hasRole('DEFAULT')")
    public void createJoinRequest(int groupId) {
        WmsUserProfile profile = securityContext.currentProfile();
        Assert.isTrue(profile.getGroupId() == 0, "user already in a group");
        groupMapper.insertJoinRequest(securityContext.currentUserId(), groupId);
    }

    public void createJoinRequestByOwnerPhone(String phone) {
        ApiRes<Long> res = authServiceClient.getUserIdByPhone(phone);
        Assert.notNull(res.getData(), "no user found with that phone");
        Long ownerUserId = res.getData();
        WmsUserProfile ownerProfile = profileMapper.findByUserId(ownerUserId);
        Assert.notNull(ownerProfile, "owner has no WMS profile");
        Assert.isTrue(ownerProfile.getGroupId() > 0, "owner is not in any group");
        createJoinRequest(ownerProfile.getGroupId());
    }

    @PreAuthorize("hasRole('DEFAULT')")
    public Group getGroupInRequest() {
        return groupMapper.findGroupByUserIdInRequest(securityContext.currentUserId());
    }

    @PreAuthorize("hasRole('DEFAULT')")
    public void deleteRequestCurrentUser() {
        groupMapper.deleteJoinRequest(securityContext.currentUserId());
    }

    @PreAuthorize("hasRole('OWNER')")
    public void deleteRequest(Long userId) {
        Group requestedGroup = groupMapper.findGroupByUserIdInRequest(userId);
        Assert.notNull(requestedGroup, "request not found");
        Assert.isTrue(requestedGroup.getId() == securityContext.currentGroupId(), "access denied");
        groupMapper.deleteJoinRequest(userId);
    }

    @PreAuthorize("hasRole('OWNER')")
    public List<WmsUserProfile> getUsersUnderRequest() {
        return groupMapper.findRequestProfilesByGroupId(securityContext.currentGroupId());
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void approveJoinRequest(Long userId, boolean shopping, boolean inventory, boolean statistics) {
        WmsUserProfile target = profileMapper.findByUserId(userId);
        Assert.notNull(target, "user not found");
        Assert.isTrue(target.getGroupId() == 0, "user already in a group");

        int groupId = securityContext.currentGroupId();
        groupMapper.updateGroupOfUser(userId, groupId);
        profileMapper.updateGroupAndRole(userId, groupId, SystemAuthority.Role.STAFF.value());

        authorityMapper.deleteAllPermissions(userId);
        if (shopping)   authorityMapper.insertAuthority(userId, SystemAuthority.Permission.SHOPPING.value());
        if (inventory)  authorityMapper.insertAuthority(userId, SystemAuthority.Permission.INVENTORY.value());
        if (statistics) authorityMapper.insertAuthority(userId, SystemAuthority.Permission.STATISTICS.value());

        groupMapper.deleteJoinRequest(userId);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public void removeUserFromGroup(Long userId) {
        WmsUserProfile target = profileMapper.findByUserId(userId);
        Assert.notNull(target, "user not found");
        Assert.isTrue(target.getGroupId() == securityContext.currentGroupId(), "not in same group");

        groupMapper.updateGroupOfUser(userId, 0);
        profileMapper.updateGroupAndRole(userId, 0, SystemAuthority.Role.DEFAULT.value());
        authorityMapper.deleteAllPermissions(userId);
    }
}
