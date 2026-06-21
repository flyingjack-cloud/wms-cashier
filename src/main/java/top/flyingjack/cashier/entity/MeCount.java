package top.flyingjack.cashier.entity;

import java.math.BigDecimal;

public class MeCount {
    private String cateName;
    private int total;
    private int sold;
    private BigDecimal totalCost;
    private BigDecimal totalPrice;

    public String getCateName() { return cateName; }
    public void setCateName(String cateName) { this.cateName = cateName; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
