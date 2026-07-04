package top.flyingjack.cashier.entity;

public class OrderListItemDto extends Order {
    private MerchandiseWithCategoryDto merchandise;

    public MerchandiseWithCategoryDto getMerchandise() { return merchandise; }
    public void setMerchandise(MerchandiseWithCategoryDto merchandise) { this.merchandise = merchandise; }
}
