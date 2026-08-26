package ro.bibliotopicsearch.app;

/** Pure display policy: the Lens never exposes more than three colored layers at once. */
public final class LensDisplayPolicy {
    private LensDisplayPolicy() {}

    public enum Level {
        SOURCE,
        SECTION,
        PARAGRAPH,
        SENTENCE,
        SEGMENT;

        public Level closer() {
            int next = Math.min(values().length - 1, ordinal() + 1);
            return values()[next];
        }

        public Level farther() {
            int previous = Math.max(0, ordinal() - 1);
            return values()[previous];
        }

        public String labelRo() {
            switch (this) {
                case SOURCE: return "SURSĂ";
                case SECTION: return "SECȚIUNE";
                case PARAGRAPH: return "PARAGRAF";
                case SENTENCE: return "PROPOZIȚIE";
                case SEGMENT:
                default: return "SEGMENT";
            }
        }
    }

    public static final class Plan {
        public final boolean target;
        public final boolean subject;
        public final boolean function;
        public final boolean answer;

        Plan(boolean target, boolean subject, boolean function, boolean answer) {
            this.target = target;
            this.subject = subject;
            this.function = function;
            this.answer = answer;
        }

        public int coloredLayers() {
            int n = 0;
            if (target) n++;
            if (subject) n++;
            if (function) n++;
            if (answer) n++;
            return n;
        }
    }

    public static Plan plan(Level level, boolean queryActive) {
        Level safe = level == null ? Level.PARAGRAPH : level;
        if (queryActive) {
            // Search is a filter, not a dashboard: target + subject + direct answer.
            return new Plan(true, safe.ordinal() >= Level.SECTION.ordinal(), false, true);
        }
        switch (safe) {
            case SOURCE:
                return new Plan(false, false, false, false);
            case SECTION:
                return new Plan(false, true, false, false);
            case PARAGRAPH:
                return new Plan(false, true, true, false);
            case SENTENCE:
            case SEGMENT:
            default:
                return new Plan(false, true, true, false);
        }
    }
}
