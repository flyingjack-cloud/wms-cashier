package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.Notice;

@Mapper
public interface NoticeMapper {
    Notice findLatestByGroupAndType(@Param("groupId") int groupId, @Param("type") String type);
}
