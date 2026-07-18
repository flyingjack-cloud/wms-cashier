package top.flyingjack.cashier.entity;

public class AvailableFieldDto {
    private String field;
    private String label;

    public AvailableFieldDto() {
    }

    public AvailableFieldDto(String field, String label) {
        this.field = field;
        this.label = label;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
