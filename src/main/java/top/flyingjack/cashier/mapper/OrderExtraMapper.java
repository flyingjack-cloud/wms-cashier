package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraTemplate;

import java.util.List;

@Mapper
public interface OrderExtraMapper {
    List<OrderExtraTemplate> findEnabledTemplates(@Param("groupId") int groupId);
    OrderExtraTemplate findEnabledTemplateByCode(@Param("groupId") int groupId, @Param("code") String code);
    void insertTemplate(OrderExtraTemplate template);
    void upsertExtra(OrderExtra extra);
    List<OrderExtra> findExtrasByOrderId(@Param("groupId") int groupId, @Param("orderId") int orderId);
    OrderExtra findExtraByOrderIdAndCode(@Param("groupId") int groupId, @Param("orderId") int orderId,
                                         @Param("templateCode") String templateCode);
}
