package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.WmsUserProfile;

@Mapper
public interface WmsUserProfileMapper {
    WmsUserProfile findByUserId(@Param("userId") Long userId);
    void insert(WmsUserProfile profile);
    void updateGroupAndRole(@Param("userId") Long userId, @Param("groupId") int groupId, @Param("role") String role);
    void updateRole(@Param("userId") Long userId, @Param("role") String role);
    void updateNickname(@Param("userId") Long userId, @Param("nickname") String nickname);
}
