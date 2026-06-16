package com.alibaba.assistant.agent.start.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * SqlAuditController Web 层测试（MockMvc 独立模式，不启动整个应用）。
 * 验证 HTTP 路由、查询参数、JSON 序列化和返回结构。
 */
class SqlAuditControllerWebTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SqlAuditTrail trail = new SqlAuditTrail(200);
        trail.record(new SqlAuditEntry("SELECT 1", null, 1, 2, true, "2026-06-15T00:00:00Z"));
        trail.record(new SqlAuditEntry("SELECT * FROM dwd_orders WHERE region_name = ?",
                "[华东]", 12, 5, true, "2026-06-15T00:00:01Z"));
        mockMvc = standaloneSetup(new SqlAuditController(trail)).build();
    }

    @Test
    void recent_returnsAuditEntriesAsJson() throws Exception {
        mockMvc.perform(get("/api/ecommerce/sql-audit/recent").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.total_recorded").value(2))
                // 最新的在最前
                .andExpect(jsonPath("$.entries[0].sql").value("SELECT * FROM dwd_orders WHERE region_name = ?"))
                .andExpect(jsonPath("$.entries[0].params").value("[华东]"))
                .andExpect(jsonPath("$.entries[0].rowCount").value(12))
                .andExpect(jsonPath("$.entries[1].sql").value("SELECT 1"));
    }

    @Test
    void recent_respectsLimit() throws Exception {
        mockMvc.perform(get("/api/ecommerce/sql-audit/recent").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].sql").value("SELECT * FROM dwd_orders WHERE region_name = ?"));
    }

    @Test
    void recent_defaultLimit_whenNoParam() throws Exception {
        mockMvc.perform(get("/api/ecommerce/sql-audit/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }
}
