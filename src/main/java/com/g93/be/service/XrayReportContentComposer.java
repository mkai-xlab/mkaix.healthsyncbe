package com.g93.be.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes the Vietnamese result text printed in the "KET QUA" and "KET LUAN" blocks of the X-ray
 * report from the Kellgren-Lawrence grades a doctor confirmed during verification.
 *
 * <p>The text states the grade and nothing else: the radiographic signs behind it are the doctor's
 * to describe. Both blocks are editable, so this is only the starting point the doctor sees in the
 * preview before confirming.
 */
@Component
public class XrayReportContentComposer {

    private static final int MAX_GRADE = 4;

    /** Builds one line per knee that has a verified grade. */
    public List<String> composeFindings(String leftKlGrade, String rightKlGrade) {
        List<String> findings = new ArrayList<>();
        addSideFinding(findings, "G\u1ED1i ph\u1EA3i", rightKlGrade);
        addSideFinding(findings, "G\u1ED1i tr\u00E1i", leftKlGrade);
        if (findings.isEmpty()) {
            findings.add("Kh\u00F4ng c\u00F3 \u0111\u1ED9 Kellgren-Lawrence \u0111\u00E3 x\u00E1c nh\u1EADn cho c\u1EA3 hai kh\u1EDBp g\u1ED1i.");
        }
        return findings;
    }

    /** Builds the single-sentence conclusion naming the grade of each verified knee. */
    public String composeConclusion(String leftKlGrade, String rightKlGrade) {
        Integer left = parseGrade(leftKlGrade);
        Integer right = parseGrade(rightKlGrade);
        if (left == null && right == null) {
            return "Kh\u00F4ng th\u1EA5y h\u00ECnh \u1EA3nh tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i tr\u00EAn phim X-quang.";
        }
        if (left != null && right != null && left.equals(right)) {
            return left == 0
                    ? "Kh\u00F4ng th\u1EA5y h\u00ECnh \u1EA3nh tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i hai b\u00EAn."
                    : "H\u00ECnh \u1EA3nh tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i hai b\u00EAn \u0111\u1ED9 " + left
                            + " theo ph\u00E2n lo\u1EA1i Kellgren-Lawrence.";
        }
        List<String> parts = new ArrayList<>();
        if (right != null) {
            parts.add("g\u1ED1i ph\u1EA3i \u0111\u1ED9 " + right);
        }
        if (left != null) {
            parts.add("g\u1ED1i tr\u00E1i \u0111\u1ED9 " + left);
        }
        return "H\u00ECnh \u1EA3nh tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i: " + String.join(", ", parts)
                + " theo ph\u00E2n lo\u1EA1i Kellgren-Lawrence.";
    }

    private void addSideFinding(List<String> findings, String sideLabel, String klGrade) {
        Integer grade = parseGrade(klGrade);
        if (grade == null) {
            return;
        }
        if (grade == 0) {
            findings.add(sideLabel + ": Kh\u00F4ng tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i (Kellgren-Lawrence \u0111\u1ED9 0).");
            return;
        }
        findings.add(sideLabel + ": Tho\u00E1i h\u00F3a kh\u1EDBp g\u1ED1i \u0111\u1ED9 " + grade + " (Kellgren-Lawrence).");
    }

    private Integer parseGrade(String klGrade) {
        if (klGrade == null || klGrade.isBlank()) {
            return null;
        }
        try {
            int grade = Integer.parseInt(klGrade.trim());
            return grade >= 0 && grade <= MAX_GRADE ? grade : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
