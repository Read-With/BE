package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "book_event")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    @Column(name = "start_block_index")
    private Integer startBlockIndex;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_block_index")
    private Integer endBlockIndex;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(name = "start_txt_offset", nullable = false)
    private Integer startTxtOffset;

    @Column(name = "end_txt_offset", nullable = false)
    private Integer endTxtOffset;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawText;

    @Column(nullable = false)
    private Integer idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;
}
    
