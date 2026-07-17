package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderExtraDto {
    private int orderId;
    private String templateCode;
    private String templateName;
    private int templateVersion;
    private JsonNode payload;

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public int getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(int templateVersion) { this.templateVersion = templateVersion; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
