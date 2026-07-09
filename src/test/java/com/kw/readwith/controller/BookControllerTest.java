package com.kw.readwith.controller;

import com.kw.readwith.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    private Long existingBookId;

    @BeforeEach
    void setUp() {
        existingBookId = bookRepository.findAll().get(0).getId();
    }

    @Test
    @DisplayName("도서 목록 조회 API가 200과 결과 리스트를 반환한다")
    void getBooks_returnsList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/books")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        JsonNode resultJson = readResult(responseJson);
        assertThat(resultJson.isArray()).isTrue();
        assertThat(resultJson.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("단일 도서 조회 API가 200과 상세 정보를 반환한다")
    void getBook_returnsDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/books/" + existingBookId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        JsonNode resultJson = readResult(responseJson);
        assertThat(resultJson.path("id").asLong()).isEqualTo(existingBookId);
        assertThat(resultJson.path("title").asText()).isNotBlank();
    }

    private JsonNode readResult(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        assertThat(root.path("isSuccess").asBoolean()).isTrue();
        return root.path("result");
    }
}
