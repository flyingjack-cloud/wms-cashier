package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraTemplate;

import java.util.List;

@Mapper
public interface OrderExtraMapper {
    List<OrderExtraTemplate> findTemplates(@Param("groupId") int groupId,
                                           @Param("includeDisabled") boolean includeDisabled);
    OrderExtraTemplate findEnabledTemplateByCode(@Param("groupId") int groupId, @Param("code") String code);
    OrderExtraTemplate findTemplateByCode(@Param("groupId") int groupId, @Param("code") String code);
    void insertTemplate(OrderExtraTemplate template);
    void updateTemplate(OrderExtraTemplate template);
    void updateEnabled(@Param("groupId") int groupId, @Param("code") String code,
                       @Param("enabled") boolean enabled);
    void upsertExtra(OrderExtra extra);
    List<OrderExtra> findExtrasByOrderId(@Param("groupId") int groupId, @Param("orderId") int orderId);
    OrderExtra findExtraByOrderIdAndCode(@Param("groupId") int groupId, @Param("orderId") int orderId,
                                         @Param("templateCode") String templateCode);
}
