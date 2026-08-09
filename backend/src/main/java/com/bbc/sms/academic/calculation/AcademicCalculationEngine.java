package com.bbc.sms.academic.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure, persistence-free academic result calculations.
 *
 * Values stay unrounded until a presentation boundary.  A result is a
 * product: T3 is not an alias for Annual, and child blockers are propagated
 * instead of silently turning missing evidence into zero.
 */
public final class AcademicCalculationEngine {
    public static final BigDecimal TWENTY = BigDecimal.valueOf(20);

    private AcademicCalculationEngine() {}

    public enum MarkStatus { SCORED, ABSENT, EXEMPT, MISSING }
    public enum Product { SEQUENCE, TERM, ANNUAL }

    public record AssessmentInput(BigDecimal score, BigDecimal maxScore, BigDecimal weight,
                                  MarkStatus status) {
        public AssessmentInput {
            status = status == null ? MarkStatus.SCORED : status;
            if (status == MarkStatus.SCORED) {
                Objects.requireNonNull(score, "score");
                Objects.requireNonNull(maxScore, "maxScore");
                if (maxScore.signum() <= 0) throw new IllegalArgumentException("maxScore must be positive");
                if (score.signum() < 0 || score.compareTo(maxScore) > 0)
                    throw new IllegalArgumentException("score must be within maxScore");
            }
            weight = weight == null ? BigDecimal.ONE : weight;
            if (weight.signum() < 0) throw new IllegalArgumentException("weight must not be negative");
        }
    }

    public record WeightedValue(String code, BigDecimal value, BigDecimal weight, boolean optional) {
        public WeightedValue {
            if (value == null) throw new IllegalArgumentException("value is required");
            weight = weight == null ? BigDecimal.ONE : weight;
            if (weight.signum() <= 0) throw new IllegalArgumentException("weight must be positive");
        }
    }

    public record Result(Product product, BigDecimal value, BigDecimal denominator,
                         List<String> blockers, List<String> includedComponents) {
        public Result {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            includedComponents = includedComponents == null ? List.of() : List.copyOf(includedComponents);
        }

        public boolean complete() { return blockers.isEmpty() && (value != null || exempt()); }

        /** A fully exempt subject/product has no comparable value and is not a blocker. */
        public boolean exempt() { return blockers.isEmpty() && value == null && denominator.signum() == 0; }

        public BigDecimal display(int scale) {
            return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
        }
    }

    public static Result sequence(List<AssessmentInput> assessments) {
        List<String> blockers = new ArrayList<>();
        List<WeightedValue> values = new ArrayList<>();
        boolean hasEvidence = false;
        boolean hasExemption = false;
        int index = 0;
        for (AssessmentInput assessment : assessments == null ? List.<AssessmentInput>of() : assessments) {
            String label = "assessment-" + (++index);
            switch (assessment.status()) {
                case EXEMPT -> { hasEvidence = true; hasExemption = true; /* explicitly excluded from the denominator */ }
                case MISSING -> blockers.add(label + ":MISSING");
                case ABSENT -> blockers.add(label + ":ABSENT");
                case SCORED -> {
                    hasEvidence = true;
                    values.add(new WeightedValue(label,
                            assessment.score().multiply(TWENTY).divide(assessment.maxScore(), 12, RoundingMode.HALF_UP),
                            assessment.weight(), false));
                }
            }
        }
        if (values.isEmpty() && !hasExemption && !hasEvidence && blockers.isEmpty()) blockers.add("NO_SCORED_ASSESSMENT");
        Result average = weighted(Product.SEQUENCE, values, blockers);
        return new Result(Product.SEQUENCE, average.value(), average.denominator(), average.blockers(),
                values.stream().map(WeightedValue::code).toList());
    }

    public static Result sequenceFromMarks(List<AssessmentInput> assessments) {
        return sequence(assessments);
    }

    public static Result term(Result s1, Result s2, Result optionalComp) {
        return term(s1, BigDecimal.ONE, s2, BigDecimal.ONE, optionalComp, BigDecimal.ONE);
    }

