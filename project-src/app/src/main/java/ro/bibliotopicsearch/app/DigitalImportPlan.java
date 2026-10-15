package ro.bibliotopicsearch.app;

/** Pure page-range mapping for deterministic digital PDF ingestion. */
public final class DigitalImportPlan {
    public final int pdfPageFrom;
    public final int pdfPageTo;
    public final int printedPageFrom;
    public final boolean defineScope;

    public DigitalImportPlan(
            int pdfPageFrom,
            int pdfPageTo,
            int printedPageFrom,
            boolean defineScope
    ) {
        this.pdfPageFrom = Math.max(1, pdfPageFrom);
        this.pdfPageTo = Math.max(this.pdfPageFrom, pdfPageTo);
        this.printedPageFrom = Math.max(1, printedPageFrom);
        this.defineScope = defineScope;
    }

    public int pageCount() {
        return pdfPageTo - pdfPageFrom + 1;
    }

    public int printedPageForPdfPage(int pdfPageOneBased) {
        return printedPageFrom + (pdfPageOneBased - pdfPageFrom);
    }

    public int printedPageTo() {
        return printedPageForPdfPage(pdfPageTo);
    }

    public boolean validForPdf(int pdfPageCount) {
        return pdfPageCount > 0
                && pdfPageFrom >= 1
                && pdfPageTo >= pdfPageFrom
                && pdfPageTo <= pdfPageCount;
    }
}
