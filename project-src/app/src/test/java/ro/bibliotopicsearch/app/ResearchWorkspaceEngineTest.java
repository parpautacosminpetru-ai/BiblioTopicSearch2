package ro.bibliotopicsearch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;

public final class ResearchWorkspaceEngineTest {

    @Test
    public void sameSubjectAcrossSessionsBecomesOneDossierWithProvenance() {
        OnePassSemanticOrganizer.Snapshot a = snapshot(
                100L,
                "efectele inflației",
                claim("Inflația reduce puterea de cumpărare.", "puterea de cumpărare", false)
        );
        OnePassSemanticOrganizer.Snapshot b = snapshot(
                200L,
                "efectele inflației",
                claim("Inflația reduce puterea de cumpărare.", "puterea de cumpărare", false)
        );

        ResearchWorkspaceStore.State state = new ResearchWorkspaceStore.State(
                "Inflație", "", Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap()
        );
        ResearchWorkspaceEngine.Workspace workspace = ResearchWorkspaceEngine.build(Arrays.asList(a, b), state);

        assertEquals(2, workspace.sourceCount());
        assertEquals(1, workspace.groups().size());
        ResearchWorkspaceEngine.DossierGroup group = workspace.groups().get(0);
        assertEquals(2, group.sourceCount());
        assertEquals(2, group.evidence().size());
        assertTrue(group.convergenceCount() >= 1);
        assertFalse(group.requiredSlots().isEmpty());
    }

    @Test
    public void oppositePolarityIsOnlyFlaggedAsTensionCandidate() {
        OnePassSemanticOrganizer.Snapshot a = snapshot(
                300L,
                "efectele inflației",
                claim("Inflația reduce consumul.", "consumul", false)
        );
        OnePassSemanticOrganizer.Snapshot b = snapshot(
                400L,
                "efectele inflației",
                claim("Inflația nu reduce consumul.", "consumul", true)
        );
        ResearchWorkspaceStore.State state = new ResearchWorkspaceStore.State(
                "Inflație", "Sinteza mea", Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap()
        );

        ResearchWorkspaceEngine.Workspace workspace = ResearchWorkspaceEngine.build(Arrays.asList(a, b), state);
        assertEquals("Sinteza mea", workspace.state().synthesisDraft());
        assertTrue(workspace.groups().get(0).tensionCandidateCount() >= 1);
    }

    private static OnePassSemanticOrganizer.Claim claim(String raw, String object, boolean negated) {
        EnumSet<SemanticGraph.Operator> operators = EnumSet.noneOf(SemanticGraph.Operator.class);
        if (negated) operators.add(SemanticGraph.Operator.NEGATION);
        EnumMap<SemanticGraph.Slot, String> slots = new EnumMap<>(SemanticGraph.Slot.class);
        slots.put(SemanticGraph.Slot.EFFECT, object);
        return new OnePassSemanticOrganizer.Claim(
                raw,
                "inflația",
                "reduce",
                object,
                SemanticGraph.Relation.EFFECT,
                operators,
                slots,
                0.91
        );
    }

    private static OnePassSemanticOrganizer.Snapshot snapshot(
            long id,
            String subject,
            OnePassSemanticOrganizer.Claim claim
    ) {
        OnePassSemanticOrganizer.Paragraph paragraph = new OnePassSemanticOrganizer.Paragraph(
                0,
                0,
                -1,
                ParagraphCartography.Link.ROOT,
                claim.raw(),
                subject,
                UniversalDetectionLexicon.Function.CAUSE_EFFECT,
                UniversalDetectionLexicon.Function.UNKNOWN,
                0.92,
                0.88,
                1,
                claim.raw(),
                0.84,
                ResearchSemanticEngine.Intent.EFFECT,
                SemanticGraph.Relation.EFFECT,
                Collections.singletonList(claim)
        );
        return new OnePassSemanticOrganizer.Snapshot(
                id,
                id + 10,
                1,
                0,
                "efectele inflației",
                subject,
                0,
                1,
                claim.raw(),
                0.84,
                Collections.singletonList(paragraph)
        );
    }
}
