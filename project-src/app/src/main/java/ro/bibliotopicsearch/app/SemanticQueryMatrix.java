package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles title/query + SubjectFrame axes into the semantic questions worth asking. */
public final class SemanticQueryMatrix {
    private SemanticQueryMatrix() {}

    public enum QuerySlot {
        WHAT,
        WHO,
        WHERE,
        WHEN,
        WHY,
        HOW,
        WHICH,
        QUANTITY,
        CONDITION,
        EFFECT,
        COMPARISON,
        PURPOSE,
        EVIDENCE,
        CLAIM,
        TARGET,
        DOMAIN,
        RISK,
        PROBLEM,
        SOLUTION
    }

    public static final class Matrix {
        private final Set<QuerySlot> slots;
        private final Map<QuerySlot, Integer> priority;
        private final Set<ResearchSemanticEngine.Intent> intents;

        Matrix(Set<QuerySlot> slots, Map<QuerySlot, Integer> priority, Set<ResearchSemanticEngine.Intent> intents) {
            this.slots = Collections.unmodifiableSet(new LinkedHashSet<>(slots));
            EnumMap<QuerySlot, Integer> copy = new EnumMap<>(QuerySlot.class);
            copy.putAll(priority);
            this.priority = Collections.unmodifiableMap(copy);
            this.intents = Collections.unmodifiableSet(new LinkedHashSet<>(intents));
        }

        public Set<QuerySlot> slots() { return slots; }
        public int priority(QuerySlot slot) { return priority.getOrDefault(slot, 0); }
        public Set<ResearchSemanticEngine.Intent> intents() { return intents; }
        public List<QuerySlot> orderedSlots() {
            List<QuerySlot> out = new ArrayList<>(slots);
            out.sort((a, b) -> Integer.compare(priority(b), priority(a)));
            return out;
        }
    }

    public static Matrix compile(
            ResearchSemanticEngine.Profile profile,
            UniversalSubjectFrame.Frame frame,
            UniversalDetectionLexicon.Function function
    ) {
        LinkedHashSet<QuerySlot> slots = new LinkedHashSet<>();
        EnumMap<QuerySlot, Integer> priority = new EnumMap<>(QuerySlot.class);
        LinkedHashSet<ResearchSemanticEngine.Intent> intents = new LinkedHashSet<>();

        if (profile != null) intents.addAll(profile.intents());
        if (intents.isEmpty()) intents.add(ResearchSemanticEngine.Intent.TOPIC);

        for (ResearchSemanticEngine.Intent intent : intents) {
            switch (intent) {
                case DEFINITION: high(slots, priority, QuerySlot.WHAT); break;
                case WHY: high(slots, priority, QuerySlot.WHY); medium(slots, priority, QuerySlot.CONDITION); break;
                case HOW: high(slots, priority, QuerySlot.HOW); medium(slots, priority, QuerySlot.WHAT); break;
                case EFFECT: high(slots, priority, QuerySlot.EFFECT); high(slots, priority, QuerySlot.TARGET); break;
                case CONDITION: high(slots, priority, QuerySlot.CONDITION); break;
                case WHEN: high(slots, priority, QuerySlot.WHEN); break;
                case WHERE: high(slots, priority, QuerySlot.WHERE); break;
                case WHO: high(slots, priority, QuerySlot.WHO); break;
                case COMPARISON: high(slots, priority, QuerySlot.COMPARISON); medium(slots, priority, QuerySlot.QUANTITY); break;
                case PURPOSE: high(slots, priority, QuerySlot.PURPOSE); medium(slots, priority, QuerySlot.HOW); break;
                case EVIDENCE: high(slots, priority, QuerySlot.EVIDENCE); high(slots, priority, QuerySlot.CLAIM); break;
                case PROBLEM: high(slots, priority, QuerySlot.PROBLEM); medium(slots, priority, QuerySlot.WHY); break;
                case SOLUTION: high(slots, priority, QuerySlot.SOLUTION); high(slots, priority, QuerySlot.HOW); break;
                case TOPIC:
                default: high(slots, priority, QuerySlot.WHAT); break;
            }
        }

        if (frame != null) {
            for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
                switch (axis) {
                    case CAUSE: high(slots, priority, QuerySlot.WHY); break;
                    case EFFECT: high(slots, priority, QuerySlot.EFFECT); break;
                    case MECHANISM: high(slots, priority, QuerySlot.HOW); break;
                    case CONDITION: high(slots, priority, QuerySlot.CONDITION); break;
                    case PURPOSE: high(slots, priority, QuerySlot.PURPOSE); break;
                    case COMPARISON: high(slots, priority, QuerySlot.COMPARISON); break;
                    case QUANTITY: high(slots, priority, QuerySlot.QUANTITY); break;
                    case LOCATION: high(slots, priority, QuerySlot.WHERE); break;
                    case TIME: high(slots, priority, QuerySlot.WHEN); break;
                    case POPULATION:
                    case TARGET: high(slots, priority, QuerySlot.TARGET); medium(slots, priority, QuerySlot.WHO); break;
                    case DOMAIN: high(slots, priority, QuerySlot.DOMAIN); break;
                    case EVIDENCE: high(slots, priority, QuerySlot.EVIDENCE); break;
                    case PROBLEM: high(slots, priority, QuerySlot.PROBLEM); break;
                    case SOLUTION: high(slots, priority, QuerySlot.SOLUTION); break;
                    case RISK: high(slots, priority, QuerySlot.RISK); break;
                    default: break;
                }
            }
        }

