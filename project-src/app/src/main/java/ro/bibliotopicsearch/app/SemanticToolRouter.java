package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selects only the offline semantic tools needed by the detected meaning. */
public final class SemanticToolRouter {
    private SemanticToolRouter() {}

    public enum Tool {
        IDENTITY_CLASSIFIER,
        SUBJECT_FRAME,
        DEPENDENCY_GRAPH,
        COREFERENCE,
        CAUSAL_GRAPH,
        EFFECT_GRAPH,
        MECHANISM_GRAPH,
        TEMPORAL_GRAPH,
        SPATIAL_GRAPH,
        POPULATION_TARGETING,
        CONDITION_SCOPE,
        PURPOSE_GRAPH,
        COMPARISON_ALIGNER,
        QUANTITY_EXTRACTOR,
        EVIDENCE_BINDER,
        CLAIM_EXTRACTOR,
        PROBLEM_SOLUTION,
        RISK_EXTRACTOR,
        TAXONOMY,
        SEQUENCE,
        OPERATOR_SCOPE
    }

    public enum Mode {
        IDENTIFY,
        DEFINE,
        EXPLAIN_CAUSE,
        TRACE_EFFECT,
        TRACE_MECHANISM,
        ORDER_SEQUENCE,
        LOCATE,
        TIME_POINT,
        TIME_RANGE,
        CAUSAL_PRECEDENCE,
        TARGET_POPULATION,
        APPLY_CONDITION,
        TRACE_PURPOSE,
        COMPARE_ATTRIBUTES,
        COMPARE_VALUES,
        MEASURE_EFFECT,
        MEASURE_POPULATION,
        MEASURE_CHANGE,
        BIND_EVIDENCE,
        EXTRACT_CLAIM,
        FIND_PROBLEM,
        FIND_SOLUTION,
        ESTIMATE_RISK,
        CLASSIFY,
        PRESERVE_SCOPE
    }

    public static final class Route {
        private final Tool tool;
        private final Mode mode;
        private final int priority;
        private final String reason;

        Route(Tool tool, Mode mode, int priority, String reason) {
            this.tool = tool;
            this.mode = mode;
            this.priority = Math.max(0, Math.min(100, priority));
            this.reason = reason == null ? "" : reason;
        }

        public Tool tool() { return tool; }
        public Mode mode() { return mode; }
        public int priority() { return priority; }
        public String reason() { return reason; }
        public String compactLabel() { return tool.name() + ":" + mode.name(); }
    }

    public static List<Route> route(
            UniversalSubjectFrame.Frame frame,
            SemanticQueryMatrix.Matrix matrix
    ) {
        if (frame == null && matrix == null) return Collections.emptyList();
        List<Route> routes = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        add(routes, dedupe, Tool.SUBJECT_FRAME, Mode.IDENTIFY, 100, "subject frame");
        add(routes, dedupe, Tool.OPERATOR_SCOPE, Mode.PRESERVE_SCOPE, 94, "explicit semantic operators");
        add(routes, dedupe, Tool.COREFERENCE, Mode.IDENTIFY, 90, "discourse continuity");

        if (frame != null) {
            for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
                switch (axis) {
                    case CAUSE:
                        add(routes, dedupe, Tool.CAUSAL_GRAPH, Mode.EXPLAIN_CAUSE, 100, "CAUSE axis");
                        break;
                    case EFFECT:
                        add(routes, dedupe, Tool.EFFECT_GRAPH, Mode.TRACE_EFFECT, 100, "EFFECT axis");
                        break;
                    case MECHANISM:
                        add(routes, dedupe, Tool.MECHANISM_GRAPH, Mode.TRACE_MECHANISM, 98, "MECHANISM axis");
                        break;
                    case TIME:
                        add(routes, dedupe, Tool.TEMPORAL_GRAPH, Mode.TIME_POINT, 88, "TIME axis");
                        break;
                    case LOCATION:
                        add(routes, dedupe, Tool.SPATIAL_GRAPH, Mode.LOCATE, 88, "LOCATION axis");
                        break;
                    case POPULATION:
                    case TARGET:
                        add(routes, dedupe, Tool.POPULATION_TARGETING, Mode.TARGET_POPULATION, 94, "target/population axis");
                        break;
                    case CONDITION:
                        add(routes, dedupe, Tool.CONDITION_SCOPE, Mode.APPLY_CONDITION, 96, "CONDITION axis");
                        break;
                    case PURPOSE:
                        add(routes, dedupe, Tool.PURPOSE_GRAPH, Mode.TRACE_PURPOSE, 94, "PURPOSE axis");
                        break;
                    case COMPARISON:
                        add(routes, dedupe, Tool.COMPARISON_ALIGNER, Mode.COMPARE_ATTRIBUTES, 96, "COMPARISON axis");
                        break;
                    case QUANTITY:
                        add(routes, dedupe, Tool.QUANTITY_EXTRACTOR, quantityMode(frame), 96, "QUANTITY axis");
                        break;
                    case EVIDENCE:
                        add(routes, dedupe, Tool.EVIDENCE_BINDER, Mode.BIND_EVIDENCE, 100, "EVIDENCE axis");
                        break;
                    case PROBLEM:
                        add(routes, dedupe, Tool.PROBLEM_SOLUTION, Mode.FIND_PROBLEM, 94, "PROBLEM axis");
                        break;
                    case SOLUTION:
                        add(routes, dedupe, Tool.PROBLEM_SOLUTION, Mode.FIND_SOLUTION, 94, "SOLUTION axis");
                        break;
                    case RISK:
                        add(routes, dedupe, Tool.RISK_EXTRACTOR, Mode.ESTIMATE_RISK, 92, "RISK axis");
                        break;
                    default:
                        break;
                }
            }
            if (frame.type() == UniversalSubjectFrame.OntologyType.CLASS) {
                add(routes, dedupe, Tool.TAXONOMY, Mode.CLASSIFY, 90, "class subject");
            }
            if (frame.type() == UniversalSubjectFrame.OntologyType.PROCESS) {
                add(routes, dedupe, Tool.SEQUENCE, Mode.ORDER_SEQUENCE, 88, "process subject");
            }
        }

