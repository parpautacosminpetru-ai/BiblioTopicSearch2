package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
            List<String> criteria = criteriaByParagraph.getOrDefault(
                    candidate.paragraphIndex(), Collections.emptyList()
            );
            criteria = LivingIndexOrganizer.withPrimary(criteria, candidate.category());
            out.add(new LivingIndexEngine.Candidate(
                    candidate.surface(),
                    candidate.category(),
                    candidate.confidence(),
                    candidate.paragraphIndex(),
                    candidate.knownId(),
                    candidate.validated(),
                    candidate.contextCode(),
                    criteria
            ));
        }
        return Collections.unmodifiableList(out);
    }
}