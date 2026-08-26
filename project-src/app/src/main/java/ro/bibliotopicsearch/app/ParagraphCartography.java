package ro.bibliotopicsearch.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic paragraph cartography built only from explicit detector/semantic evidence.
 *
 * The hierarchy has no fixed maximum depth. Each paragraph is attached to the most
 * plausible previous semantic parent and receives a discourse link such as CONTINUES,
 * NARROWS, RETURNS, SUPPORTS or SHIFTS. Low-evidence cases stay shallow instead of
 * inventing a deep hierarchy.
 */
public final class ParagraphCartography {
    private ParagraphCartography() {}

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*");
    private static final int PARENT_LOOKBACK = 18;

    public enum Link {
        ROOT,
        CONTINUES,
        NARROWS,
        BROADENS,
        RETURNS,
        SHIFTS,
        SUPPORTS,
        EXPLAINS,
        EXEMPLIFIES,
        CONTRASTS,
        CONCLUDES,
        TRANSITIONS
    }

    public static final class Node {
        private final int paragraphIndex;
        private final int depth;
        private final int parentParagraphIndex;
        private final String subject;
        private final UniversalDetectionLexicon.Function function;
        private final Link link;
        private final double confidence;
        private final double subjectSimilarity;
        private final int propositionCount;
        private final boolean inheritedSubject;

        Node(
                int paragraphIndex,
                int depth,
                int parentParagraphIndex,
                String subject,
                UniversalDetectionLexicon.Function function,
                Link link,
                double confidence,
                double subjectSimilarity,
                int propositionCount,
                boolean inheritedSubject
        ) {
            this.paragraphIndex = paragraphIndex;
            this.depth = Math.max(0, depth);
            this.parentParagraphIndex = parentParagraphIndex;
            this.subject = subject == null ? "" : subject.trim();
            this.function = function == null ? UniversalDetectionLexicon.Function.UNKNOWN : function;
            this.link = link == null ? Link.ROOT : link;
            this.confidence = clamp01(confidence);
            this.subjectSimilarity = clamp01(subjectSimilarity);
            this.propositionCount = Math.max(0, propositionCount);
            this.inheritedSubject = inheritedSubject;
        }

        public int paragraphIndex() { return paragraphIndex; }
        public int depth() { return depth; }
        public int parentParagraphIndex() { return parentParagraphIndex; }
        public String subject() { return subject; }
        public UniversalDetectionLexicon.Function function() { return function; }
        public Link link() { return link; }
        public double confidence() { return confidence; }
        public double subjectSimilarity() { return subjectSimilarity; }
        public int propositionCount() { return propositionCount; }
        public boolean inheritedSubject() { return inheritedSubject; }

        public String compactLabel() {
            return "P" + (paragraphIndex + 1) + " L" + depth + " " + shortLink(link);
        }
    }

    public static final class Map {
        private final List<Node> nodes;
        private final String globalSubject;
        private final int maxDepth;

        Map(List<Node> nodes, String globalSubject) {
            List<Node> copy = new ArrayList<>(nodes == null ? Collections.emptyList() : nodes);
            this.nodes = Collections.unmodifiableList(copy);
            this.globalSubject = globalSubject == null ? "" : globalSubject.trim();
            int max = 0;
            for (Node node : copy) max = Math.max(max, node.depth());
            this.maxDepth = max;
        }

        public List<Node> nodes() { return nodes; }
        public String globalSubject() { return globalSubject; }
        public int maxDepth() { return maxDepth; }
        public boolean isEmpty() { return nodes.isEmpty(); }

        public Node nodeForParagraph(int paragraphIndex) {
            for (Node node : nodes) if (node.paragraphIndex == paragraphIndex) return node;
            return null;
        }
    }

    private static final class ParentChoice {
        int nodeIndex = -1;
        double similarity = 0.0;
        boolean exactOrInherited = false;
        boolean currentMoreSpecific = false;
        boolean currentBroader = false;
    }

    public static Map build(List<UniversalParagraphDetector.Detection> detections) {
        SemanticGraph graph = SemanticGraphBuilder.build(detections);
        return build(detections, graph);
    }

    public static Map build(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph
    ) {
        if (detections == null || detections.isEmpty()) {
            return new Map(Collections.emptyList(), "");
        }

        List<Node> nodes = new ArrayList<>();
        List<Set<String>> subjectTerms = new ArrayList<>();

        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection == null) continue;
            int paragraphIndex = detection.paragraphIndex();
            String subject = safeSubject(detection);
            Set<String> currentTerms = terms(subject);
            if (currentTerms.isEmpty()) currentTerms = terms(detection.paragraph());

            boolean inherited = paragraphHasInheritedSubject(graph, paragraphIndex);
            int propositionCount = graph == null
                    ? 0
                    : graph.propositionsForParagraph(paragraphIndex).size();

