package top.flyingjack.cashier.entity;

import java.math.BigDecimal;
import java.time.Instant;

public class Merchandise {
    private int id;
    private int groupId;
    private int cateId;
    private BigDecimal cost;
    private BigDecimal price;
    private String imei;
    private boolean sold;
    private Instant createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public int getCateId() { return cateId; }
    public void setCateId(int cateId) { this.cateId = cateId; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public boolean isSold() { return sold; }
    public void setSold(boolean sold) { this.sold = sold; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
