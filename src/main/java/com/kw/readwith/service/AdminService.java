package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.dto.admin.CharacterInfoDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ObjectMapper objectMapper;

    public void uploadCharacters(Long bookId, MultipartFile file) {
        // 1. bookId로 Book 엔터티 조회
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            // 2. JSON 파일을 DTO로 파싱
            CharacterInfoDTO.CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterInfoDTO.CharacterListDTO.class);

            List<Character> newCharacters = new ArrayList<>();
            for (CharacterInfoDTO dto : characterListDTO.getCharacters()) {
                // 3. 해당 책에 이 이름을 가진 인물이 이미 있는지 확인
                Optional<Character> existingCharacter = characterRepository.findByBookAndName(book, dto.getCommonName());

                // 4. 존재하지 않을 경우에만, 새로운 Character 엔티티를 생성하여 리스트에 추가
                if (existingCharacter.isEmpty()) {
                    Character character = Character.builder()
                            .book(book)
                            .characterId(dto.getId().longValue())
                            .name(dto.getCommonName()) // DTO 필드명 변경 적용
                            .names(String.join(",", dto.getNames()))
                            .isMainCharacter(dto.isMainCharacter())
                            .personalityText(dto.getDescription())
                            .profileText(dto.getPortraitPrompt()) // profile_text 저장 로직 추가
                            .build();
                    newCharacters.add(character);
                }
            }

            // 5. 새로 추가할 인물들이 있을 경우에만, 데이터베이스에 한 번에 저장
            if (!newCharacters.isEmpty()) {
                characterRepository.saveAll(newCharacters);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }
    }
}
