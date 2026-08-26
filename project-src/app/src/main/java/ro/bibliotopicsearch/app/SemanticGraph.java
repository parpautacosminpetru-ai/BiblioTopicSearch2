package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable clause-level semantic representation used by Semantic Engine v2.
 *
 * The graph stays fully offline and evidence-bound: every proposition keeps the
 * exact source span that produced it. Operators are attached to the proposition
 * where they occur instead of being promoted to paragraph-wide properties.
 */
public final class SemanticGraph {

    public enum Relation {
        GENERIC,
        DEFINITION,
        CAUSE,
        EFFECT,
        MECHANISM,
        CONDITION,
        PURPOSE,
        COMPARISON,
        EVIDENCE,
        PROBLEM,
        SOLUTION,
        SEQUENCE,
        ATTRIBUTE
    }

    public enum Operator {
        NEGATION,
        POSSIBILITY,
        OBLIGATION,
        QUANTIFICATION,
        RESTRICTION,
        EXCEPTION,
        ATTRIBUTION
    }

    public enum Slot {
        WHAT,
        WHO,
        WHERE,
        WHEN,
        WHY,
        HOW,
        CONDITION,
        EFFECT,
        PURPOSE,
        EVIDENCE,
        QUANTITY,
        COMPARISON
    }

    public static final class Proposition {
        private final int paragraphIndex;
        private final int sentenceIndex;
        private final int clauseIndex;
        private final String raw;
        private final String subject;
        private final String predicate;
        private final String object;
        private final Relation relation;
        private final Set<Operator> operators;
        private final Map<Slot, String> slots;
        private final double confidence;
        private final boolean inheritedSubject;

        Proposition(
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String raw,
                String subject,
                String predicate,
                String object,
                Relation relation,
                Set<Operator> operators,
                Map<Slot, String> slots,
                double confidence,
                boolean inheritedSubject
        ) {
            this.paragraphIndex = paragraphIndex;
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.raw = safe(raw);
            this.subject = safe(subject);
            this.predicate = safe(predicate);
            this.object = safe(object);
            this.relation = relation == null ? Relation.GENERIC : relation;
            EnumSet<Operator> operatorCopy = operators == null || operators.isEmpty()
                    ? EnumSet.noneOf(Operator.class)
                    : EnumSet.copyOf(operators);
            this.operators = Collections.unmodifiableSet(operatorCopy);
            EnumMap<Slot, String> slotCopy = new EnumMap<>(Slot.class);
            if (slots != null) {
                for (Map.Entry<Slot, String> entry : slots.entrySet()) {
                    String value = safe(entry.getValue());
                    if (!value.isEmpty()) slotCopy.put(entry.getKey(), value);
                }
            }
            this.slots = Collections.unmodifiableMap(slotCopy);
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.inheritedSubject = inheritedSubject;
        }

        public int paragraphIndex() { return paragraphIndex; }
        public int sentenceIndex() { return sentenceIndex; }
        public int clauseIndex() { return clauseIndex; }
        public String raw() { return raw; }
        public String subject() { return subject; }
        public String predicate() { return predicate; }
        public String object() { return object; }
        public Relation relation() { return relation; }
        public Set<Operator> operators() { return operators; }
        public Map<Slot, String> slots() { return slots; }
        public String slot(Slot slot) { return slots.getOrDefault(slot, ""); }
        public double confidence() { return confidence; }
        public boolean inheritedSubject() { return inheritedSubject; }
        public boolean negated() { return operators.contains(Operator.NEGATION); }
        public boolean modal() {
            return operators.contains(Operator.POSSIBILITY)
                    || operators.contains(Operator.OBLIGATION);
        }
    }

    private final List<Proposition> propositions;
    private final String lastSubject;

    SemanticGraph(List<Proposition> propositions, String lastSubject) {
        this.propositions = Collections.unmodifiableList(new ArrayList<>(
                propositions == null ? Collections.emptyList() : propositions
        ));
        this.lastSubject = safe(lastSubject);
    }

    public List<Proposition> propositions() { return propositions; }
    public int size() { return propositions.size(); }
    public boolean isEmpty() { return propositions.isEmpty(); }
    public String lastSubject() { return lastSubject; }

    public List<Proposition> propositionsForParagraph(int paragraphIndex) {
        List<Proposition> out = new ArrayList<>();
        for (Proposition proposition : propositions) {
            if (proposition.paragraphIndex == paragraphIndex) out.add(proposition);
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