        if (function != null) {
            switch (function) {
                case DEFINITION: high(slots, priority, QuerySlot.WHAT); break;
                case DESCRIPTION: medium(slots, priority, QuerySlot.WHAT); medium(slots, priority, QuerySlot.WHICH); medium(slots, priority, QuerySlot.HOW); break;
                case EXPLANATION: high(slots, priority, QuerySlot.HOW); high(slots, priority, QuerySlot.WHY); break;
                case CAUSE_EFFECT: high(slots, priority, QuerySlot.WHY); high(slots, priority, QuerySlot.EFFECT); break;
                case PURPOSE: high(slots, priority, QuerySlot.PURPOSE); break;
                case CONDITION: high(slots, priority, QuerySlot.CONDITION); break;
                case EXAMPLE: high(slots, priority, QuerySlot.WHICH); break;
                case ENUMERATION:
                case CLASSIFICATION: high(slots, priority, QuerySlot.WHICH); medium(slots, priority, QuerySlot.QUANTITY); break;
                case COMPARISON:
                case CONTRAST: high(slots, priority, QuerySlot.COMPARISON); break;
                case ARGUMENTATION: high(slots, priority, QuerySlot.CLAIM); high(slots, priority, QuerySlot.EVIDENCE); break;
                case EVIDENCE: high(slots, priority, QuerySlot.EVIDENCE); break;
                case PROBLEM: high(slots, priority, QuerySlot.PROBLEM); break;
                case SOLUTION: high(slots, priority, QuerySlot.SOLUTION); high(slots, priority, QuerySlot.HOW); break;
                case SEQUENCE: high(slots, priority, QuerySlot.HOW); medium(slots, priority, QuerySlot.WHEN); break;
                case CONCLUSION:
                case SUMMARY: high(slots, priority, QuerySlot.EFFECT); medium(slots, priority, QuerySlot.WHAT); break;
                default: break;
            }
        }

        // Every semantic extraction remains evidence-bound.
        medium(slots, priority, QuerySlot.EVIDENCE);
        return new Matrix(slots, priority, intents);
    }

    private static void high(Set<QuerySlot> slots, Map<QuerySlot, Integer> priority, QuerySlot slot) {
        slots.add(slot); priority.put(slot, Math.max(priority.getOrDefault(slot, 0), 100));
    }

    private static void medium(Set<QuerySlot> slots, Map<QuerySlot, Integer> priority, QuerySlot slot) {
        slots.add(slot); priority.put(slot, Math.max(priority.getOrDefault(slot, 0), 60));
    }
}
