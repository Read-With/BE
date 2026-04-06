package com.kw.readwith.service.normalization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalChapterPlannerTest {

    @Test
    @DisplayName("planner promotes scenes under the same act into one canonical chapter")
    void plannerPromotesScenesToActs() {
        CanonicalChapterPlanner planner = new CanonicalChapterPlanner();

        List<CanonicalChapterPlan> plans = planner.plan(List.of(
                scene("unit-1", "SCENE I", List.of("PLAY", "ACT I", "SCENE I"), 5000),
                scene("unit-2", "SCENE II", List.of("PLAY", "ACT I", "SCENE II"), 4000),
                scene("unit-3", "SCENE I", List.of("PLAY", "ACT II", "SCENE I"), 4500)
        ));

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).title()).isEqualTo("ACT I");
        assertThat(plans.get(1).title()).isEqualTo("ACT II");
    }

    @Test
    @DisplayName("planner groups many short cantos into fewer canonical chapters when chapter count exceeds the soft cap")
    void plannerGroupsShortCantosWhenTooMany() {
        CanonicalChapterPlanner planner = new CanonicalChapterPlanner(3000, 3000, 6000, 8000, 2);

        List<CanonicalChapterPlan> plans = planner.plan(List.of(
                canto("unit-1", "Canto 1", List.of("Book I", "Canto 1"), 1200),
                canto("unit-2", "Canto 2", List.of("Book I", "Canto 2"), 1100),
                canto("unit-3", "Canto 3", List.of("Book I", "Canto 3"), 1300),
                canto("unit-4", "Canto 4", List.of("Book I", "Canto 4"), 1250),
                canto("unit-5", "Canto 5", List.of("Book I", "Canto 5"), 1280)
        ));

        assertThat(plans.size()).isLessThan(5);
        assertThat(plans.get(0).title()).contains("Book I");
    }

    @Test
    @DisplayName("planner forces large chapter sets into twenty or fewer canonical chapters")
    void plannerForcesLargeChapterSetsIntoTwentyOrFewerChapters() {
        CanonicalChapterPlanner planner = new CanonicalChapterPlanner();

        List<SplitUnit> units = java.util.stream.IntStream.rangeClosed(1, 75)
                .mapToObj(index -> unit(
                        "unit-" + index,
                        "Chapter " + index,
                        SplitUnitRole.CHAPTER,
                        List.of("Novel", "Chapter " + index),
                        2500
                ))
                .toList();

        List<CanonicalChapterPlan> plans = planner.plan(units);

        assertThat(plans).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("planner does not reuse contents as the canonical title when grouping sibling units")
    void plannerSkipsContentsPlaceholderWhenResolvingGroupedTitle() {
        CanonicalChapterPlanner planner = new CanonicalChapterPlanner(3000, 3000, 6000, 8000, 1);

        List<CanonicalChapterPlan> plans = planner.plan(List.of(
                unit("unit-1", "ACT I", SplitUnitRole.ACT, List.of("Contents", "PLAY", "ACT I"), 2500),
                unit("unit-2", "ACT II", SplitUnitRole.ACT, List.of("Contents", "PLAY", "ACT II"), 2500)
        ));

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).title()).isEqualTo("PLAY");
    }

    private SplitUnit scene(String unitId, String title, List<String> path, int totalCodePoints) {
        return unit(unitId, title, SplitUnitRole.SCENE, path, totalCodePoints);
    }

    private SplitUnit canto(String unitId, String title, List<String> path, int totalCodePoints) {
        return unit(unitId, title, SplitUnitRole.CANTO, path, totalCodePoints);
    }

    private SplitUnit unit(
            String unitId,
            String title,
            SplitUnitRole role,
            List<String> path,
            int totalCodePoints
    ) {
        String text = "a".repeat(totalCodePoints);
        return new SplitUnit(
                unitId,
                title,
                role,
                "OEBPS/book.xhtml",
                null,
                null,
                path,
                path.size(),
                List.of(new SplitBlock("OEBPS/book.xhtml", unitId, text)),
                List.of(0),
                List.of(totalCodePoints),
                totalCodePoints
        );
    }
}
