package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.MeCount;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;

import java.util.List;

@Mapper
public interface MerchandiseMapper {
    int countByGroupAndSold(@Param("groupId") int groupId, @Param("sold") boolean sold);
    List<MerchandiseWithCategoryDto> findByGroupPaged(@Param("groupId") int groupId, @Param("sold") boolean sold,
                                       @Param("limit") int limit, @Param("offset") int offset);
    List<Merchandise> findByCateId(@Param("groupId") int groupId, @Param("cateId") int cateId);
    List<Merchandise> searchByGroupAndText(@Param("groupId") int groupId,
                                           @Param("text") String text, @Param("sold") boolean sold);
    void insert(Merchandise merchandise);
    void update(@Param("id") int id, @Param("cost") java.math.BigDecimal cost,
                @Param("price") java.math.BigDecimal price, @Param("imei") String imei,
                @Param("groupId") int groupId);
    void deleteById(@Param("id") int id, @Param("groupId") int groupId);
    List<MeCount> accountByGroup(@Param("groupId") int groupId);
}
