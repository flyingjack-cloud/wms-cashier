package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.Group;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.service.AuthorityService;
import top.flyingjack.cashier.service.GroupService;
import top.flyingjack.common.dto.ApiRes;
import top.flyingjack.common.tool.Verify;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/group")
public class GroupController {
    private final GroupService groupService;
    private final AuthorityService authorityService;

    public GroupController(GroupService groupService, AuthorityService authorityService) {
        this.groupService = groupService;
        this.authorityService = authorityService;
    }

    @GetMapping("/")
    public ResponseEntity<ApiRes<Group>> getGroup() {
        return ResponseEntity.ok(ApiRes.success(groupService.getGroupOfCurrentUser()));
    }

    @PostMapping("/")
    public ResponseEntity<ApiRes<Void>> createGroup(
            @RequestParam String storeName,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) Instant createTime) {
        Instant time = createTime != null ? createTime : Instant.now();
        groupService.createGroup(storeName, address, contact, time);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/storename")
    public ResponseEntity<ApiRes<Void>> updateStoreName(@RequestParam String storeName) {
        groupService.updateStoreName(storeName);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/address")
    public ResponseEntity<ApiRes<Void>> updateAddress(@RequestParam String address) {
        groupService.updateAddress(address);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/contact")
    public ResponseEntity<ApiRes<Void>> updateContact(@RequestParam String contact) {
        groupService.updateContact(contact);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/staffs")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<List<WmsUserProfile>>> getUsersInGroup() {
        return ResponseEntity.ok(ApiRes.success(groupService.getUsersInGroup()));
    }

    @DeleteMapping("/staff")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<Void>> deleteUserInGroup(@RequestParam Long userId) {
        groupService.removeUserFromGroup(userId);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PostMapping("/join/id")
    @PreAuthorize("hasRole('DEFAULT')")
    public ResponseEntity<ApiRes<Void>> createRequestByGroupId(@RequestParam int groupId) {
        groupService.createJoinRequest(groupId);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PostMapping("/join/phone")
    @PreAuthorize("hasRole('DEFAULT')")
    public ResponseEntity<ApiRes<Void>> createRequestByOwnerPhone(@RequestParam String phone) {
        groupService.createJoinRequestByOwnerPhone(phone);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/join/")
    @PreAuthorize("hasRole('DEFAULT')")
    public ResponseEntity<ApiRes<Group>> getGroupInRequest() {
        return ResponseEntity.ok(ApiRes.success(groupService.getGroupInRequest()));
    }

    @DeleteMapping("/join/delete")
    @PreAuthorize("hasRole('DEFAULT')")
    public ResponseEntity<ApiRes<Void>> deleteRequestCurrentUser() {
        groupService.deleteRequestCurrentUser();
        return ResponseEntity.ok(ApiRes.success());
    }

    @DeleteMapping("/join/delete/id")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<Void>> deleteRequest(@RequestParam Long userId) {
        groupService.deleteRequest(userId);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/join/users")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<List<WmsUserProfile>>> getUsersUnderRequest() {
        return ResponseEntity.ok(ApiRes.success(groupService.getUsersUnderRequest()));
    }

    @PostMapping("/join/agree")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<Void>> agreeJoinRequest(@RequestParam Long userId,
            @RequestParam boolean shopping, @RequestParam boolean inventory,
            @RequestParam boolean statistics) {
        groupService.approveJoinRequest(userId, shopping, inventory, statistics);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/permissions")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<Void>> updatePermissions(@RequestParam Long userId,
            @RequestParam boolean shopping, @RequestParam boolean inventory,
            @RequestParam boolean statistics) {
        authorityService.updatePermissions(userId, shopping, inventory, statistics);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiRes<List<String>>> getPermissionByUserId(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiRes.success(authorityService.getPermissionsByUserId(userId)));
    }
}
