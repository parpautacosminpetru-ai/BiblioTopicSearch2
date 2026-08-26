package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic faceted organizer for the persistent Living Index.
 *
 * It never stores or generates assertions. Each OCR occurrence is annotated only
 * with generic routing/index criteria so the same entry can be retrieved through
 * several orthogonal views at once: ontology, domain, relation, role, time/place,
 * discourse function and cartographic position.
 */
public final class LivingIndexOrganizer {
    private LivingIndexOrganizer() {}

    public enum Dimension {
        PRIMARY,
        ONTOLOGY,
        DOMAIN,
        RELATION,
        ROLE,
        TIME,
        PLACE,
        DISCOURSE,
        CARTOGRAPHY,
        SCOPE
    }

    public static final class Index {
        private final Map<Dimension, Map<String, List<LivingIndexStore.Entry>>> buckets;
        private final int activeDimensions;
        private final int multiCriteriaEntries;

        Index(
                Map<Dimension, Map<String, List<LivingIndexStore.Entry>>> buckets,
                int activeDimensions,
                int multiCriteriaEntries
        ) {
            Map<Dimension, Map<String, List<LivingIndexStore.Entry>>> outer = new LinkedHashMap<>();
            for (Map.Entry<Dimension, Map<String, List<LivingIndexStore.Entry>>> dimension : buckets.entrySet()) {
                Map<String, List<LivingIndexStore.Entry>> inner = new LinkedHashMap<>();
                for (Map.Entry<String, List<LivingIndexStore.Entry>> group : dimension.getValue().entrySet()) {
                    inner.put(group.getKey(), Collections.unmodifiableList(new ArrayList<>(group.getValue())));
                }
                outer.put(dimension.getKey(), Collections.unmodifiableMap(inner));
            }
            this.buckets = Collections.unmodifiableMap(outer);
            this.activeDimensions = Math.max(0, activeDimensions);
            this.multiCriteriaEntries = Math.max(0, multiCriteriaEntries);
        }

        public Map<Dimension, Map<String, List<LivingIndexStore.Entry>>> buckets() { return buckets; }
        public int activeDimensions() { return activeDimensions; }
        public int multiCriteriaEntries() { return multiCriteriaEntries; }
        public Map<String, List<LivingIndexStore.Entry>> groups(Dimension dimension) {
            return buckets.getOrDefault(dimension, Collections.emptyMap());
        }
        public boolean isEmpty() { return buckets.isEmpty(); }
    }

