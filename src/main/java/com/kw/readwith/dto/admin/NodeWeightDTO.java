package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "한 인물의 node weight 정보")
public class NodeWeightDTO {

    @Schema(description = "가중치", example = "18.5")
    private double weight;

    @Schema(description = "등장 횟수 또는 누적 횟수", example = "6")
    private int count;
}
