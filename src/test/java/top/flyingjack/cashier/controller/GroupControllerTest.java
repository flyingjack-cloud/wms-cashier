package top.flyingjack.cashier.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.flyingjack.cashier.service.AuthorityService;
import top.flyingjack.cashier.service.GroupService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    @Mock GroupService groupService;
    @Mock AuthorityService authorityService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupService, authorityService)).build();
    }

    @Test
    void createGroup_parsesIso8601CreateTimeParam() throws Exception {
        mockMvc.perform(post("/group/")
                        .param("storeName", "My Store")
                        .param("createTime", "2025-01-01T00:00:00Z"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(groupService).createGroup(
                eq("My Store"), eq(null), eq(null), eq(Instant.parse("2025-01-01T00:00:00Z")));
    }
}