        if (matrix != null) {
            for (SemanticQueryMatrix.QuerySlot slot : matrix.slots()) {
                int priority = matrix.priority(slot);
                switch (slot) {
                    case WHAT:
                        add(routes, dedupe, Tool.IDENTITY_CLASSIFIER, Mode.DEFINE, priority, "WHAT query");
                        break;
                    case WHY:
                        add(routes, dedupe, Tool.CAUSAL_GRAPH, Mode.EXPLAIN_CAUSE, priority, "WHY query");
                        break;
                    case HOW:
                        add(routes, dedupe, Tool.MECHANISM_GRAPH, Mode.TRACE_MECHANISM, priority, "HOW query");
                        break;
                    case EFFECT:
                        add(routes, dedupe, Tool.EFFECT_GRAPH, Mode.TRACE_EFFECT, priority, "EFFECT query");
                        break;
                    case WHEN:
                        add(routes, dedupe, Tool.TEMPORAL_GRAPH, temporalMode(frame), priority, "WHEN query");
                        break;
                    case WHERE:
                        add(routes, dedupe, Tool.SPATIAL_GRAPH, Mode.LOCATE, priority, "WHERE query");
                        break;
                    case WHO:
                    case TARGET:
                        add(routes, dedupe, Tool.POPULATION_TARGETING, Mode.TARGET_POPULATION, priority, "WHO/TARGET query");
                        break;
                    case CONDITION:
                        add(routes, dedupe, Tool.CONDITION_SCOPE, Mode.APPLY_CONDITION, priority, "CONDITION query");
                        break;
                    case PURPOSE:
                        add(routes, dedupe, Tool.PURPOSE_GRAPH, Mode.TRACE_PURPOSE, priority, "PURPOSE query");
                        break;
                    case COMPARISON:
                        add(routes, dedupe, Tool.COMPARISON_ALIGNER,
                                frame != null && frame.hasAxis(UniversalSubjectFrame.Axis.QUANTITY)
                                        ? Mode.COMPARE_VALUES : Mode.COMPARE_ATTRIBUTES,
                                priority, "COMPARISON query");
                        break;
                    case QUANTITY:
                        add(routes, dedupe, Tool.QUANTITY_EXTRACTOR, quantityMode(frame), priority, "QUANTITY query");
                        break;
                    case EVIDENCE:
                        add(routes, dedupe, Tool.EVIDENCE_BINDER, Mode.BIND_EVIDENCE, priority, "EVIDENCE query");
                        break;
                    case CLAIM:
                        add(routes, dedupe, Tool.CLAIM_EXTRACTOR, Mode.EXTRACT_CLAIM, priority, "CLAIM query");
                        break;
                    case PROBLEM:
                        add(routes, dedupe, Tool.PROBLEM_SOLUTION, Mode.FIND_PROBLEM, priority, "PROBLEM query");
                        break;
                    case SOLUTION:
                        add(routes, dedupe, Tool.PROBLEM_SOLUTION, Mode.FIND_SOLUTION, priority, "SOLUTION query");
                        break;
                    case RISK:
                        add(routes, dedupe, Tool.RISK_EXTRACTOR, Mode.ESTIMATE_RISK, priority, "RISK query");
                        break;
                    default:
                        break;
                }
            }
        }

        routes.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return Collections.unmodifiableList(routes);
    }

    private static Mode quantityMode(UniversalSubjectFrame.Frame frame) {
        if (frame != null) {
            if (frame.hasAxis(UniversalSubjectFrame.Axis.EFFECT)) return Mode.MEASURE_EFFECT;
            if (frame.hasAxis(UniversalSubjectFrame.Axis.POPULATION)) return Mode.MEASURE_POPULATION;
            if (frame.hasAxis(UniversalSubjectFrame.Axis.TIME)) return Mode.MEASURE_CHANGE;
            if (frame.hasAxis(UniversalSubjectFrame.Axis.COMPARISON)) return Mode.COMPARE_VALUES;
        }
        return Mode.MEASURE_CHANGE;
    }

    private static Mode temporalMode(UniversalSubjectFrame.Frame frame) {
        if (frame != null && frame.hasAxis(UniversalSubjectFrame.Axis.CAUSE)) return Mode.CAUSAL_PRECEDENCE;
        if (frame != null && frame.type() == UniversalSubjectFrame.OntologyType.PROCESS) return Mode.TIME_RANGE;
        return Mode.TIME_POINT;
    }

    private static void add(
            List<Route> routes,
            Set<String> dedupe,
            Tool tool,
            Mode mode,
            int priority,
            String reason
    ) {
        String key = tool.name() + "|" + mode.name();
        if (dedupe.add(key)) routes.add(new Route(tool, mode, priority, reason));
    }
}
