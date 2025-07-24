package com.kw.readwith.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteControllerTest {

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
    @DisplayName("즐겨찾기 추가/조회/삭제 플로우가 정상 동작한다")
    void favorite_flow() throws Exception {
        // 1. 즐겨찾기 추가
        mockMvc.perform(post("/api/favorites/" + existingBookId))
                .andExpect(status().isOk());

        // 2. 즐겨찾기 목록 조회 – 추가된 책 포함
        MvcResult listResult = mockMvc.perform(get("/api/favorites")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<BookSummaryDTO> list = objectMapper.readValue(listResult.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(list).extracting(BookSummaryDTO::getId).contains(existingBookId);

        // 3. 즐겨찾기 삭제
        mockMvc.perform(delete("/api/favorites/" + existingBookId))
                .andExpect(status().isNoContent());

        // 4. 목록 조회 시 비어 있음
        MvcResult listResult2 = mockMvc.perform(get("/api/favorites")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<BookSummaryDTO> list2 = objectMapper.readValue(listResult2.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(list2).extracting(BookSummaryDTO::getId).doesNotContain(existingBookId);
    }
} 