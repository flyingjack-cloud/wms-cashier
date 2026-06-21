package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.Order;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrderMapper {
    void insert(Order order);
    void insertBatch(@Param("orders") List<Order> orders);
    List<Order> findByGroupAndDateRange(@Param("groupId") int groupId,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);
    void markReturned(@Param("id") int id, @Param("groupId") int groupId);
}
