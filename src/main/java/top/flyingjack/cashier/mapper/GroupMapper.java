package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.Group;
import top.flyingjack.cashier.entity.WmsUserProfile;

import java.util.List;

@Mapper
public interface GroupMapper {
    void insertGroup(Group group);
    Group findByUserId(@Param("userId") Long userId);
    Group findById(@Param("id") int id);
    void updateGroupOfUser(@Param("userId") Long userId, @Param("groupId") int groupId);
    void updateStoreName(@Param("groupId") int groupId, @Param("storeName") String storeName);
    void updateAddress(@Param("groupId") int groupId, @Param("address") String address);
    void updateContact(@Param("groupId") int groupId, @Param("contact") String contact);
    List<WmsUserProfile> findProfilesByGroupId(@Param("groupId") int groupId);
    void insertJoinRequest(@Param("userId") Long userId, @Param("groupId") int groupId);
    void deleteJoinRequest(@Param("userId") Long userId);
    Group findGroupByUserIdInRequest(@Param("userId") Long userId);
    List<WmsUserProfile> findRequestProfilesByGroupId(@Param("groupId") int groupId);
}
