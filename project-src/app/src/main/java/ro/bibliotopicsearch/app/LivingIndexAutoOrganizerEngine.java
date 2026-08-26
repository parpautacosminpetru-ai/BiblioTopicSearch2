package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Adds deterministic multi-criteria routing to the existing LivingIndex detector. */
public final class LivingIndexAutoOrganizerEngine {
    private LivingIndexAutoOrganizerEngine() {}

    public static List<LivingIndexEngine.Candidate> detect(
            List<UniversalParagraphDetector.Detection> detections,
            SemanticGraph graph,
            ParagraphCartography.Map cartography,
            LivingIndexStore.State known
    ) {
        List<LivingIndexEngine.Candidate> base = LivingIndexEngine.detect(
                detections, graph, cartography, known
        );
        if (base.isEmpty()) return base;

        Map<Integer, List<String>> criteriaByParagraph = new HashMap<>();
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
                criteriaByParagraph.put(
                        p,
                        LivingIndexOrganizer.criteriaFor(detection, frame, node, propositions)
                );
            }
        }

        List<LivingIndexEngine.Candidate> out = new ArrayList<>(base.size());
        for (LivingIndexEngine.Candidate candidate : base) {
            LinkedHashSet<String> criteria = new LinkedHashSet<>(
                    LivingIndexOrganizer.withPrimary(
                            criteriaByParagraph.getOrDefault(
                                    candidate.paragraphIndex(), Collections.emptyList()
                            ),
                            candidate.category()
                    )
            );
            addCandidateCriteria(criteria, candidate);
            out.add(new LivingIndexEngine.Candidate(
                    candidate.surface(),
                    candidate.category(),
                    candidate.confidence(),
                    candidate.paragraphIndex(),
                    candidate.knownId(),
                    candidate.validated(),
                    candidate.contextCode(),
                    new ArrayList<>(criteria)
            ));
        }
        return Collections.unmodifiableList(out);
    }

    private static void addCandidateCriteria(
            LinkedHashSet<String> criteria,
            LivingIndexEngine.Candidate candidate
    ) {
        LivingIndexStore.Category category = candidate.category();
        if (category == null) category = LivingIndexStore.Category.INBOX;
        switch (category) {
            case PERSON:
                criteria.add("ONTOLOGY=PERSON");
                criteria.add("ROLE=ENTITY");
                break;
            case PLACE:
                criteria.add("ONTOLOGY=PLACE");
                criteria.add("PLACE=INDEX_ENTRY");
                break;
            case ORGANIZATION:
                criteria.add("ONTOLOGY=ORGANIZATION");
                break;
            case EVENT:
                criteria.add("ONTOLOGY=EVENT");
                break;
            case DATE:
                criteria.add("ONTOLOGY=TIME");
                criteria.add("TIME=DATE_OR_YEAR");
                break;
            case PERIOD:
                criteria.add("ONTOLOGY=TIME");
                criteria.add("TIME=PERIOD");
                break;
            case WORK:
                criteria.add("ONTOLOGY=WORK");
                break;
            case LAW:
                criteria.add("ONTOLOGY=RULE_OR_LAW");
                criteria.add("DOMAIN=LAW");
                break;
            case METHOD:
                criteria.add("ONTOLOGY=METHOD");
                criteria.add("ROLE=METHOD");
                break;
            case DOMAIN:
                criteria.add("ONTOLOGY=DOMAIN");
                break;
            case CONCEPT:
                criteria.add("ONTOLOGY=CONCEPT");
                break;
            case TERM:
                criteria.add("ONTOLOGY=TERM");
                break;
            case INBOX:
            default:
                criteria.add("ONTOLOGY=UNRESOLVED");
                criteria.add("ROLE=NEEDS_VALIDATION");
                break;
        }
    }
}