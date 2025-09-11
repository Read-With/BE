package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RelationshipDTO {
    private Long id1;
    private Long id2;
    private List<String> relation;
    private Double positivity;
    private Double weight;
    private Integer count;
}
