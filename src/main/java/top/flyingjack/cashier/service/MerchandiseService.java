package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import top.flyingjack.cashier.entity.MeCount;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.mapper.MerchandiseMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class MerchandiseService {
    private final MerchandiseMapper merchandiseMapper;
    private final WmsSecurityContext securityContext;

    public MerchandiseService(MerchandiseMapper merchandiseMapper, WmsSecurityContext securityContext) {
        this.merchandiseMapper = merchandiseMapper;
        this.securityContext = securityContext;
    }

    public int getMerchandiseCount(boolean sold) {
        return merchandiseMapper.countByGroupAndSold(securityContext.currentGroupId(), sold);
    }

    public List<Merchandise> getMerchandiseByPage(int limit, int offset, boolean sold) {
        return merchandiseMapper.findByGroupPaged(securityContext.currentGroupId(), sold, limit, offset);
    }

    public List<Merchandise> getMerchandiseByCateId(int cateId) {
        return merchandiseMapper.findByCateId(securityContext.currentGroupId(), cateId);
    }

    public List<Merchandise> searchMerchandise(String text, boolean sold) {
        return merchandiseMapper.searchByGroupAndText(securityContext.currentGroupId(), text, sold);
    }

    public void insertMerchandise(int cateId, BigDecimal cost, BigDecimal price,
                                   List<String> imeiList, Instant createdAt) {
        int groupId = securityContext.currentGroupId();
        for (String imei : imeiList) {
            Merchandise m = new Merchandise();
            m.setGroupId(groupId);
            m.setCateId(cateId);
            m.setCost(cost);
            m.setPrice(price);
            m.setImei(imei);
            m.setSold(false);
            m.setCreatedAt(createdAt);
            merchandiseMapper.insert(m);
        }
    }

    public void updateMerchandise(int id, BigDecimal cost, BigDecimal price, String imei) {
        merchandiseMapper.update(id, cost, price, imei);
    }

    public void deleteMerchandise(int id) {
        merchandiseMapper.deleteById(id);
    }

    public List<MeCount> accountMerchandises() {
        return merchandiseMapper.accountByGroup(securityContext.currentGroupId());
    }
}
