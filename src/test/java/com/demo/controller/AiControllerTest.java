package com.demo.controller;

import com.demo.service.AiService;
import com.smartlibrary.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiController.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiService aiService;

    @Test
    void analyze_shouldReturnOkResult() throws Exception {
        when(aiService.analyze(eq("推荐一本适合初学者的Java书")))
                .thenReturn(Result.ok("mock-answer"));

        mockMvc.perform(get("/api/ai/analyze")
                        .queryParam("question", "推荐一本适合初学者的Java书")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":200")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"data\":\"mock-answer\"")));
    }
}

