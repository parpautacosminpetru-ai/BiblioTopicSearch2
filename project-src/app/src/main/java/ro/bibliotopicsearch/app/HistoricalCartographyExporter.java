package ro.bibliotopicsearch.app;

import java.util.List;

/** Deterministic source-map export: provenance and indices only, never generated historical prose. */
public final class HistoricalCartographyExporter {
    private HistoricalCartographyExporter() {}

    public static String buildSourceMap(IndexCoreDatabase db, String sourceId) {
        if (db == null || sourceId == null || sourceId.trim().isEmpty()) return "";
        IndexCoreDatabase.SourceRecord source = db.source(sourceId);
        IndexCoreDatabase.SourceScope scope = db.sourceScope(sourceId);
        IndexCoreCartographyReader.Stats stats = IndexCoreCartographyReader.stats(db, sourceId);

        StringBuilder out = new StringBuilder();
        out.append("BIBLIOTOPICSEARCH — FIȘĂ DETERMINISTĂ DE CARTOGRAFIERE\n");
        out.append("====================================================\n\n");
        if (source != null) {
            out.append("SURSĂ: ").append(source.displayName()).append('\n');
            if (!source.author.isEmpty()) out.append("AUTOR: ").append(source.author).append('\n');
            if (!source.edition.isEmpty()) out.append("EDIȚIE/VOLUM: ").append(source.edition).append('\n');
            if (!source.isbn.isEmpty()) out.append("ISBN: ").append(source.isbn).append('\n');
            if (!source.locator.isEmpty()) out.append("LOCALIZARE: ").append(source.locator).append('\n');
        }
        out.append("ID SURSĂ: ").append(sourceId).append("\n\n");

        out.append("REGISTRUL EXHAUSTIVITĂȚII\n");
        out.append("-------------------------\n");
        if (scope.defined()) {
            out.append("Domeniu declarat: pag. ").append(scope.pageFrom).append("–").append(scope.pageTo)
                    .append(" (").append(scope.expectedPages()).append(" pagini)\n");
            out.append("Pagini încheiate: ").append(stats.pagesComplete).append('/').append(stats.expectedPages)
                    .append(" (").append(stats.coveragePercent()).append("%)\n");
            out.append("Stare: ").append(stats.exhaustive() ? "EXHAUSTIVĂ / AUDITATĂ" : "ÎN LUCRU").append('\n');
            if (!stats.missingPages.isEmpty()) {
                out.append("Pagini neîncheiate: ").append(compactPages(stats.missingPages)).append('\n');
            }
        } else {
            out.append("Domeniu declarat: NU\n");
            out.append("Stare: exhaustivitatea nu poate fi afirmată fără intervalul sursei.\n");
        }
        out.append("Paragrafe contabilizate: ").append(stats.paragraphUnits).append('\n');
        out.append("Clauze contabilizate: ").append(stats.clauseUnits).append('\n');
        out.append("Unități cu indice: ").append(stats.mappedUnits).append('\n');
        out.append("Unități fără indice: ").append(stats.noIndexUnits).append('\n');
        out.append("Ambigue: ").append(stats.ambiguousUnits).append('\n');
        out.append("Neparse: ").append(stats.unparsedUnits).append('\n');
        out.append("Excepții nerevizuite: ").append(stats.unresolvedUnits).append('\n');
        out.append("Indici distincți: ").append(stats.distinctTopics).append('\n');
        out.append("Încrucișări deterministe: ").append(stats.intersections).append("\n\n");

        out.append("ARII TEMATICE\n");
        out.append("-------------\n");
        List<IndexCoreCartographyReader.CoverageRange> ranges =
                IndexCoreCartographyReader.coverageRanges(db, sourceId);
        if (ranges.isEmpty()) out.append("(niciuna)\n");
        for (IndexCoreCartographyReader.CoverageRange range : ranges) {
            out.append("- ").append(label(range.topicTitle, range.topicPath))
                    .append(" | pag. ").append(range.pageLabel())
                    .append(" | ").append(range.units).append(" unități\n");
        }

        out.append("\nÎNCRUCIȘĂRI DETERMINISTE (×)\n");
        out.append("-----------------------------\n");
        List<IndexCoreCartographyReader.IntersectionSummary> intersections =
                IndexCoreCartographyReader.intersections(db, sourceId, 1000);
        if (intersections.isEmpty()) out.append("(niciuna)\n");
        for (IndexCoreCartographyReader.IntersectionSummary value : intersections) {
            out.append("- ").append(value.leftTitle).append(" × ").append(value.rightTitle)
                    .append(" [").append(value.rule).append(']');
            if (!value.relation.isEmpty() && !"GENERIC".equals(value.relation)) {
                out.append(" | ").append(value.relation);
            }
            out.append(" | ").append(value.occurrences).append(" unități");
            if (value.pageFrom > 0) {
                out.append(" | pag. ").append(value.pageFrom);
                if (value.pageTo > value.pageFrom) out.append('–').append(value.pageTo);
            }
            out.append('\n');
        }

        out.append("\nCO-PREZENȚE FĂRĂ RELAȚIE DEMONSTRATĂ (+)\n");
        out.append("-----------------------------------------\n");
        List<IndexCoreCartographyReader.CoPresenceSummary> co =
                IndexCoreCartographyReader.coPresence(db, sourceId, 1000);
        if (co.isEmpty()) out.append("(niciuna)\n");
        for (IndexCoreCartographyReader.CoPresenceSummary value : co) {
            out.append("- ").append(value.leftTitle).append(" + ").append(value.rightTitle)
                    .append(" | ").append(value.occurrences).append(" unități");
            if (value.pageFrom > 0) {
                out.append(" | pag. ").append(value.pageFrom);
                if (value.pageTo > value.pageFrom) out.append('–').append(value.pageTo);
            }
            out.append('\n');
        }

        out.append("\nPRINCIPIU\n");
        out.append("---------\n");
        out.append("Fișa este o hartă de proveniență, nu o sinteză istorică automată. ")
                .append("× înseamnă relație structural demonstrată; + înseamnă numai co-prezență. ")
                .append("Nicio afirmație istorică nu este generată de aplicație.\n");
        return out.toString();
    }

    private static String label(String title, String path) {
        return title == null || title.trim().isEmpty() ? (path == null ? "" : path) : title;
    }

    private static String compactPages(List<Integer> pages) {
        if (pages == null || pages.isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pages.size()) {
            int start = pages.get(i);
            int end = start;
            int j = i + 1;
            while (j < pages.size() && pages.get(j) == end + 1) {
                end = pages.get(j);
                j++;
            }
            if (out.length() > 0) out.append(", ");
            out.append(start);
            if (end > start) out.append('–').append(end);
            i = j;
        }
        return out.toString();
    }
}
