package top.flyingjack.cashier.entity;

public class Category {
    private int id;
    private int groupId;
    private int parentId;
    private String name;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public int getParentId() { return parentId; }
    public void setParentId(int parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