    /**
     * Produce generic criteria for one paragraph. Values deliberately describe the
     * type/routing context, not what the source asserts.
     */
    public static List<String> criteriaFor(
            UniversalParagraphDetector.Detection detection,
            UniversalSubjectFrame.Frame frame,
            ParagraphCartography.Node node,
            List<SemanticGraph.Proposition> propositions
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (detection == null) return Collections.emptyList();

        if (frame != null && frame.type() != null) {
            add(out, Dimension.ONTOLOGY, frame.type().name());
        }
        add(out, Dimension.DISCOURSE, detection.function().name());

        if (node != null) {
            add(out, Dimension.CARTOGRAPHY, "L" + node.depth() + ":" + node.link().name());
            add(out, Dimension.SCOPE, node.depth() == 0 ? "GLOBAL" : "LOCAL_L" + node.depth());
        } else {
            add(out, Dimension.SCOPE, "LOCAL");
        }

        if (frame != null) {
            for (UniversalSubjectFrame.Axis axis : frame.axes().keySet()) {
                switch (axis) {
                    case CAUSE: add(out, Dimension.RELATION, "CAUSE"); break;
                    case EFFECT: add(out, Dimension.RELATION, "EFFECT"); break;
                    case MECHANISM: add(out, Dimension.RELATION, "MECHANISM"); break;
                    case CONDITION: add(out, Dimension.RELATION, "CONDITION"); break;
                    case PURPOSE: add(out, Dimension.RELATION, "PURPOSE"); break;
                    case COMPARISON: add(out, Dimension.RELATION, "COMPARISON"); break;
                    case EVIDENCE: add(out, Dimension.RELATION, "EVIDENCE"); break;
                    case PROBLEM: add(out, Dimension.RELATION, "PROBLEM"); break;
                    case SOLUTION: add(out, Dimension.RELATION, "SOLUTION"); break;
                    case AGENT: add(out, Dimension.ROLE, "AGENT"); break;
                    case PATIENT: add(out, Dimension.ROLE, "PATIENT"); break;
                    case TARGET:
                    case POPULATION: add(out, Dimension.ROLE, "TARGET"); break;
                    case LOCATION: add(out, Dimension.PLACE, "EXPLICIT"); break;
                    case TIME: add(out, Dimension.TIME, "EXPLICIT"); break;
                    case QUANTITY: add(out, Dimension.ROLE, "MEASURED"); break;
                    default: break;
                }
            }
        }

        if (propositions != null) {
            for (SemanticGraph.Proposition proposition : propositions) {
                if (proposition == null) continue;
                switch (proposition.relation()) {
                    case CAUSE: add(out, Dimension.RELATION, "CAUSE"); break;
                    case EFFECT: add(out, Dimension.RELATION, "EFFECT"); break;
                    case MECHANISM: add(out, Dimension.RELATION, "MECHANISM"); break;
                    case CONDITION: add(out, Dimension.RELATION, "CONDITION"); break;
                    case PURPOSE: add(out, Dimension.RELATION, "PURPOSE"); break;
                    case COMPARISON: add(out, Dimension.RELATION, "COMPARISON"); break;
                    case EVIDENCE: add(out, Dimension.RELATION, "EVIDENCE"); break;
                    case PROBLEM: add(out, Dimension.RELATION, "PROBLEM"); break;
                    case SOLUTION: add(out, Dimension.RELATION, "SOLUTION"); break;
                    case SEQUENCE: add(out, Dimension.RELATION, "SEQUENCE"); break;
                    case DEFINITION: add(out, Dimension.RELATION, "DEFINITION"); break;
                    case ATTRIBUTE: add(out, Dimension.RELATION, "ATTRIBUTE"); break;
                    default: break;
                }
                if (!proposition.subject().isEmpty()) add(out, Dimension.ROLE, "HAS_AGENT_OR_SUBJECT");
                if (!proposition.object().isEmpty()) add(out, Dimension.ROLE, "HAS_TARGET_OR_OBJECT");
                if (!proposition.slot(SemanticGraph.Slot.WHEN).isEmpty()) add(out, Dimension.TIME, "EXPLICIT");
                if (!proposition.slot(SemanticGraph.Slot.WHERE).isEmpty()) add(out, Dimension.PLACE, "EXPLICIT");
            }
        }

        addDomains(out, detection.paragraph());
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    /** Add the entry's primary category to the occurrence criteria before persistence. */
    public static List<String> withPrimary(List<String> criteria, LivingIndexStore.Category category) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (criteria != null) out.addAll(criteria);
        add(out, Dimension.PRIMARY, (category == null ? LivingIndexStore.Category.INBOX : category).name());
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    /** Reconstruct all faceted views from the persistent refs; no duplicate database. */
    public static Index build(LivingIndexStore.State state) {
        if (state == null || state.entries().isEmpty()) {
            return new Index(Collections.emptyMap(), 0, 0);
        }
        Map<Dimension, Map<String, List<LivingIndexStore.Entry>>> buckets = new LinkedHashMap<>();
        int multi = 0;

        for (LivingIndexStore.Entry entry : state.entries()) {
            LinkedHashSet<String> criteria = new LinkedHashSet<>();
            criteria.add(label(Dimension.PRIMARY, entry.category().name()));
            for (LivingIndexStore.Ref ref : entry.refs()) criteria.addAll(ref.axes());
            if (criteria.size() >= 3) multi++;

            for (String criterion : criteria) {
                Parsed parsed = parse(criterion);
                if (parsed == null) continue;
                List<LivingIndexStore.Entry> values = buckets
                        .computeIfAbsent(parsed.dimension, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(parsed.value, ignored -> new ArrayList<>());
                if (!values.contains(entry)) values.add(entry);
            }
        }
        return new Index(buckets, buckets.size(), multi);
    }

    public static List<String> criteriaForEntry(LivingIndexStore.Entry entry) {
        if (entry == null) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(label(Dimension.PRIMARY, entry.category().name()));
        for (LivingIndexStore.Ref ref : entry.refs()) out.addAll(ref.axes());
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    private static void addDomains(Set<String> out, String paragraph) {
        String f = " " + fold(paragraph) + " ";
        domain(out, f, "HISTORY", " istorie ", " istoric ", " istorica ", " secol ", " domnie ", " dinastie ", " reforma ", " revolutie ");
        domain(out, f, "RELIGION", " religie ", " religios ", " religioasa ", " biserica ", " teologie ", " protestant ", " catolic ", " calvin ", " luther ", " ortodox ");
        domain(out, f, "POLITICS", " politic ", " politica ", " guvern ", " partid ", " alegeri ", " parlament ", " stat ", " putere ");
        domain(out, f, "ECONOMICS", " economic ", " economica ", " economie ", " financiar ", " piata ", " inflatie ", " pret ", " fiscal ", " monetar ");
        domain(out, f, "LAW", " juridic ", " juridica ", " lege ", " constitutie ", " decret ", " tribunal ", " drept ", " regulament ");
        domain(out, f, "MEDICINE", " medical ", " medicina ", " pacient ", " boala ", " diagnostic ", " tratament ", " clinic ", " simptom ", " terapie ");
        domain(out, f, "MILITARY", " militar ", " armata ", " razboi ", " batalie ", " ofensiva ", " defensiva ", " regiment ", " general ");
        domain(out, f, "SCIENCE", " stiinta ", " stiintific ", " experiment ", " cercetare ", " ipoteza ", " teorie ", " biologic ", " fizic ", " chimic ");
        domain(out, f, "TECHNOLOGY", " tehnologic ", " tehnologie ", " software ", " algoritm ", " calculator ", " digital ", " retea ");
        domain(out, f, "EDUCATION", " educatie ", " educational ", " scoala ", " universitate ", " elev ", " student ", " examen ");
        domain(out, f, "SOCIETY", " social ", " societate ", " populatie ", " comunitate ", " demografic ", " familie ");
        domain(out, f, "CULTURE", " cultura ", " cultural ", " literatura ", " arta ", " filosofie ", " muzica ", " limba ");
        domain(out, f, "GEOGRAPHY", " geografie ", " geografic ", " regiune ", " teritoriu ", " oras ", " tara ", " continent ");
    }

    private static void domain(Set<String> out, String folded, String value, String... cues) {
        for (String cue : cues) {
            if (folded.contains(cue)) {
                add(out, Dimension.DOMAIN, value);
                return;
            }
        }
    }

    private static void add(Set<String> out, Dimension dimension, String value) {
        if (out == null || dimension == null || value == null || value.trim().isEmpty()) return;
        out.add(label(dimension, value.trim()));
    }

    private static String label(Dimension dimension, String value) {
        return dimension.name() + "=" + value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class Parsed {
        final Dimension dimension;
        final String value;
        Parsed(Dimension dimension, String value) { this.dimension = dimension; this.value = value; }
    }

    private static Parsed parse(String criterion) {
        if (criterion == null) return null;
        int split = criterion.indexOf('=');
        if (split <= 0 || split >= criterion.length() - 1) return null;
        try {
            Dimension dimension = Dimension.valueOf(criterion.substring(0, split).trim());
            String value = criterion.substring(split + 1).trim();
            return value.isEmpty() ? null : new Parsed(dimension, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}