            if (nodes.isEmpty()) {
                nodes.add(new Node(
                        paragraphIndex, 0, -1, subject, detection.function(), Link.ROOT,
                        baseConfidence(detection, 1.0, inherited), 1.0,
                        propositionCount, inherited
                ));
                subjectTerms.add(currentTerms);
                continue;
            }

            ParentChoice parent = chooseParent(nodes, subjectTerms, currentTerms, inherited);
            Node previous = nodes.get(nodes.size() - 1);
            Link functional = functionalLink(detection.function());

            int parentIndex;
            int depth;
            Link link;
            double similarity = parent.similarity;

            if (functional != null && functional != Link.TRANSITIONS) {
                parentIndex = parent.nodeIndex >= 0 ? parent.nodeIndex : nodes.size() - 1;
                Node parentNode = nodes.get(parentIndex);
                link = functional;
                depth = Math.max(parentNode.depth() + supportDepthDelta(functional), parentNode.depth());
            } else if (inherited || parent.exactOrInherited || similarity >= 0.72) {
                parentIndex = parent.nodeIndex >= 0 ? parent.nodeIndex : nodes.size() - 1;
                Node parentNode = nodes.get(parentIndex);
                if (parentIndex == nodes.size() - 1) {
                    link = Link.CONTINUES;
                } else {
                    link = Link.RETURNS;
                }
                depth = parentNode.depth();
            } else if (parent.nodeIndex >= 0 && parent.currentMoreSpecific && similarity >= 0.34) {
                parentIndex = parent.nodeIndex;
                Node parentNode = nodes.get(parentIndex);
                link = Link.NARROWS;
                depth = parentNode.depth() + 1;
            } else if (parent.nodeIndex >= 0 && parent.currentBroader && similarity >= 0.34) {
                parentIndex = parent.nodeIndex;
                Node parentNode = nodes.get(parentIndex);
                link = Link.BROADENS;
                depth = Math.max(0, parentNode.depth() - 1);
            } else if (functional == Link.TRANSITIONS) {
                parentIndex = nodes.size() - 1;
                link = Link.TRANSITIONS;
                depth = Math.max(0, previous.depth());
            } else if (similarity >= 0.30 && parent.nodeIndex >= 0) {
                parentIndex = parent.nodeIndex;
                Node parentNode = nodes.get(parentIndex);
                link = parentIndex == nodes.size() - 1 ? Link.CONTINUES : Link.RETURNS;
                depth = parentNode.depth();
            } else {
                parentIndex = -1;
                link = Link.SHIFTS;
                depth = 0;
            }

            double confidence = baseConfidence(detection, similarity, inherited);
            if (link == Link.SHIFTS && similarity < 0.18) confidence = Math.max(confidence, 0.62);
            if (functional != null && functional != Link.TRANSITIONS) confidence = Math.max(confidence, 0.66);

