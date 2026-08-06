package com.prediction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsPagedVideos() throws Exception {
        mockMvc.perform(get("/api/videos").param("page", "1").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(282_481))
                .andExpect(jsonPath("$.data.items.length()").value(5));
    }

    @Test
    void categoryFilterWorks() throws Exception {
        mockMvc.perform(get("/api/videos").param("category", "Pets  Animals").param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].category").value("Pets  Animals"));
    }

    @Test
    void searchFindsChineseTitles() throws Exception {
        mockMvc.perform(get("/api/videos").param("q", "三毛水果"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(Matchers.greaterThan(0)));
    }

    @Test
    void detailReturnsFullCurve() throws Exception {
        mockMvc.perform(get("/api/videos/a1fJqcNwCB4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curve.length()").value(30))
                .andExpect(jsonPath("$.data.peakDay").isNumber())
                .andExpect(jsonPath("$.data.day30").value(Matchers.greaterThan(0.0)));
    }

    @Test
    void detailReturns404ForUnknownVideo() throws Exception {
        mockMvc.perform(get("/api/videos/__nope__"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void unknownStaticResourceReturns404() throws Exception {
        mockMvc.perform(get("/nope.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void existingCoverIsServed() throws Exception {
        mockMvc.perform(get("/covers/a1/a1fJqcNwCB4.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    }

    @Test
    void missingCoverFallsBackToPlaceholder() throws Exception {
        mockMvc.perform(get("/covers/ZZ/ZZnotexistZZ.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("image/svg+xml")));
    }
}
