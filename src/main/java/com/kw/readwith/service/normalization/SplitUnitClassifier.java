package com.kw.readwith.service.normalization;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class SplitUnitClassifier {

    private static final Pattern ROMAN_NUMERAL = Pattern.compile("^(?i)[IVXLCDM]+$");
    private static final Pattern CHAPTER_TITLE = Pattern.compile("^(?i)(chapter|letter|prologue|epilogue)\\b.*");
    private static final Pattern BOOK_TITLE = Pattern.compile("^(?i)book\\b.*");
    private static final Pattern PART_TITLE = Pattern.compile("^(?i)part\\b.*");
    private static final Pattern ACT_TITLE = Pattern.compile("^(?i)act\\b.*");
    private static final Pattern SCENE_TITLE = Pattern.compile("^(?i)scene\\b.*");
    private static final Pattern CANTO_TITLE = Pattern.compile("^(?i)canto\\b.*");
    private static final Pattern POEM_TITLE = Pattern.compile("^(?i)(poem|sonnet)\\b.*");
    private static final Pattern STORY_TITLE = Pattern.compile("^(?i)(story|tale)\\b.*");
    private static final Pattern ESSAY_TITLE = Pattern.compile("^(?i)(essay|lecture|sermon)\\b.*");
    private static final Pattern APPENDIX_TITLE = Pattern.compile("^(?i)(appendix|appendices|afterword|epilogue|conclusion)\\b.*");
    private static final Pattern NOTES_TITLE = Pattern.compile("^(?i)(notes?|footnotes?)\\b.*");

    SplitUnitRole classify(String title, List<String> tocNodePath) {
        String normalizedTitle = normalize(title);
        String pathText = normalize(String.join(" / ", tocNodePath == null ? List.of() : tocNodePath));

        if (normalizedTitle.isBlank()) {
            return SplitUnitRole.UNKNOWN;
        }
        if (containsAny(normalizedTitle, "contents", "table of contents")) {
            return SplitUnitRole.CONTENTS;
        }
        if (containsAny(normalizedTitle, "project gutenberg license", "full project gutenberg")) {
            return SplitUnitRole.LICENSE;
        }
        if (normalizedTitle.equals("index") || normalizedTitle.startsWith("index ")) {
            return SplitUnitRole.INDEX;
        }
        if (containsAny(normalizedTitle, "preface", "foreword", "introduction")) {
            return SplitUnitRole.FRONTMATTER;
        }
        if (APPENDIX_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.APPENDIX;
        }
        if (NOTES_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.NOTES;
        }
        if (ACT_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.ACT;
        }
        if (SCENE_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.SCENE;
        }
        if (CANTO_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.CANTO;
        }
        if (BOOK_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.BOOK;
        }
        if (PART_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.PART;
        }
        if (POEM_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.POEM;
        }
        if (STORY_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.STORY;
        }
        if (ESSAY_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.ESSAY;
        }
        if (CHAPTER_TITLE.matcher(normalizedTitle).matches()) {
            return SplitUnitRole.CHAPTER;
        }
        if (ROMAN_NUMERAL.matcher(normalizedTitle).matches()) {
            if (pathText.contains("act ")) {
                return SplitUnitRole.SCENE;
            }
            if (pathText.contains("book ") || pathText.contains("part ")) {
                return SplitUnitRole.CHAPTER;
            }
            return SplitUnitRole.CHAPTER;
        }
        return SplitUnitRole.UNKNOWN;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
