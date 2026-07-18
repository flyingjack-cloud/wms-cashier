package top.flyingjack.cashier.entity;

import java.util.List;

public class AvailableFieldsDto {
    private List<AvailableFieldDto> fixed;
    private List<AvailableExtraFieldDto> extra;

    public AvailableFieldsDto() {
    }

    public AvailableFieldsDto(List<AvailableFieldDto> fixed, List<AvailableExtraFieldDto> extra) {
        this.fixed = fixed;
        this.extra = extra;
    }

    public List<AvailableFieldDto> getFixed() { return fixed; }
    public void setFixed(List<AvailableFieldDto> fixed) { this.fixed = fixed; }
    public List<AvailableExtraFieldDto> getExtra() { return extra; }
    public void setExtra(List<AvailableExtraFieldDto> extra) { this.extra = extra; }
}
