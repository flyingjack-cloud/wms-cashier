package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderExtraTemplateReq {
    private String code;
    private String name;
    private JsonNode schema;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public JsonNode getSchema() { return schema; }
    public void setSchema(JsonNode schema) { this.schema = schema; }
}
