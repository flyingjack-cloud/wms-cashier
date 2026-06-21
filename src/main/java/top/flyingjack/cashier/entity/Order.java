package top.flyingjack.cashier.entity;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {
    private int id;
    private int groupId;
    private int meId;
    private BigDecimal sellingPrice;
    private Instant sellingTime;
    private String remark;
    private boolean returned;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public int getMeId() { return meId; }
    public void setMeId(int meId) { this.meId = meId; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public Instant getSellingTime() { return sellingTime; }
    public void setSellingTime(Instant sellingTime) { this.sellingTime = sellingTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public boolean isReturned() { return returned; }
    public void setReturned(boolean returned) { this.returned = returned; }
}
