package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class ReceiptTemplateDto {
    private long id;
    private String printerType;
    private JsonNode layout;
    private boolean enabled;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }
    public JsonNode getLayout() { return layout; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
