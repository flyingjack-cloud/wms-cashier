package top.flyingjack.cashier.entity;

public class MerchandiseWithCategoryDto extends Merchandise {
    private Category category;

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
