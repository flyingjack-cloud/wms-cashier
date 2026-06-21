package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorityMapper {
    List<String> findAuthoritiesByUserId(@Param("userId") Long userId);
    void insertAuthority(@Param("userId") Long userId, @Param("authority") String authority);
    void deleteAuthority(@Param("userId") Long userId, @Param("authority") String authority);
    void deleteAllPermissions(@Param("userId") Long userId);
}
