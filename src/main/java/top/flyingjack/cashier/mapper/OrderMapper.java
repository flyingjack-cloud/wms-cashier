package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.entity.OrderListItemDto;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrderMapper {
    void insert(Order order);
    void insertBatch(@Param("orders") List<Order> orders);
    int countByGroupAndDateRange(@Param("groupId") int groupId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);
    List<OrderListItemDto> findByGroupAndDateRangePaged(@Param("groupId") int groupId,
                                             @Param("start") Instant start,
                                             @Param("end") Instant end,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);
    void markReturned(@Param("id") int id, @Param("groupId") int groupId);
    Integer findMeIdById(@Param("id") int id, @Param("groupId") int groupId);
}
