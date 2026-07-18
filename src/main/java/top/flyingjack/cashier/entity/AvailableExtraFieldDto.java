package top.flyingjack.cashier.entity;

public class AvailableExtraFieldDto {
    private String field;
    private String label;
    private String templateCode;
    private String key;

    public AvailableExtraFieldDto() {
    }

    public AvailableExtraFieldDto(String field, String label, String templateCode, String key) {
        this.field = field;
        this.label = label;
        this.templateCode = templateCode;
        this.key = key;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
}
