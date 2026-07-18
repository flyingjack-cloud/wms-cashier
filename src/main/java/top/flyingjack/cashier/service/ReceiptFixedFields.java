package top.flyingjack.cashier.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReceiptFixedFields {
    public static final Map<String, String> LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("store.storeName", "店铺名称");
        labels.put("store.address", "店铺地址");
        labels.put("order.sellingTime", "销售时间");
        labels.put("order.brand", "品牌");
        labels.put("order.model", "型号");
        labels.put("order.imei", "IMEI");
        labels.put("order.sellingPrice", "销售价格");
        labels.put("order.cost", "成本");
        labels.put("cashier.printedBy", "打印人");
        LABELS = Map.copyOf(labels);
    }

    private ReceiptFixedFields() {
    }
}
