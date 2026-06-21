package top.flyingjack.cashier.entity;

public class WmsUserProfile {
    private Long userId;
    private int groupId;
    private String role;
    private String nickname;

    public WmsUserProfile() {}
    public WmsUserProfile(Long userId, int groupId, String role) {
        this.userId = userId; this.groupId = groupId; this.role = role;
    }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
