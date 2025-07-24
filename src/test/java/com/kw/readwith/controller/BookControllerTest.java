package com.kw.readwith.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.dto.book.BookDetailDTO;
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
        List<BookSummaryDTO> list = objectMapper.readValue(responseJson, new TypeReference<>() {});
        assertThat(list).isNotEmpty();
    }

    @Test
    @DisplayName("단일 도서 조회 API가 200과 상세 정보를 반환한다")
    void getBook_returnsDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/books/" + existingBookId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        BookDetailDTO dto = objectMapper.readValue(responseJson, BookDetailDTO.class);
        assertThat(dto.getId()).isEqualTo(existingBookId);
        assertThat(dto.getTitle()).isNotBlank();
    }
} 