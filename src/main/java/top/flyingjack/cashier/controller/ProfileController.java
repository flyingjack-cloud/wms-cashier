package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.service.AuthorityService;
import top.flyingjack.cashier.service.ProfileService;
import top.flyingjack.common.dto.ApiRes;
import top.flyingjack.common.tool.Verify;

import java.util.List;

@RestController
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final AuthorityService authorityService;

    public ProfileController(ProfileService profileService, AuthorityService authorityService) {
        this.profileService = profileService;
        this.authorityService = authorityService;
    }

    @GetMapping("/role")
    public ResponseEntity<ApiRes<String>> getRole() {
        return ResponseEntity.ok(ApiRes.success(authorityService.getRole()));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiRes<List<String>>> getPermissions() {
        return ResponseEntity.ok(ApiRes.success(authorityService.getPermissions()));
    }

    @PutMapping("/nickname")
    public ResponseEntity<ApiRes<Void>> updateNickname(@RequestParam String nickname) {
        if (!Verify.isNotBlank(nickname)) {
            return ResponseEntity.badRequest().body(ApiRes.fail(org.springframework.http.HttpStatus.BAD_REQUEST));
        }
        profileService.updateNickname(nickname);
        return ResponseEntity.ok(ApiRes.success());
    }
}
