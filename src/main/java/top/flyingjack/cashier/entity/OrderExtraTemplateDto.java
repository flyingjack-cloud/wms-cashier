package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderExtraTemplateDto {
    private long id;
    private String code;
    private String name;
    private int version;
    private JsonNode schema;
    private boolean enabled;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public JsonNode getSchema() { return schema; }
    public void setSchema(JsonNode schema) { this.schema = schema; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
