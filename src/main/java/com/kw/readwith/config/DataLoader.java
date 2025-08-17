package com.kw.readwith.config;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    @Override
    public void run(String... args) throws Exception {
        // 목업 데이터가 이미 존재하는지 확인
        if (userRepository.count() == 0) {
            createMockData();
        }
    }

    private void createMockData() {
        // 사용자 생성 (ID는 자동 생성)
        User user = User.builder()
                .nickname("테스트 사용자")
                .email("test@example.com")
                .provider(Provider.GOOGLE)
                .providerUid("test_provider_uid_123")
                .build();
        User savedUser = userRepository.save(user);

        // 기본 제공 책들 생성 (모든 사용자가 접근 가능)
        Book book1 = Book.builder()
                .title("해리포터와 마법사의 돌")
                .summary(true)
                .author("J.K. 롤링")
                .language("ko")
                .isDefault(true)
                .uploadedBy(null)  // null = 서버 기본 제공
                .build();
        bookRepository.save(book1);

        Book book2 = Book.builder()
                .title("반지의 제왕")
                .summary(true)
                .author("J.R.R. 톨킨")
                .language("ko")
                .isDefault(true)
                .uploadedBy(null)  // null = 서버 기본 제공
                .build();
        bookRepository.save(book2);

        // 사용자별 책 생성 (해당 사용자만 접근 가능)
        Book book3 = Book.builder()
                .title("개인 도서관 - 1984")
                .summary(false)
                .author("조지 오웰")
                .language("ko")
                .isDefault(false)
                .uploadedBy(savedUser)  // 특정 사용자가 업로드한 책
                .build();
        bookRepository.save(book3);

        Book book4 = Book.builder()
                .title("개인 도서관 - 동물농장")
                .summary(false)
                .author("조지 오웰")
                .language("ko")
                .isDefault(false)
                .uploadedBy(savedUser)  // 특정 사용자가 업로드한 책
                .build();
        bookRepository.save(book4);

        System.out.println("목업 데이터가 성공적으로 생성되었습니다!");
        System.out.println("사용자 ID: " + savedUser.getId());
        System.out.println("기본 제공 책 ID: 1, 2 (모든 사용자 접근 가능)");
        System.out.println("사용자별 책 ID: 3, 4 (사용자 ID " + savedUser.getId() + "만 접근 가능)");
    }
} 