package top.flyingjack.cashier.entity;

/**
 * 商品分类
 *
 * @author Zumin Li
 * @date 2024/2/10 20:37
 */
public class Category {
    private int id;
    private int parentId; // 父分类id，用于多级分类，0表示一级分类
    private String name;

    private int ownerId;
    private int groupId;

    public Category(int id, int parentId, String name, int ownerId, int groupId) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.ownerId = ownerId;
        this.groupId = groupId;
    }
}
