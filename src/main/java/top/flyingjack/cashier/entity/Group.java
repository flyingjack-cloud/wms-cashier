package top.flyingjack.cashier.entity;

import java.time.Instant;

public class Group {
    private int id;
    private String storeName;
    private String address;
    private String contact;
    private Instant createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
