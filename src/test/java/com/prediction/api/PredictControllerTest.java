package com.prediction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PredictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void predictReturnsBaselineCurveWithReferences() throws Exception {
        mockMvc.perform(multipart("/api/predict")
                        .param("text", "三毛水果艺术")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mode").value("baseline"))
                .andExpect(jsonPath("$.data.prediction.length()").value(30))
                .andExpect(jsonPath("$.data.references.length()", Matchers.greaterThan(0)));
    }

    @Test
    void predictWithImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x0A});
        mockMvc.perform(multipart("/api/predict")
                        .file(file)
                        .param("text", "美食教程")
                        .param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prediction.length()").value(30));
    }

    @Test
    void predictRequiresText() throws Exception {
        mockMvc.perform(multipart("/api/predict"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void predictAlwaysReturnsThirtyDayCurve() throws Exception {
        mockMvc.perform(multipart("/api/predict").param("text", "zzzzzzzzzzzzzzzzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prediction.length()").value(30));
    }
}
