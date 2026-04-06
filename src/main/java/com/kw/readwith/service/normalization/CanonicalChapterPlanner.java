package com.kw.readwith.service.normalization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CanonicalChapterPlanner {

    private static final Pattern NUMBER_AT_END = Pattern.compile("(\\d+|[IVXLCDM]+)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> PLACEHOLDER_TITLES = List.of(
            "contents",
            "table of contents",
            "index",
            "the full project gutenberg license"
    );

    private final int softMinCodePoints;
    private final int targetMinCodePoints;
    private final int targetMaxCodePoints;
    private final int hardMaxCodePoints;
    private final int softMaxChapterCount;

    CanonicalChapterPlanner() {
        this(3000, 8000, 30000, 45000, 20);
    }

    CanonicalChapterPlanner(
            int softMinCodePoints,
            int targetMinCodePoints,
            int targetMaxCodePoints,
            int hardMaxCodePoints,
            int softMaxChapterCount
    ) {
        this.softMinCodePoints = softMinCodePoints;
        this.targetMinCodePoints = targetMinCodePoints;
        this.targetMaxCodePoints = targetMaxCodePoints;
        this.hardMaxCodePoints = hardMaxCodePoints;
        this.softMaxChapterCount = softMaxChapterCount;
    }

    List<CanonicalChapterPlan> plan(List<SplitUnit> splitUnits) {
        List<SplitUnit> candidates = splitUnits.stream()
                .filter(unit -> !isExcluded(unit.role()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = splitUnits;
        }

        List<UnitGroup> groups = initialGroups(candidates);
        groups = mergeShortGroups(groups);
        if (groups.size() > softMaxChapterCount) {
            groups = bundleGroups(groups, true);
            groups = mergeShortGroups(groups);
        }
        if (groups.size() > softMaxChapterCount) {
            groups = bundleGroups(groups, false);
            groups = mergeShortGroups(groups);
        }
        groups = splitOversizedGroups(groups);
        if (groups.size() > softMaxChapterCount) {
            groups = forceChapterCountWithinLimit(groups);
        }

        List<CanonicalChapterPlan> plans = new ArrayList<>();
        int index = 1;
        for (UnitGroup group : groups) {
            if (group.units().isEmpty()) {
                continue;
            }

            List<String> sourceDocHrefs = orderedSourceDocHrefs(group.units());
            List<String> sourceTitles = group.units().stream().map(SplitUnit::title).toList();
            plans.add(new CanonicalChapterPlan(
                    index++,
                    resolveGroupTitle(group),
                    resolveGroupRole(group),
                    List.copyOf(group.units()),
                    sourceDocHrefs,
                    sourceTitles,
                    group.reason(),
                    group.totalCodePoints()
            ));
        }
        return plans;
    }

    private boolean isExcluded(SplitUnitRole role) {
        return role == SplitUnitRole.FRONTMATTER
                || role == SplitUnitRole.CONTENTS
                || role == SplitUnitRole.LICENSE
                || role == SplitUnitRole.INDEX
                || role == SplitUnitRole.NOTES;
    }

    private List<UnitGroup> initialGroups(List<SplitUnit> units) {
        List<UnitGroup> groups = new ArrayList<>();
        int i = 0;
        while (i < units.size()) {
            SplitUnit unit = units.get(i);
            if (unit.role() == SplitUnitRole.SCENE) {
                String actTitle = closestAncestorMatching(unit, "act ");
                if (actTitle != null) {
                    List<SplitUnit> sceneGroup = new ArrayList<>();
                    sceneGroup.add(unit);
                    int j = i + 1;
                    while (j < units.size()) {
                        SplitUnit next = units.get(j);
                        if (next.role() != SplitUnitRole.SCENE || !Objects.equals(actTitle, closestAncestorMatching(next, "act "))) {
                            break;
                        }
                        sceneGroup.add(next);
                        j++;
                    }
                    groups.add(new UnitGroup(sceneGroup, CanonicalMergeReason.PROMOTE_SCENE_TO_ACT, actTitle));
                    i = j;
                    continue;
                }
            }

            groups.add(new UnitGroup(List.of(unit), CanonicalMergeReason.KEEP_AS_IS, null));
            i++;
        }
        return groups;
    }

    private List<UnitGroup> mergeShortGroups(List<UnitGroup> groups) {
        if (groups.size() < 2) {
            return groups;
        }

        List<UnitGroup> merged = new ArrayList<>();
        int index = 0;
        while (index < groups.size()) {
            UnitGroup current = groups.get(index);
            if (current.totalCodePoints() >= softMinCodePoints || index == groups.size() - 1) {
                merged.add(current);
                index++;
                continue;
            }

            UnitGroup next = groups.get(index + 1);
            if (canMerge(current, next) && current.totalCodePoints() + next.totalCodePoints() <= Math.max(targetMaxCodePoints, hardMaxCodePoints)) {
                merged.add(current.merge(next, current.reason() == CanonicalMergeReason.KEEP_AS_IS
                        && next.reason() == CanonicalMergeReason.KEEP_AS_IS
                        ? CanonicalMergeReason.MERGE_SHORT_SIBLINGS
                        : CanonicalMergeReason.GROUP_SMALL_UNITS));
                index += 2;
                continue;
            }

            merged.add(current);
            index++;
        }
        return merged;
    }

    private List<UnitGroup> bundleGroups(List<UnitGroup> groups, boolean preferParentBundles) {
        List<UnitGroup> bundled = new ArrayList<>();
        UnitGroup current = null;
        String currentKey = null;

        for (UnitGroup group : groups) {
            String groupKey = preferParentBundles ? bundleKey(group) : broadBundleKey(group);
            if (current == null) {
                current = group;
                currentKey = groupKey;
                continue;
            }

            boolean sameKey = Objects.equals(currentKey, groupKey);
            boolean shouldFlush = current.totalCodePoints() >= targetMinCodePoints && (!sameKey || current.totalCodePoints() >= targetMaxCodePoints);
            if (shouldFlush) {
                bundled.add(current);
                current = group;
                currentKey = groupKey;
                continue;
            }

            current = current.merge(group, resolveBundleReason(current, group));
            if (!sameKey) {
                currentKey = groupKey;
            }
        }

        if (current != null) {
            bundled.add(current);
        }
        return bundled;
    }

    private List<UnitGroup> splitOversizedGroups(List<UnitGroup> groups) {
        List<UnitGroup> result = new ArrayList<>();
        for (UnitGroup group : groups) {
            if (group.totalCodePoints() <= hardMaxCodePoints || group.units().size() == 1) {
                result.add(group);
                continue;
            }

            List<SplitUnit> bucket = new ArrayList<>();
            int bucketSize = 0;
            for (SplitUnit unit : group.units()) {
                if (!bucket.isEmpty() && bucketSize >= targetMaxCodePoints) {
                    result.add(new UnitGroup(List.copyOf(bucket), CanonicalMergeReason.GROUP_SMALL_UNITS, group.promotedTitle()));
                    bucket = new ArrayList<>();
                    bucketSize = 0;
                }
                bucket.add(unit);
                bucketSize += unit.totalCodePoints();
            }
            if (!bucket.isEmpty()) {
                result.add(new UnitGroup(List.copyOf(bucket), CanonicalMergeReason.GROUP_SMALL_UNITS, group.promotedTitle()));
            }
        }
        return result;
    }

    private List<UnitGroup> forceChapterCountWithinLimit(List<UnitGroup> groups) {
        if (groups.size() <= softMaxChapterCount) {
            return groups;
        }

        int chunkSize = (int) Math.ceil((double) groups.size() / softMaxChapterCount);
        List<UnitGroup> reduced = collapseByChunk(groups, chunkSize);
        while (reduced.size() > softMaxChapterCount) {
            chunkSize++;
            reduced = collapseByChunk(groups, chunkSize);
        }
        return reduced;
    }

    private List<UnitGroup> collapseByChunk(List<UnitGroup> groups, int chunkSize) {
        List<UnitGroup> collapsed = new ArrayList<>();
        for (int start = 0; start < groups.size(); start += chunkSize) {
            int end = Math.min(groups.size(), start + chunkSize);
            UnitGroup merged = groups.get(start);
            for (int i = start + 1; i < end; i++) {
                merged = merged.merge(groups.get(i), CanonicalMergeReason.PROMOTE_TO_PARENT);
            }
            collapsed.add(merged);
        }
        return collapsed;
    }

    private CanonicalMergeReason resolveBundleReason(UnitGroup current, UnitGroup next) {
        if (containsRole(current, SplitUnitRole.CANTO) || containsRole(next, SplitUnitRole.CANTO)) {
            return CanonicalMergeReason.GROUP_CANTOS;
        }
        return CanonicalMergeReason.PROMOTE_TO_PARENT;
    }

    private boolean containsRole(UnitGroup group, SplitUnitRole role) {
        return group.units().stream().anyMatch(unit -> unit.role() == role);
    }

    private boolean canMerge(UnitGroup left, UnitGroup right) {
        String leftParent = nearestParentTitle(left);
        String rightParent = nearestParentTitle(right);
        if (leftParent != null && Objects.equals(leftParent, rightParent)) {
            return true;
        }
        return containsFlexibleRole(left) || containsFlexibleRole(right);
    }

    private boolean containsFlexibleRole(UnitGroup group) {
        return group.units().stream().anyMatch(unit -> switch (unit.role()) {
            case CANTO, POEM, STORY, ESSAY, UNKNOWN, APPENDIX -> true;
            default -> false;
        });
    }

    private String nearestParentTitle(UnitGroup group) {
        for (SplitUnit unit : group.units()) {
            String parent = nearestMeaningfulAncestorTitle(unit);
            if (parent != null && !parent.isBlank()) {
                return parent;
            }
        }
        return null;
    }

    private String bundleKey(UnitGroup group) {
        SplitUnitRole dominantRole = resolveGroupRole(group);
        for (SplitUnit unit : group.units()) {
            String ancestor = switch (dominantRole) {
                case CANTO -> closestAncestorMatching(unit, "book ");
                case SCENE, ACT -> closestAncestorMatching(unit, "act ");
                default -> unit.nearestAncestorTitle();
            };
            if (ancestor != null && !ancestor.isBlank()) {
                return dominantRole + ":" + ancestor;
            }
        }
        return broadBundleKey(group);
    }

    private String broadBundleKey(UnitGroup group) {
        SplitUnitRole role = resolveGroupRole(group);
        String ancestor = nearestParentTitle(group);
        return role + ":" + (ancestor == null ? "ROOT" : ancestor);
    }

    private SplitUnitRole resolveGroupRole(UnitGroup group) {
        if (group.reason() == CanonicalMergeReason.PROMOTE_SCENE_TO_ACT) {
            return SplitUnitRole.ACT;
        }
        if (group.units().stream().anyMatch(unit -> unit.role() == SplitUnitRole.CANTO)) {
            return SplitUnitRole.CANTO;
        }
        return group.units().get(0).role();
    }

    private String resolveGroupTitle(UnitGroup group) {
        if (group.reason() == CanonicalMergeReason.PROMOTE_SCENE_TO_ACT && group.promotedTitle() != null) {
            return group.promotedTitle();
        }
        if (group.units().size() == 1) {
            return group.units().get(0).title();
        }

        String commonParent = nearestParentTitle(group);
        if (group.reason() == CanonicalMergeReason.GROUP_CANTOS) {
            String rangeTitle = buildRangeTitle(group, commonParent, "Cantos");
            if (rangeTitle != null) {
                return rangeTitle;
            }
        }
        if (commonParent != null && !commonParent.isBlank()) {
            return commonParent;
        }

        String firstTitle = group.units().get(0).title();
        String lastTitle = group.units().get(group.units().size() - 1).title();
        if (Objects.equals(firstTitle, lastTitle)) {
            return firstTitle;
        }
        return firstTitle + " - " + lastTitle;
    }

    private String buildRangeTitle(UnitGroup group, String parentTitle, String label) {
        String firstNumber = extractTrailingNumber(group.units().get(0).title());
        String lastNumber = extractTrailingNumber(group.units().get(group.units().size() - 1).title());
        if (firstNumber == null || lastNumber == null) {
            return parentTitle;
        }
        if (parentTitle == null || parentTitle.isBlank()) {
            return label + " " + firstNumber + "-" + lastNumber;
        }
        return parentTitle + " (" + label + " " + firstNumber + "-" + lastNumber + ")";
    }

    private String extractTrailingNumber(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = NUMBER_AT_END.matcher(title.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String closestAncestorMatching(SplitUnit unit, String tokenPrefix) {
        List<String> path = unit.tocNodePath();
        if (path == null || path.isEmpty()) {
            return null;
        }

        for (int i = path.size() - 2; i >= 0; i--) {
            String title = path.get(i);
            String normalized = title.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(tokenPrefix) && !isPlaceholderTitle(normalized)) {
                return title;
            }
        }
        return null;
    }

    private String nearestMeaningfulAncestorTitle(SplitUnit unit) {
        List<String> path = unit.tocNodePath();
        if (path == null || path.size() < 2) {
            return null;
        }

        for (int i = path.size() - 2; i >= 0; i--) {
            String title = path.get(i);
            if (title == null || title.isBlank()) {
                continue;
            }
            String normalized = title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!isPlaceholderTitle(normalized)) {
                return title;
            }
        }
        return null;
    }

    private boolean isPlaceholderTitle(String normalizedTitle) {
        return PLACEHOLDER_TITLES.stream().anyMatch(placeholder ->
                normalizedTitle.equals(placeholder) || normalizedTitle.startsWith(placeholder + " "));
    }

    private List<String> orderedSourceDocHrefs(List<SplitUnit> units) {
        Set<String> ordered = new LinkedHashSet<>();
        for (SplitUnit unit : units) {
            ordered.add(unit.sourceDocHref());
        }
        return List.copyOf(ordered);
    }

    private record UnitGroup(
            List<SplitUnit> units,
            CanonicalMergeReason reason,
            String promotedTitle
    ) {

        int totalCodePoints() {
            return units.stream().mapToInt(SplitUnit::totalCodePoints).sum();
        }

        UnitGroup merge(UnitGroup other, CanonicalMergeReason mergeReason) {
            List<SplitUnit> mergedUnits = new ArrayList<>(units);
            mergedUnits.addAll(other.units);
            return new UnitGroup(List.copyOf(mergedUnits), mergeReason, promotedTitle != null ? promotedTitle : other.promotedTitle);
        }
    }
}
