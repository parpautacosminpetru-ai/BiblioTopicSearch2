package ro.bibliotopicsearch.app;

import android.content.Context;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Reproducible, human-authored historical research dossier. */
public final class HistorianResearchExporter {
    private HistorianResearchExporter() {}

    public static String build(Context context, IndexCoreDatabase db) {
        if (context == null || db == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("BIBLIOTOPICSEARCH — DOSAR REPRODUCTIBIL DE CERCETARE\n");
        out.append("===================================================\n");
        out.append("Generat: ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date()))
                .append("\n");
        out.append("Principiu: afirmațiile și evaluările critice aparțin istoricului; ")
                .append("aplicația păstrează organizarea, relațiile declarate și proveniența.\n\n");

        out.append("HARTA TEMATICĂ ACTIVĂ\n");
        out.append("---------------------\n");
        String rawMap = TopicMapStore.getRawMap(context);
        out.append(rawMap == null || rawMap.trim().isEmpty() ? "(goală)" : rawMap.trim())
                .append("\n\n");

        out.append("SURSE ȘI CRITICA SURSEI\n");
        out.append("------------------------\n");
        List<IndexCoreDatabase.SourceRecord> sources = db.listSources();
        if (sources.isEmpty()) out.append("(nicio sursă)\n");
        for (IndexCoreDatabase.SourceRecord source : sources) {
            HistorianWorkbenchStore.SourceCriticism criticism =
                    HistorianWorkbenchStore.sourceCriticism(db, source.id);
            IndexCoreCartographyReader.Stats stats =
                    IndexCoreCartographyReader.stats(db, source.id);
            IndexCoreDatabase.SourceScope scope = db.sourceScope(source.id);

            out.append("\n[").append(source.displayName()).append("]\n");
            out.append("ID: ").append(source.id).append('\n');
            if (!source.author.isEmpty()) out.append("Autor: ").append(source.author).append('\n');
            if (!source.edition.isEmpty()) out.append("Ediție/volum: ").append(source.edition).append('\n');
            if (!source.isbn.isEmpty()) out.append("ISBN: ").append(source.isbn).append('\n');
            if (!source.locator.isEmpty()) out.append("Localizare: ").append(source.locator).append('\n');
            if (scope.defined()) {
                out.append("Domeniu: pag. ").append(scope.pageFrom).append('–').append(scope.pageTo)
                        .append(" | încheiate ").append(stats.pagesComplete)
                        .append('/').append(stats.expectedPages)
                        .append(" | ").append(stats.coveragePercent()).append("%")
                        .append(stats.exhaustive() ? " | EXHAUSTIVĂ/AUDITATĂ" : "")
                        .append('\n');
            }
            out.append("Unități: ").append(stats.paragraphUnits).append(" paragrafe | ")
                    .append(stats.clauseUnits).append(" clauze | ")
                    .append(stats.intersections).append(" × | ")
                    .append(stats.unresolvedUnits).append(" excepții nerevizuite\n");

            if (!criticism.sourceClass.isEmpty()) out.append("Tip/statut: ").append(criticism.sourceClass).append('\n');
            if (!criticism.genre.isEmpty()) out.append("Gen: ").append(criticism.genre).append('\n');
            if (!criticism.dating.isEmpty()) out.append("Datare: ").append(criticism.dating).append('\n');
            if (!criticism.origin.isEmpty()) out.append("Origine: ").append(criticism.origin).append('\n');
            if (!criticism.language.isEmpty()) out.append("Limbă: ").append(criticism.language).append('\n');
            if (!criticism.provenance.isEmpty()) out.append("Proveniență: ").append(criticism.provenance).append('\n');
            if (!criticism.transmission.isEmpty()) out.append("Transmitere: ").append(criticism.transmission).append('\n');
            if (!criticism.audience.isEmpty()) out.append("Public/destinatar: ").append(criticism.audience).append('\n');
            if (!criticism.proximity.isEmpty()) out.append("Proximitate: ").append(criticism.proximity).append('\n');
            if (!criticism.perspective.isEmpty()) out.append("Perspectivă/interese: ").append(criticism.perspective).append('\n');
            if (!criticism.authenticity.isEmpty()) out.append("Autenticitate/integritate: ").append(criticism.authenticity).append('\n');
            if (!criticism.editionNotes.isEmpty()) out.append("Ediție/aparat critic: ").append(criticism.editionNotes).append('\n');
            if (!criticism.researchNotes.isEmpty()) out.append("Note metodologice: ").append(criticism.researchNotes).append('\n');
        }

        out.append("\n\nRELAȚII ÎNTRE SURSE / EDIȚII / MARTORI\n");
        out.append("--------------------------------------\n");
        List<HistorianSourceRelationsStore.Relation> sourceRelations =
                HistorianSourceRelationsStore.list(db);
        if (sourceRelations.isEmpty()) out.append("(nicio relație declarată)\n");
        for (HistorianSourceRelationsStore.Relation relation : sourceRelations) {
            out.append("- ")
                    .append(relation.leftTitle.isEmpty() ? relation.leftSourceId : relation.leftTitle)
                    .append(" → ").append(relation.type.name()).append(" → ")
                    .append(relation.rightTitle.isEmpty() ? relation.rightSourceId : relation.rightTitle);
            if (!relation.note.isEmpty()) out.append(" | ").append(relation.note);
            out.append('\n');
        }

        out.append("\n\nAFIRMAȚII ȘI REȚEAUA DE DOVEZI\n");
        out.append("--------------------------------\n");
        List<HistorianWorkbenchStore.Claim> claims = HistorianWorkbenchStore.claims(db);
        if (claims.isEmpty()) out.append("(nicio afirmație formulată de cercetător)\n");
        for (HistorianWorkbenchStore.Claim claim : claims) {
            out.append("\n[").append(claim.status.name()).append("] ")
                    .append(claim.displayTitle()).append('\n');
            out.append("ID: ").append(claim.id).append('\n');
            out.append("Afirmație: ").append(claim.statement).append('\n');
            out.append("Dovezi: ").append(claim.evidenceCount)
                    .append(" | surse: ").append(claim.sourceCount)
                    .append(" | susține: ").append(claim.supports)
                    .append(" | contrazice: ").append(claim.contradicts)
                    .append(" | nuanțează: ").append(claim.nuances)
                    .append(" | exemplifică: ").append(claim.exemplifies)
                    .append(" | contextualizează: ").append(claim.contextualizes)
                    .append('\n');

            for (HistorianWorkbenchStore.Evidence evidence :
                    HistorianWorkbenchStore.evidence(db, claim.id)) {
                out.append("  - ").append(evidence.role.name())
                        .append(" | ")
                        .append(evidence.sourceTitle.isEmpty()
                                ? evidence.sourceId : evidence.sourceTitle);
                if (!evidence.page.isEmpty()) out.append(" | pag. ").append(evidence.page);
                out.append(" | P").append(evidence.paragraphIndex + 1);
                if (evidence.sentenceIndex >= 0) out.append(" S").append(evidence.sentenceIndex + 1);
                if (evidence.clauseIndex >= 0) out.append(" C").append(evidence.clauseIndex + 1);
                if (!evidence.outlineTitle.isEmpty()) out.append(" | ").append(evidence.outlineTitle);
                if (!evidence.function.isEmpty()) out.append(" | F:").append(evidence.function);
                if (!evidence.relation.isEmpty()) out.append(" | R:").append(evidence.relation);
                out.append("\n    UNIT: ").append(evidence.unitId);
                if (!evidence.topics.isEmpty()) out.append("\n    Indici: ").append(evidence.topics);
                if (!evidence.surfaces.isEmpty()) out.append("\n    Ancore: ").append(evidence.surfaces);
                if (!evidence.note.isEmpty()) out.append("\n    Nota cercetătorului: ").append(evidence.note);
                out.append('\n');
            }
        }

        out.append("\n\nJURNAL DE DECIZII METODOLOGICE\n");
        out.append("-------------------------------\n");
        List<HistorianWorkbenchStore.Audit> audit =
                HistorianWorkbenchStore.auditTrail(db, 5000);
        if (audit.isEmpty()) out.append("(gol)\n");
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT
        );
        for (HistorianWorkbenchStore.Audit value : audit) {
            out.append("- ").append(format.format(new Date(value.createdAt)))
                    .append(" | ").append(value.entityType)
                    .append(" | ").append(value.entityId)
                    .append(" | ").append(value.action);
            if (!value.detail.isEmpty()) out.append(" | ").append(value.detail);
            out.append('\n');
        }

        out.append("\n\nNOTĂ DE METODĂ\n");
        out.append("--------------\n");
        out.append("Acest fișier nu este o sinteză generată. Este un registru exportabil al corpusului, ")
                .append("al criticii sursei, al afirmațiilor formulate de cercetător, al dovezilor legate explicit ")
                .append("și al deciziilor metodologice. Orice dovadă rămâne identificată prin unitatea ei persistentă.\n");
        return out.toString();
    }
}
