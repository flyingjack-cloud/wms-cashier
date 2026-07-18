package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.ReceiptTemplate;

import java.util.List;

@Mapper
public interface ReceiptTemplateMapper {
    List<ReceiptTemplate> findTemplates(@Param("groupId") int groupId,
                                        @Param("includeDisabled") boolean includeDisabled);
    ReceiptTemplate findTemplateByPrinterType(@Param("groupId") int groupId,
                                              @Param("printerType") String printerType);
    ReceiptTemplate findEnabledTemplateByPrinterType(@Param("groupId") int groupId,
                                                     @Param("printerType") String printerType);
    void insertTemplate(ReceiptTemplate template);
    void updateTemplate(ReceiptTemplate template);
    void updateEnabled(@Param("groupId") int groupId, @Param("printerType") String printerType,
                       @Param("enabled") boolean enabled);
}
