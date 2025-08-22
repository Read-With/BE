package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RelationshipDTO {
    private List<String> relation;
    private Double id1;
    private Double id2;
    private Double positivity;
    private Double weight;
    private Integer count;
}
