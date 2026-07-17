package top.flyingjack.cashier.entity;

import java.time.Instant;

public class OrderExtraTemplate {
    private long id;
    private int groupId;
    private String code;
    private String name;
    private int version;
    private String schemaJson;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
