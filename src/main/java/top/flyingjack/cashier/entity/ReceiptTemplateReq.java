package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.JsonNode;

public class ReceiptTemplateReq {
    private String printerType;
    private JsonNode layout;

    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }
    public JsonNode getLayout() { return layout; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
}
