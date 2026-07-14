package com.kw.readwith.controller;

import com.kw.readwith.dto.admin.AdminImageGenerationStatusResponseDTO;
import com.kw.readwith.web.controller.AdminController;
import com.kw.readwith.web.controller.AdminImageGenerationController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSwaggerDocumentationTest {

    @Test
    void documentsAdminBookDeletionContract() throws NoSuchMethodException {
        Method method = AdminController.class.getDeclaredMethod("deleteBook", Long.class);

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation.summary()).isEqualTo("관리자 도서 삭제");
        assertThat(operation.description())
                .contains("종속된 분석 데이터", "QUEUED", "PROCESSING", "S3");

        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(Arrays.stream(responses.value())
                .map(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode))
                .containsExactlyInAnyOrder("200", "404", "409");

        Parameter bookId = method.getParameters()[0].getAnnotation(Parameter.class);
        assertThat(bookId.description()).contains("삭제할 도서의 DB ID");
    }

    @Test
    void distinguishesCandidateAssetIdFromSlotNumber() throws ReflectiveOperationException {
        Method method = AdminImageGenerationController.class.getDeclaredMethod(
                "selectReferenceCandidate",
                Long.class,
                Long.class
        );

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation.description())
                .contains("referenceCandidates[].id", "asset DB ID", "slotNo(1~4)");

        Parameter candidateId = method.getParameters()[1].getAnnotation(Parameter.class);
        assertThat(candidateId.description())
                .contains("referenceCandidates[].id", "slotNo(1~4)가 아닙니다");

        Schema idSchema = AdminImageGenerationStatusResponseDTO.ReferenceCandidate.class
                .getDeclaredField("id")
                .getAnnotation(Schema.class);
        Schema slotSchema = AdminImageGenerationStatusResponseDTO.ReferenceCandidate.class
                .getDeclaredField("slotNo")
                .getAnnotation(Schema.class);

        assertThat(idSchema.description()).contains("candidateId");
        assertThat(slotSchema.description()).contains("candidateId로 사용하지 않습니다");
    }
}
