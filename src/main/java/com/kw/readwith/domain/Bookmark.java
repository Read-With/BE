package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(indexes = {
    @Index(columnList = "user_id, book_id"),
    @Index(columnList = "book_id, created_at")
})
public class Bookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "start_locator_json", columnDefinition = "TEXT", nullable = false)
    private String startLocatorJson;

    @Column(name = "end_locator_json", columnDefinition = "TEXT")
    private String endLocatorJson;

    @Column(name = "start_txt_offset", nullable = false)
    private Integer startTxtOffset;

    @Column(name = "end_txt_offset")
    private Integer endTxtOffset;

    @Column(name = "locator_version", length = 50)
    private String locatorVersion;

    @Column(name = "color", length = 7)
    private String color; // HEX 색상 코드 (예: #ffd700)

    @Column(name = "memo", length = 1000)
    private String memo; // 사용자 메모

    /**
     * 비즈니스 로직
     */
    public void updateBookmark(String color, String memo) {
        if (color != null) {
            this.color = color;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    /**
     * 범위 선택 북마크인지 확인
     */
    public boolean isRangeBookmark() {
        return endLocatorJson != null && !endLocatorJson.trim().isEmpty();
    }

    /**
     * 단일 위치 북마크인지 확인
     */
    public boolean isPointBookmark() {
        return !isRangeBookmark();
    }
}