            nodes.add(new Node(
                    paragraphIndex,
                    depth,
                    parentIndex < 0 ? -1 : nodes.get(parentIndex).paragraphIndex(),
                    subject,
                    detection.function(),
                    link,
                    confidence,
                    similarity,
                    propositionCount,
                    inherited
            ));
            subjectTerms.add(currentTerms);
        }

        return new Map(nodes, chooseGlobalSubject(nodes));
    }

    private static ParentChoice chooseParent(
            List<Node> nodes,
            List<Set<String>> priorTerms,
            Set<String> current,
            boolean inherited
    ) {
        ParentChoice best = new ParentChoice();
        int start = Math.max(0, nodes.size() - PARENT_LOOKBACK);

        for (int i = nodes.size() - 1; i >= start; i--) {
            Set<String> previous = priorTerms.get(i);
            double similarity = semanticOverlap(current, previous);
            boolean currentContains = containsConceptSet(current, previous);
            boolean previousContains = containsConceptSet(previous, current);

            double recency = 1.0 - Math.min(0.18, (nodes.size() - 1 - i) * 0.015);
            double score = similarity * recency;
            if (inherited && i == nodes.size() - 1) score += 0.42;
            if (currentContains && current.size() > previous.size()) score += 0.10;
            if (previousContains && previous.size() > current.size()) score += 0.07;

            if (score > best.similarity) {
                best.nodeIndex = i;
                best.similarity = Math.min(1.0, score);
                best.exactOrInherited = inherited && i == nodes.size() - 1
                        || similarity >= 0.88;
                best.currentMoreSpecific = currentContains && current.size() > previous.size();
                best.currentBroader = previousContains && previous.size() > current.size();
            }
        }
        return best;
    }

    private static Link functionalLink(UniversalDetectionLexicon.Function function) {
        if (function == null) return null;
        switch (function) {
            case EVIDENCE:
            case ARGUMENTATION:
                return Link.SUPPORTS;
            case EXPLANATION:
            case CAUSE_EFFECT:
            case DESCRIPTION:
            case CLASSIFICATION:
            case ENUMERATION:
            case SEQUENCE:
                return Link.EXPLAINS;
            case EXAMPLE:
                return Link.EXEMPLIFIES;
            case CONTRAST:
            case COMPARISON:
                return Link.CONTRASTS;
            case CONCLUSION:
            case SUMMARY:
                return Link.CONCLUDES;
            case TRANSITION:
            case INTRODUCTION:
                return Link.TRANSITIONS;
            default:
                return null;
        }
    }

    private static int supportDepthDelta(Link link) {
        switch (link) {
            case SUPPORTS:
            case EXPLAINS:
            case EXEMPLIFIES:
                return 1;
            default:
                return 0;
        }
    }

    private static boolean paragraphHasInheritedSubject(SemanticGraph graph, int paragraphIndex) {
        if (graph == null) return false;
        for (SemanticGraph.Proposition proposition : graph.propositionsForParagraph(paragraphIndex)) {
            if (proposition.inheritedSubject()) return true;
        }
        return false;
    }

    private static double baseConfidence(
            UniversalParagraphDetector.Detection detection,
            double similarity,
            boolean inherited
    ) {
        double score = 0.34
                + detection.subjectConfidence() * 0.28
                + detection.functionConfidence() * 0.18
                + Math.min(1.0, similarity) * 0.20;
        if (inherited) score += 0.06;
        return clamp01(score);
    }

    private static String chooseGlobalSubject(List<Node> nodes) {
        if (nodes.isEmpty()) return "";
        String best = nodes.get(0).subject();
        double bestScore = -1.0;
        for (int i = 0; i < nodes.size(); i++) {
            Node candidate = nodes.get(i);
            if (candidate.subject().isEmpty()) continue;
            Set<String> a = terms(candidate.subject());
            double coverage = 0.0;
            for (Node other : nodes) {
                coverage += semanticOverlap(a, terms(other.subject()));
            }
            double depthPenalty = candidate.depth() * 0.08;
            double score = coverage + candidate.confidence() - depthPenalty;
            if (score > bestScore) {
                bestScore = score;
                best = candidate.subject();
            }
        }
        return best;
    }

    private static String safeSubject(UniversalParagraphDetector.Detection detection) {
        if (detection == null) return "";
        String value = detection.subject();
        if (value != null && !value.trim().isEmpty()) return value.trim();
        SemanticGraph graph = SemanticGraphBuilder.build(detection);
        for (SemanticGraph.Proposition proposition : graph.propositions()) {
            if (!proposition.subject().isEmpty()) return proposition.subject();
        }
        return "";
    }

    private static Set<String> terms(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(fold(value));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 || isStop(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static boolean isStop(String token) {
        return token.equals("acest") || token.equals("aceasta") || token.equals("acesta")
                || token.equals("proces") || token.equals("procesul")
                || token.equals("fenomen") || token.equals("fenomenul")
                || token.equals("este") || token.equals("sunt")
                || token.equals("care") || token.equals("pentru") || token.equals("prin")
                || token.equals("dintre") || token.equals("despre") || token.equals("aceasta");
    }

    private static double semanticOverlap(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        int matchedA = 0;
        for (String x : a) if (containsConcept(b, x)) matchedA++;
        int matchedB = 0;
        for (String y : b) if (containsConcept(a, y)) matchedB++;
        double recallA = matchedA / (double) a.size();
        double recallB = matchedB / (double) b.size();
        return (recallA + recallB) / 2.0;
    }

    private static boolean containsConceptSet(Set<String> superset, Set<String> subset) {
        if (superset == null || subset == null || subset.isEmpty()) return false;
        for (String item : subset) if (!containsConcept(superset, item)) return false;
        return true;
    }

    private static boolean containsConcept(Set<String> values, String target) {
        for (String value : values) if (conceptMatch(value, target)) return true;
        return false;
    }

    private static boolean conceptMatch(String a, String b) {
        String x = fold(a);
        String y = fold(b);
        if (x.equals(y)) return true;
        int min = Math.min(x.length(), y.length());
        if (min < 5) return false;
        int prefix = min >= 8 ? 6 : 5;
        return x.regionMatches(0, y, 0, prefix);
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String shortLink(Link link) {
        switch (link) {
            case CONTINUES: return "CONT";
            case NARROWS: return "NAR";
            case BROADENS: return "BRD";
            case RETURNS: return "RET";
            case SHIFTS: return "SHIFT";
            case SUPPORTS: return "SUP";
            case EXPLAINS: return "EXP";
            case EXEMPLIFIES: return "EXM";
            case CONTRASTS: return "CTR";
            case CONCLUDES: return "CONC";
            case TRANSITIONS: return "TR";
            case ROOT:
            default: return "ROOT";
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
