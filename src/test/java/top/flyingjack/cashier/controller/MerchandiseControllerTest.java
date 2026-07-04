package top.flyingjack.cashier.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.flyingjack.cashier.service.MerchandiseService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchandiseControllerTest {

    @Mock MerchandiseService merchandiseService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchandiseController(merchandiseService)).build();
    }

    @Test
    void insertMerchandise_parsesIso8601CreateTimeParam() throws Exception {
        mockMvc.perform(post("/merchandise")
                        .param("cate_id", "2")
                        .param("cost", "100.00")
                        .param("price", "150.00")
                        .param("imei_list", "IMEI001")
                        .param("create_time", "2025-01-01T00:00:00Z"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(merchandiseService).insertMerchandise(
                eq(2), eq(new BigDecimal("100.00")), eq(new BigDecimal("150.00")),
                eq(List.of("IMEI001")), eq(Instant.parse("2025-01-01T00:00:00Z")));
    }

    @Test
    void getMerchandiseByCateId_defaultsSoldToFalse() throws Exception {
        mockMvc.perform(get("/merchandise/cate").param("cate_id", "2"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(merchandiseService).getMerchandiseByCateId(2, false);
    }

    @Test
    void getMerchandise_defaultsSoldToFalse() throws Exception {
        when(merchandiseService.getMerchandiseCount(false)).thenReturn(0);
        when(merchandiseService.getMerchandiseByPage(20, 0, false)).thenReturn(List.of());

        mockMvc.perform(get("/merchandise").param("limit", "20").param("offset", "0"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(merchandiseService).getMerchandiseByPage(20, 0, false);
    }

    @Test
    void search_defaultsSoldToFalse() throws Exception {
        mockMvc.perform(get("/merchandise/search").param("text", "IMEI"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(merchandiseService).searchMerchandise("IMEI", false);
    }
}
