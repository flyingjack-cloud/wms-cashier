package top.flyingjack.cashier.entity;

public class SystemAuthority {
    public enum Role {
        ADMIN("ROLE_ADMIN"), OWNER("ROLE_OWNER"), STAFF("ROLE_STAFF"), DEFAULT("ROLE_DEFAULT");
        private final String value;
        Role(String value) { this.value = value; }
        public String value() { return value; }
    }

    public enum Permission {
        SHOPPING("PERMISSION:shopping"), INVENTORY("PERMISSION:inventory"), STATISTICS("PERMISSION:statistic");
        private final String value;
        Permission(String value) { this.value = value; }
        public String value() { return value; }
        public static boolean isValid(String p) {
            for (Permission perm : values()) { if (perm.value.equals(p)) return true; }
            return false;
        }
    }
}
