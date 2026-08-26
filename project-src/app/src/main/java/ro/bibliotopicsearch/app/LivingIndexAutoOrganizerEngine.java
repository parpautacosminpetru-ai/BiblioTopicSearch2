package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds deterministic Romanian multi-criteria routing to the LivingIndex detector. */
public final class LivingIndexAutoOrganizerEngine {
    private LivingIndexAutoOrganizerEngine() {}

    public static List<LivingIndexEngine.Candidate> detect(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ParagraphCartography.Map cartography,
            LivingIndexStore.State known
    ) {
        List<LivingIndexEngine.Candidate> base = new ArrayList<>(LivingIndexEngine.detect(
                detections, graph, cartography, known
        ));

        Map<Integer, List<String>> criteriaByParagraph = new HashMap<>();
        Map<Integer, String> contextByParagraph = new HashMap<>();
        if (detections != null) {
            for (UniversalParagraphDetector.Detection detection : detections) {
                if (detection == null) continue;
                int p = detection.paragraphIndex();
                List<SemanticGraph.Proposition> propositions = graph == null
                        ? Collections.emptyList()
                        : graph.propositionsForParagraph(p);
                UniversalSubjectFrame.Frame frame = UniversalSubjectFrame.from(detection, propositions);
                ParagraphCartography.Node node = cartography == null
                        ? null : cartography.nodeForParagraph(p);

                LinkedHashSet<String> criteria = new LinkedHashSet<>(
                        LivingIndexOrganizer.criteriaFor(detection, frame, node, propositions)
                );
                criteria.addAll(RomanianDomainLexicon.facetsFor(detection.paragraph()));
                criteriaByParagraph.put(p, new ArrayList<>(criteria));
                contextByParagraph.put(p, contextFor(detection, node));
            }
        }

        // A validated entry must survive Romanian inflection. Exact matching in the
        // base engine keeps priority; this supplement only fires when the same stable
        // entry was not already found in that paragraph.
        if (known != null && detections != null) {
            Set<String> alreadyKnown = new HashSet<>();
            for (LivingIndexEngine.Candidate candidate : base) {
                if (candidate != null && !candidate.knownId().isEmpty()) {
                    alreadyKnown.add(candidate.paragraphIndex() + "|" + candidate.knownId());
                }
            }
            for (UniversalParagraphDetector.Detection detection : detections) {
                if (detection == null) continue;
                for (LivingIndexStore.Entry entry : known.validated()) {
                    String stableKey = detection.paragraphIndex() + "|" + entry.id();
                    if (alreadyKnown.contains(stableKey)) continue;
                    String surface = "";
                    for (String alias : entry.aliases()) {
                        surface = RomanianFamilyMatcher.findSurface(detection.paragraph(), alias);
                        if (!surface.isEmpty()) break;
                    }
                    if (surface.isEmpty()) continue;
                    base.add(new LivingIndexEngine.Candidate(
                            surface,
                            entry.category(),
                            0.93,
                            detection.paragraphIndex(),
                            entry.id(),
                            true,
                            contextByParagraph.getOrDefault(detection.paragraphIndex(), "F:" + detection.function().name()),
                            criteriaByParagraph.getOrDefault(detection.paragraphIndex(), Collections.emptyList())
                    ));
                    alreadyKnown.add(stableKey);
                }
            }
        }

        if (base.isEmpty()) return Collections.emptyList();
        List<LivingIndexEngine.Candidate> out = new ArrayList<>(base.size());
        for (LivingIndexEngine.Candidate candidate : base) {
            LinkedHashSet<String> criteria = new LinkedHashSet<>(
                    LivingIndexOrganizer.withPrimary(
                            criteriaByParagraph.getOrDefault(candidate.paragraphIndex(), Collections.emptyList()),
                            candidate.category()
                    )
            );
            addCandidateCriteria(criteria, candidate);
            RomanianLanguagePack.Analysis lexical = RomanianLanguagePack.analyze(candidate.surface());
            for (RomanianLanguagePack.PartOfSpeech pos : lexical.possiblePartsOfSpeech) {
                if (pos != RomanianLanguagePack.PartOfSpeech.UNKNOWN) criteria.add("RO_POS=" + pos.name());
            }
            if (!lexical.familyKey.isEmpty()) criteria.add("RO_FAMILY=" + lexical.familyKey.toUpperCase(java.util.Locale.ROOT));

            out.add(new LivingIndexEngine.Candidate(
                    candidate.surface(), candidate.category(), candidate.confidence(),
                    candidate.paragraphIndex(), candidate.knownId(), candidate.validated(),
                    candidate.contextCode(), new ArrayList<>(criteria)
            ));
        }
        return Collections.unmodifiableList(out);
    }

    private static String contextFor(
            UniversalParagraphDetector.Detection detection,
            ParagraphCartography.Node node
    ) {
        StringBuilder out = new StringBuilder("F:").append(detection.function().name()).append("|RO:V8");
        if (node != null) out.append("|L:").append(node.depth()).append(':').append(node.link().name());
        return out.toString();
    }

    private static void addCandidateCriteria(
            LinkedHashSet<String> criteria,
            LivingIndexEngine.Candidate candidate
    ) {
        LivingIndexStore.Category category = candidate.category();
        if (category == null) category = LivingIndexStore.Category.INBOX;
        switch (category) {
            case PERSON:
                criteria.add("ONTOLOGY=PERSON"); criteria.add("ROLE=ENTITY"); break;
            case PLACE:
                criteria.add("ONTOLOGY=PLACE"); criteria.add("PLACE=INDEX_ENTRY"); break;
            case ORGANIZATION:
                criteria.add("ONTOLOGY=ORGANIZATION"); break;
            case EVENT:
                criteria.add("ONTOLOGY=EVENT"); break;
            case DATE:
                criteria.add("ONTOLOGY=TIME"); criteria.add("TIME=DATE_OR_YEAR"); break;
            case PERIOD:
                criteria.add("ONTOLOGY=TIME"); criteria.add("TIME=PERIOD"); break;
            case WORK:
                criteria.add("ONTOLOGY=WORK"); break;
            case LAW:
                criteria.add("ONTOLOGY=RULE_OR_LAW"); criteria.add("DOMAIN=LAW"); break;
            case METHOD:
                criteria.add("ONTOLOGY=METHOD"); criteria.add("ROLE=METHOD"); break;
            case DOMAIN:
                criteria.add("ONTOLOGY=DOMAIN"); break;
            case CONCEPT:
                criteria.add("ONTOLOGY=CONCEPT"); break;
            case TERM:
                criteria.add("ONTOLOGY=TERM"); break;
            case INBOX:
            default:
                criteria.add("ONTOLOGY=UNRESOLVED"); criteria.add("ROLE=NEEDS_VALIDATION"); break;
        }
    }
}
