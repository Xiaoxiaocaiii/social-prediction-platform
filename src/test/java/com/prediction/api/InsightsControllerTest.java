package com.prediction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void insightsReturnAllSections() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.categoryStats.length()").value(15))
                .andExpect(jsonPath("$.data.correlation.sampleCount").value(282_481))
                .andExpect(jsonPath("$.data.correlation.pearson",
                        Matchers.allOf(Matchers.greaterThan(0.8), Matchers.lessThanOrEqualTo(1.0))))
                .andExpect(jsonPath("$.data.correlation.scatter.length()").value(3000))
                .andExpect(jsonPath("$.data.decayCurves.overall.length()").value(30))
                .andExpect(jsonPath("$.data.decayCurves.byCategory", Matchers.aMapWithSize(15)));
    }

    @Test
    void overallDecayIsDecreasing() throws Exception {
        String body = mockMvc.perform(get("/api/insights"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);
        double first = node.at("/data/decayCurves/overall/0").asDouble();
        double last = node.at("/data/decayCurves/overall/29").asDouble();
        assertThat(first).isGreaterThan(last);
    }
}
