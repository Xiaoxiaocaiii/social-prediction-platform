package com.prediction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void compareTwoVideos() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoIds\":[\"a1fJqcNwCB4\",\"krsBRQbOPQ4\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].curve.length()").value(30))
                .andExpect(jsonPath("$.data.items[0].peakDay").isNumber());
    }

    @Test
    void compareRequiresTwoToThreeIds() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoIds\":[\"a1fJqcNwCB4\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void compareWithUnknownVideoReturns404() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoIds\":[\"a1fJqcNwCB4\",\"__nope__\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