    /**
     * Aggregate frozen sequence products using the weights stored on the
     * reporting-period dependency graph.  The three-argument overload above
     * remains the convenient equal-weight compatibility form.
     */
    public static Result term(Result s1, BigDecimal s1Weight,
                              Result s2, BigDecimal s2Weight,
                              Result optionalComp, BigDecimal compWeight) {
        requireProduct(s1, Product.SEQUENCE);
        requireProduct(s2, Product.SEQUENCE);
        if (optionalComp != null) requireProduct(optionalComp, Product.SEQUENCE);
        List<WeightedValue> values = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        addChild(values, blockers, "S1", s1, s1Weight, false);
        addChild(values, blockers, "S2", s2, s2Weight, false);
        if (optionalComp != null && optionalComp.value() != null && optionalComp.blockers().isEmpty())
            addChild(values, blockers, "COMP", optionalComp, compWeight, true);
        return weighted(Product.TERM, values, blockers);
    }

    public static Result annual(Result t1, Result t2, Result t3) {
        return annual(t1, BigDecimal.ONE, t2, BigDecimal.ONE, t3, BigDecimal.ONE);
    }

    /** Aggregate frozen term products using the configured dependency weights. */
    public static Result annual(Result t1, BigDecimal t1Weight,
                                Result t2, BigDecimal t2Weight,
                                Result t3, BigDecimal t3Weight) {
        requireProduct(t1, Product.TERM);
        requireProduct(t2, Product.TERM);
        requireProduct(t3, Product.TERM);
        List<WeightedValue> values = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        addChild(values, blockers, "T1", t1, t1Weight, false);
        addChild(values, blockers, "T2", t2, t2Weight, false);
        addChild(values, blockers, "T3", t3, t3Weight, false);
        return weighted(Product.ANNUAL, values, blockers);
    }

    public static BigDecimal weightedOverall(List<WeightedValue> subjects) {
        Result result = weighted(Product.SEQUENCE, subjects, List.of());
        return result.value();
    }

    /** Standard competition ranking over published values; ties share a rank. */
    public static List<Integer> competitionRanks(List<BigDecimal> values) {
        List<BigDecimal> sorted = values == null ? List.of() : values.stream()
                .filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList();
        return (values == null ? List.<BigDecimal>of() : values).stream().map(value -> {
            if (value == null) return null;
            int index = sorted.indexOf(value);
            return index < 0 ? null : index + 1;
        }).toList();
    }

    private static void addChild(List<WeightedValue> values, List<String> blockers, String code,
                                 Result child, boolean optional) {
        addChild(values, blockers, code, child, BigDecimal.ONE, optional);
    }

    private static void addChild(List<WeightedValue> values, List<String> blockers, String code,
                                 Result child, BigDecimal weight, boolean optional) {
        if (child == null || child.exempt()) {
            return;
        }
        if (child.value() == null) {
            if (!optional) blockers.add(code + ":MISSING");
            return;
        }
        if (!child.blockers().isEmpty()) {
            if (!optional) child.blockers().forEach(blocker -> blockers.add(code + ":" + blocker));
            return;
        }
        values.add(new WeightedValue(code, child.value(), weight, optional));
    }

    private static Result weighted(Product product, List<WeightedValue> values, List<String> blockers) {
        BigDecimal denominator = BigDecimal.ZERO;
        BigDecimal numerator = BigDecimal.ZERO;
        for (WeightedValue value : values) {
            numerator = numerator.add(value.value().multiply(value.weight()));
            denominator = denominator.add(value.weight());
        }
        BigDecimal result = denominator.signum() == 0 ? null : numerator.divide(denominator, 12, RoundingMode.HALF_UP);
        return new Result(product, result, denominator, blockers, values.stream().map(WeightedValue::code).toList());
    }

    private static void requireProduct(Result result, Product expected) {
        if (result == null || result.product() != expected)
            throw new IllegalArgumentException("Expected " + expected + " product");
    }
}
