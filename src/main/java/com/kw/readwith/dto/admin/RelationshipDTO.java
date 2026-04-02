package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RelationshipDTO {

    @JsonAlias("id1")
    private String fromCharacterId;

    @JsonAlias("id2")
    private String toCharacterId;

    @JsonAlias("relation")
    private List<String> labels;

    private Double positivity;

    @JsonAlias("count")
    private Integer evidenceCount;
}
