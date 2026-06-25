package com.kw.readwith.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.JwtUtil;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Long existingBookId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        existingBookId = bookRepository.findAll().get(0).getId();
        User user = userRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
        accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("즐겨찾기 추가/조회/삭제 플로우가 정상 동작한다")
    void favorite_flow() throws Exception {
        // 1. 즐겨찾기 추가
        mockMvc.perform(post("/api/favorites/" + existingBookId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 2. 즐겨찾기 목록 조회 – 추가된 책 포함
        MvcResult listResult = mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<BookSummaryDTO> list = objectMapper.readValue(
                readResult(listResult).traverse(),
                new TypeReference<>() {}
        );
        assertThat(list).extracting(BookSummaryDTO::getId).contains(existingBookId);

        // 3. 즐겨찾기 삭제
        mockMvc.perform(delete("/api/favorites/" + existingBookId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 4. 목록 조회 시 비어 있음
        MvcResult listResult2 = mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<BookSummaryDTO> list2 = objectMapper.readValue(
                readResult(listResult2).traverse(),
                new TypeReference<>() {}
        );
        assertThat(list2).extracting(BookSummaryDTO::getId).doesNotContain(existingBookId);
    }

    private JsonNode readResult(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("isSuccess").asBoolean()).isTrue();
        return root.path("result");
    }
}
