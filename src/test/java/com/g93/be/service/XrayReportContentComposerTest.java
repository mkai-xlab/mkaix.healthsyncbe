package com.g93.be.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrayReportContentComposerTest {

    private final XrayReportContentComposer composer = new XrayReportContentComposer();

    @Test
    void composesOneGradeLinePerKneeWithTheRightKneeFirst() {
        List<String> findings = composer.composeFindings("2", "4");

        assertEquals(2, findings.size());
        assertEquals("Gối phải: Thoái hóa khớp gối độ 4 (Kellgren-Lawrence).", findings.get(0));
        assertEquals("Gối trái: Thoái hóa khớp gối độ 2 (Kellgren-Lawrence).", findings.get(1));
    }

    @Test
    void statesOnlyTheGradeWithoutDescribingRadiographicSigns() {
        String line = composer.composeFindings(null, "3").getFirst();

        assertFalse(line.contains("gai xương"), "Osteophyte wording must not be auto-written");
        assertFalse(line.contains("khe khớp"), "Joint-space wording must not be auto-written");
        assertFalse(line.contains("phần mềm"), "Soft-tissue wording must not be auto-written");
    }

    @Test
    void skipsAKneeThatHasNoVerifiedGrade() {
        List<String> findings = composer.composeFindings("", "3");

        assertEquals(1, findings.size());
        assertEquals("Gối phải: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence).", findings.getFirst());
    }

    @Test
    void wordsGradeZeroAsNoDegenerationRatherThanDegreeZeroDegeneration() {
        List<String> findings = composer.composeFindings("0", null);

        assertEquals(1, findings.size());
        assertEquals("Gối trái: Không thoái hóa khớp gối (Kellgren-Lawrence độ 0).",
                findings.getFirst());
    }

    @Test
    void fallsBackToASingleLineWhenNeitherKneeIsGraded() {
        List<String> findings = composer.composeFindings(null, "");

        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().contains("Kellgren-Lawrence"));
    }

    @Test
    void mergesEqualGradesIntoASingleBilateralConclusion() {
        assertEquals(
                "Hình ảnh thoái hóa khớp gối hai bên độ 3 theo phân loại Kellgren-Lawrence.",
                composer.composeConclusion("3", "3"));
    }

    @Test
    void namesEachKneeWhenTheGradesDiffer() {
        assertEquals(
                "Hình ảnh thoái hóa khớp gối: gối phải độ 4, gối trái độ 1"
                        + " theo phân loại Kellgren-Lawrence.",
                composer.composeConclusion("1", "4"));
    }

    @Test
    void statesNoDegenerationWhenBothKneesAreGradeZero() {
        assertEquals(
                "Không thấy hình ảnh thoái hóa khớp gối hai bên.",
                composer.composeConclusion("0", "0"));
    }

    @Test
    void statesNoDegenerationWhenNoGradeWasVerified() {
        assertEquals(
                "Không thấy hình ảnh thoái hóa khớp gối trên phim X-quang.",
                composer.composeConclusion(null, "  "));
    }

    @Test
    void ignoresGradesOutsideTheKellgrenLawrenceScale() {
        assertEquals(
                "Không thấy hình ảnh thoái hóa khớp gối trên phim X-quang.",
                composer.composeConclusion("7", "not-a-number"));
        assertEquals(1, composer.composeFindings("7", "not-a-number").size());
    }
}
