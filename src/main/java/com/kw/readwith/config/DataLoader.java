package com.kw.readwith.config;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final CharacterRepository characterRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;

    @Override
    public void run(String... args) throws Exception {
        // 목업 데이터가 이미 존재하는지 확인
        System.out.println("DataLoader running... User count: " + userRepository.count());
        System.out.println("Book count: " + bookRepository.count());
        
        if (userRepository.count() == 0) {
            System.out.println("Creating mock data...");
            createMockData();
        } else {
            System.out.println("Mock data already exists, skipping creation.");
            
            // 기존 데이터에 챕터/캐릭터가 없다면 추가
            if (chapterRepository.count() == 0 && characterRepository.count() == 0) {
                System.out.println("Adding missing chapter and character data...");
                Book book1 = bookRepository.findById(1L).orElse(null);
                Book book2 = bookRepository.findById(2L).orElse(null);
                
                if (book1 != null) {
                    System.out.println("Adding data for existing book: " + book1.getTitle());
                    createHarryPotterData(book1);
                }
                if (book2 != null) {
                    System.out.println("Adding data for existing book: " + book2.getTitle());
                    createLordOfTheRingsData(book2);
                }
            }
        }
    }

    @Transactional
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
                .coverImgUrl("https://example.com/covers/harry_potter.jpg")
                .epubPath("https://example.com/epubs/harry_potter.epub")
                .uploadedBy(null)  // null = 서버 기본 제공
                .build();
        Book savedBook1 = bookRepository.save(book1);

        Book book2 = Book.builder()
                .title("반지의 제왕")
                .summary(true)
                .author("J.R.R. 톨킨")
                .language("ko")
                .isDefault(true)
                .coverImgUrl("https://example.com/covers/lotr.jpg")
                .epubPath("https://example.com/epubs/lotr.epub")
                .uploadedBy(null)  // null = 서버 기본 제공
                .build();
        Book savedBook2 = bookRepository.save(book2);
        
        // 첫 번째 책(해리포터)에 대한 상세 데이터 생성
        createHarryPotterData(savedBook1);
        
        // 두 번째 책(반지의 제왕)에 대한 상세 데이터 생성
        createLordOfTheRingsData(savedBook2);

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
        System.out.println("Manifest API 테스트 URL: GET /api/books/1/manifest");
    }

    /**
     * 해리포터와 마법사의 돌 예제 데이터 생성
     */
    private void createHarryPotterData(Book book) {
        // 인물 생성
        Character harry = Character.builder()
                .book(book)
                .characterId(1L)
                .name("해리 포터")
                .names("해리, 포터, 살아남은 아이")
                .isMainCharacter(true)
                .firstChapterIdx(1)
                .personalityText("용감하고 정의로운 마법사. 볼드모트를 물리칠 운명을 가진 소년.")
                .profileText("검은 머리에 번개 모양 흉터가 있는 11세 소년. 둥근 안경을 쓰고 있다.")
                .build();
        characterRepository.save(harry);

        Character hermione = Character.builder()
                .book(book)
                .characterId(2L)
                .name("헤르미온느 그레인저")
                .names("헤르미온느, 그레인저")
                .isMainCharacter(true)
                .firstChapterIdx(6)
                .personalityText("매우 똑똑하고 성실한 마법사. 책을 좋아하고 규칙을 잘 지킨다.")
                .profileText("곱슬한 갈색 머리에 앞니가 큰 똑똑한 소녀.")
                .build();
        characterRepository.save(hermione);

        Character ron = Character.builder()
                .book(book)
                .characterId(3L)
                .name("론 위즐리")
                .names("론, 위즐리")
                .isMainCharacter(true)
                .firstChapterIdx(6)
                .personalityText("충성스럽고 용감한 친구. 체스를 잘하고 유머 감각이 있다.")
                .profileText("빨간 머리에 주근깨가 있는 키 큰 소년.")
                .build();
        characterRepository.save(ron);

        Character hagrid = Character.builder()
                .book(book)
                .characterId(4L)
                .name("루베우스 해그리드")
                .names("해그리드")
                .isMainCharacter(false)
                .firstChapterIdx(4)
                .personalityText("친근하고 따뜻한 거인. 마법 생물을 사랑한다.")
                .profileText("매우 키가 크고 털이 많은 거인 혼혈. 분홍색 우산을 들고 다닌다.")
                .build();
        characterRepository.save(hagrid);

        // 챕터 생성
        Chapter chapter1 = Chapter.builder()
                .book(book)
                .idx(1)
                .title("살아남은 아이")
                .pageStart(1)
                .pageEnd(20)
                .startPos(0)
                .endPos(5000)
                .rawText("더즐리 가족은 프리벳 드라이브 4번지에 살고 있었고, 그들은 자신들이 매우 정상적이라고 자부했다...")
                .summaryText("해리 포터가 더즐리 가족에게 맡겨지고, 마법사들이 볼드모트의 몰락을 축하한다.")
                .povSummariesCached(true)
                .build();
        Chapter savedChapter1 = chapterRepository.save(chapter1);

        Chapter chapter2 = Chapter.builder()
                .book(book)
                .idx(2)
                .title("사라진 유리")
                .pageStart(21)
                .pageEnd(40)
                .startPos(5001)
                .endPos(10000)
                .rawText("해리가 더즐리 가족과 함께 지낸 지 거의 10년이 되었다...")
                .summaryText("해리의 11번째 생일이 다가오고, 동물원에서 뱀과 이야기하는 사건이 일어난다.")
                .povSummariesCached(true)
                .build();
        chapterRepository.save(chapter2);

        Chapter chapter3 = Chapter.builder()
                .book(book)
                .idx(3)
                .title("편지들")
                .pageStart(41)
                .pageEnd(60)
                .startPos(10001)
                .endPos(15000)
                .rawText("동물원 사건 이후, 더즐리 가족은 해리를 더욱 엄하게 다뤘다...")
                .summaryText("호그와트에서 온 편지들이 계속 도착하고, 더즐리 가족은 이를 막으려 한다.")
                .povSummariesCached(true)
                .build();
        chapterRepository.save(chapter3);

        // 이벤트 생성 (첫 번째 챕터만)
        Event event1 = Event.builder()
                .book(book)
                .chapter(savedChapter1)
                .idx(1)
                .startPos(0)
                .endPos(1000)
                .rawText("더즐리 씨는 그루닝스 드릴 회사의 중역이었다. 그는 뚱뚱하고 목이 거의 없는 남자였으며...")
                .build();
        eventRepository.save(event1);

        Event event2 = Event.builder()
                .book(book)
                .chapter(savedChapter1)
                .idx(2)
                .startPos(1001)
                .endPos(2500)
                .rawText("그날 밤 늦게, 맥고나걸 교수가 고양이 모습에서 사람으로 변했다...")
                .build();
        eventRepository.save(event2);

        Event event3 = Event.builder()
                .book(book)
                .chapter(savedChapter1)
                .idx(3)
                .startPos(2501)
                .endPos(5000)
                .rawText("덤블도어가 나타나서 해그리드와 함께 아기 해리를 더즐리 가족에게 맡겼다...")
                .build();
        Event savedEvent3 = eventRepository.save(event3);
        
        // EventRelationshipEdge 관계 데이터 추가 (Admin API 업로드 형태와 동일)
        createHarryPotterRelationships(book, savedEvent3, harry, hermione, ron, hagrid);
    }

    /**
     * 반지의 제왕 예제 데이터 생성
     */
    private void createLordOfTheRingsData(Book book) {
        // 인물 생성
        Character frodo = Character.builder()
                .book(book)
                .characterId(1L)
                .name("프로도 배긴스")
                .names("프로도, 배긴스")
                .isMainCharacter(true)
                .firstChapterIdx(1)
                .personalityText("용감하고 희생정신이 강한 호빗. 반지를 파괴하는 임무를 맡는다.")
                .profileText("작은 키의 호빗으로 곱슬한 갈색 머리를 가지고 있다.")
                .build();
        characterRepository.save(frodo);

        Character gandalf = Character.builder()
                .book(book)
                .characterId(2L)
                .name("간달프")
                .names("간달프, 회색의 간달프, 미스란디르")
                .isMainCharacter(true)
                .firstChapterIdx(1)
                .personalityText("지혜롭고 강력한 마법사. 프로도의 여행을 도와준다.")
                .profileText("긴 회색 수염과 뾰족한 모자를 쓴 늙은 마법사.")
                .build();
        characterRepository.save(gandalf);

        Character aragorn = Character.builder()
                .book(book)
                .characterId(3L)
                .name("아라고른")
                .names("아라고른, 스트라이더, 엘레사르")
                .isMainCharacter(true)
                .firstChapterIdx(10)
                .personalityText("곤도르의 진정한 왕. 용감하고 고귀한 전사.")
                .profileText("키가 크고 검은 머리를 가진 레인저. 날카로운 눈빛을 가지고 있다.")
                .build();
        characterRepository.save(aragorn);

        // 챕터 생성
        Chapter chapter1 = Chapter.builder()
                .book(book)
                .idx(1)
                .title("오래 기다려온 파티")
                .pageStart(1)
                .pageEnd(25)
                .startPos(0)
                .endPos(6000)
                .rawText("빌보 배긴스가 자신의 111번째 생일 파티를 준비하고 있었다...")
                .summaryText("빌보의 생일 파티에서 그가 반지의 힘으로 사라지고, 간달프가 반지의 정체를 밝힌다.")
                .povSummariesCached(true)
                .build();
        Chapter savedChapter1 = chapterRepository.save(chapter1);

        Chapter chapter2 = Chapter.builder()
                .book(book)
                .idx(2)
                .title("과거의 그림자")
                .pageStart(26)
                .pageEnd(50)
                .startPos(6001)
                .endPos(12000)
                .rawText("간달프가 프로도에게 반지의 진실을 말해주었다...")
                .summaryText("간달프가 반지가 사우론의 절대반지임을 밝히고, 프로도가 반지를 파괴해야 함을 알게 된다.")
                .povSummariesCached(true)
                .build();
        chapterRepository.save(chapter2);

        // 이벤트 생성
        Event event1 = Event.builder()
                .book(book)
                .chapter(savedChapter1)
                .idx(1)
                .startPos(0)
                .endPos(2000)
                .rawText("빌보 배긴스는 백엔드에서 자신의 111번째 생일을 맞아 성대한 파티를 열었다...")
                .build();
        eventRepository.save(event1);

        Event event2 = Event.builder()
                .book(book)
                .chapter(savedChapter1)
                .idx(2)
                .startPos(2001)
                .endPos(4000)
                .rawText("파티가 끝난 후, 빌보는 반지를 끼고 사라져버렸다...")
                .build();
        eventRepository.save(event2);
        
        // 반지의 제왕 관계 데이터 추가
        createLordOfTheRingsRelationships(book, event2, frodo, gandalf, aragorn);
    }

    /**
     * 해리포터 관계 데이터 생성 (Admin API 업로드 형태와 동일)
     */
    private void createHarryPotterRelationships(Book book, Event event, Character harry, Character hermione, Character ron, Character hagrid) {
        // 해리 - 해그리드 관계 (긍정적)
        EventRelationshipEdge harryHagrid = EventRelationshipEdge.builder()
                .fromCharacter(harry)
                .toCharacter(hagrid)
                .event(event)
                .sentimentScore(0.8f)  // Admin API의 positivity 필드
                .interactionCount(2)  // Admin API의 count 필드
                .relationTags("[\"care\", \"protection\"]")  // Admin API의 relation 필드 (JSON 문자열)
                .build();
        eventRelationshipEdgeRepository.save(harryHagrid);

        // 해그리드 - 해리 관계 (상호)
        EventRelationshipEdge hagridHarry = EventRelationshipEdge.builder()
                .fromCharacter(hagrid)
                .toCharacter(harry)
                .event(event)
                .sentimentScore(0.7f)
                .interactionCount(2)
                .relationTags("[\"mentorship\", \"guidance\"]")
                .build();
        eventRelationshipEdgeRepository.save(hagridHarry);

        // EventCharacterStat 노드 중요도 데이터 생성
        EventCharacterStat harryStat = EventCharacterStat.builder()
                .event(event)
                .character(harry)
                .nodeWeight(6.5)  // 해리는 주인공이므로 높은 중요도
                .build();
        eventCharacterStatRepository.save(harryStat);

        EventCharacterStat hagridStat = EventCharacterStat.builder()
                .event(event)
                .character(hagrid)
                .nodeWeight(4.2)  // 해그리드는 조연이므로 중간 중요도
                .build();
        eventCharacterStatRepository.save(hagridStat);
    }

    /**
     * 반지의 제왕 관계 데이터 생성
     */
    private void createLordOfTheRingsRelationships(Book book, Event event, Character frodo, Character gandalf, Character aragorn) {
        // 프로도 - 간달프 관계 (신뢰)
        EventRelationshipEdge frodoGandalf = EventRelationshipEdge.builder()
                .fromCharacter(frodo)
                .toCharacter(gandalf)
                .event(event)
                .sentimentScore(0.9f)
                .interactionCount(3)  // Admin API의 count 필드
                .relationTags("[\"trust\", \"guidance\", \"friendship\"]")
                .build();
        eventRelationshipEdgeRepository.save(frodoGandalf);

        // 간달프 - 프로도 관계 (보호)
        EventRelationshipEdge gandalfFrodo = EventRelationshipEdge.builder()
                .fromCharacter(gandalf)
                .toCharacter(frodo)
                .event(event)
                .sentimentScore(0.85f)
                .interactionCount(3)
                .relationTags("[\"protection\", \"mentorship\"]")
                .build();
        eventRelationshipEdgeRepository.save(gandalfFrodo);

        // EventCharacterStat 노드 중요도 데이터 생성
        EventCharacterStat frodoStat = EventCharacterStat.builder()
                .event(event)
                .character(frodo)
                .nodeWeight(7.2)  // 프로도는 주인공이므로 높은 중요도
                .build();
        eventCharacterStatRepository.save(frodoStat);

        EventCharacterStat gandalfStat = EventCharacterStat.builder()
                .event(event)
                .character(gandalf)
                .nodeWeight(6.8)  // 간달프는 중요한 조력자
                .build();
        eventCharacterStatRepository.save(gandalfStat);
    }
}