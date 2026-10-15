package ro.bibliotopicsearch.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Human-owned historical reasoning layer over the immutable cartography ledger.
 *
 * The application never authors a historical claim here. Claims, source criticism
 * and the meaning of an evidence link are explicit decisions of the historian.
 */
public final class HistorianWorkbenchStore {
    private HistorianWorkbenchStore() {}

    public enum ClaimStatus {
        OPEN,
        PROVISIONAL,
        SUPPORTED,
        DISPUTED,
        RETIRED
    }

    public enum EvidenceRole {
        SUPPORTS,
        CONTRADICTS,
        NUANCES,
        EXEMPLIFIES,
        CONTEXTUALIZES
    }

    public static final class SourceCriticism {
        public final String sourceId;
        public final String sourceClass;
        public final String genre;
        public final String dating;
        public final String origin;
        public final String language;
        public final String provenance;
        public final String transmission;
        public final String audience;
        public final String proximity;
        public final String perspective;
        public final String authenticity;
        public final String editionNotes;
        public final String researchNotes;
        public final long updatedAt;

        public SourceCriticism(
                String sourceId,
                String sourceClass,
                String genre,
                String dating,
                String origin,
                String language,
                String provenance,
                String transmission,
                String audience,
                String proximity,
                String perspective,
                String authenticity,
                String editionNotes,
                String researchNotes,
                long updatedAt
        ) {
            this.sourceId = safe(sourceId);
            this.sourceClass = safe(sourceClass);
            this.genre = safe(genre);
            this.dating = safe(dating);
            this.origin = safe(origin);
            this.language = safe(language);
            this.provenance = safe(provenance);
            this.transmission = safe(transmission);
            this.audience = safe(audience);
            this.proximity = safe(proximity);
            this.perspective = safe(perspective);
            this.authenticity = safe(authenticity);
            this.editionNotes = safe(editionNotes);
            this.researchNotes = safe(researchNotes);
            this.updatedAt = Math.max(0L, updatedAt);
        }

        public boolean isEmpty() {
            return sourceClass.isEmpty() && genre.isEmpty() && dating.isEmpty()
                    && origin.isEmpty() && language.isEmpty() && provenance.isEmpty()
                    && transmission.isEmpty() && audience.isEmpty() && proximity.isEmpty()
                    && perspective.isEmpty() && authenticity.isEmpty()
                    && editionNotes.isEmpty() && researchNotes.isEmpty();
        }
    }

    public static final class Claim {
        public final String id;
        public final String title;
        public final String statement;
        public final ClaimStatus status;
        public final long createdAt;
        public final long updatedAt;
        public final int evidenceCount;
        public final int sourceCount;
        public final int supports;
        public final int contradicts;
        public final int nuances;
        public final int exemplifies;
        public final int contextualizes;

        Claim(
                String id,
                String title,
                String statement,
                ClaimStatus status,
                long createdAt,
                long updatedAt,
                int evidenceCount,
                int sourceCount,
                int supports,
                int contradicts,
                int nuances,
                int exemplifies,
                int contextualizes
        ) {
            this.id = safe(id);
            this.title = safe(title);
            this.statement = safe(statement);
            this.status = status == null ? ClaimStatus.OPEN : status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.evidenceCount = Math.max(0, evidenceCount);
            this.sourceCount = Math.max(0, sourceCount);
            this.supports = Math.max(0, supports);
            this.contradicts = Math.max(0, contradicts);
            this.nuances = Math.max(0, nuances);
            this.exemplifies = Math.max(0, exemplifies);
            this.contextualizes = Math.max(0, contextualizes);
        }

        public String displayTitle() {
            return title.isEmpty() ? statement : title;
        }
    }

    public static final class Evidence {
        public final String claimId;
        public final String unitId;
        public final EvidenceRole role;
        public final String note;
        public final long linkedAt;
        public final String sourceId;
        public final String sourceTitle;
        public final String sourceAuthor;
        public final String page;
        public final int paragraphIndex;
        public final int sentenceIndex;
        public final int clauseIndex;
        public final String unitKind;
        public final String outlineTitle;
        public final String function;
        public final String relation;
        public final String topics;
        public final String surfaces;

        Evidence(
                String claimId,
                String unitId,
                EvidenceRole role,
                String note,
                long linkedAt,
                String sourceId,
                String sourceTitle,
                String sourceAuthor,
                String page,
                int paragraphIndex,
                int sentenceIndex,
                int clauseIndex,
                String unitKind,
                String outlineTitle,
                String function,
                String relation,
                String topics,
                String surfaces
        ) {
            this.claimId = safe(claimId);
            this.unitId = safe(unitId);
            this.role = role == null ? EvidenceRole.CONTEXTUALIZES : role;
            this.note = safe(note);
            this.linkedAt = linkedAt;
            this.sourceId = safe(sourceId);
            this.sourceTitle = safe(sourceTitle);
            this.sourceAuthor = safe(sourceAuthor);
            this.page = safe(page);
            this.paragraphIndex = Math.max(0, paragraphIndex);
            this.sentenceIndex = sentenceIndex;
            this.clauseIndex = clauseIndex;
            this.unitKind = safe(unitKind);
            this.outlineTitle = safe(outlineTitle);
            this.function = safe(function);
            this.relation = safe(relation);
            this.topics = safe(topics);
            this.surfaces = safe(surfaces);
        }
    }

    public static final class Audit {
        public final long id;
        public final String entityType;
        public final String entityId;
        public final String action;
        public final String detail;
        public final long createdAt;

        Audit(long id, String entityType, String entityId, String action, String detail, long createdAt) {
            this.id = id;
            this.entityType = safe(entityType);
            this.entityId = safe(entityId);
            this.action = safe(action);
            this.detail = safe(detail);
            this.createdAt = createdAt;
        }
    }

    public static SourceCriticism sourceCriticism(IndexCoreDatabase core, String sourceId) {
        if (core == null || safe(sourceId).isEmpty()) return emptyCriticism(sourceId);
        try (Cursor c = core.getReadableDatabase().query(
                "idx_source_criticism",
                new String[]{
                        "source_id","source_class","genre","dating","origin","language",
                        "provenance","transmission","audience","proximity","perspective",
                        "authenticity","edition_notes","research_notes","updated_at"
                },
                "source_id=?", new String[]{safe(sourceId)}, null, null, null, "1"
        )) {
            if (!c.moveToFirst()) return emptyCriticism(sourceId);
            return new SourceCriticism(
                    c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getString(7),
                    c.getString(8), c.getString(9), c.getString(10), c.getString(11),
                    c.getString(12), c.getString(13), c.getLong(14)
            );
        }
    }

    public static void saveSourceCriticism(IndexCoreDatabase core, SourceCriticism value) {
        if (core == null || value == null || value.sourceId.isEmpty()) return;
        core.ensureSource(value.sourceId);
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("source_id", value.sourceId);
        row.put("source_class", value.sourceClass);
        row.put("genre", value.genre);
        row.put("dating", value.dating);
        row.put("origin", value.origin);
        row.put("language", value.language);
        row.put("provenance", value.provenance);
        row.put("transmission", value.transmission);
        row.put("audience", value.audience);
        row.put("proximity", value.proximity);
        row.put("perspective", value.perspective);
        row.put("authenticity", value.authenticity);
        row.put("edition_notes", value.editionNotes);
        row.put("research_notes", value.researchNotes);
        row.put("updated_at", now);
        core.getWritableDatabase().insertWithOnConflict(
                "idx_source_criticism", null, row, SQLiteDatabase.CONFLICT_REPLACE
        );
        audit(core, "SOURCE", value.sourceId, "CRITICISM_UPDATED", criticismAuditDetail(value));
    }

    public static String createClaim(IndexCoreDatabase core, String title, String statement) {
        if (core == null || safe(statement).isEmpty()) return "";
        String id = "claim-" + UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("claim_id", id);
        row.put("title", safe(title));
        row.put("statement", safe(statement));
        row.put("status", ClaimStatus.OPEN.name());
        row.put("created_at", now);
        row.put("updated_at", now);
        core.getWritableDatabase().insertOrThrow("idx_research_claims", null, row);
        audit(core, "CLAIM", id, "CREATED", compact(statement, 240));
        return id;
    }

    public static boolean updateClaim(
            IndexCoreDatabase core,
            String claimId,
            String title,
            String statement
    ) {
        if (core == null || safe(claimId).isEmpty() || safe(statement).isEmpty()) return false;
        ContentValues row = new ContentValues();
        row.put("title", safe(title));
        row.put("statement", safe(statement));
        row.put("updated_at", System.currentTimeMillis());
        int changed = core.getWritableDatabase().update(
                "idx_research_claims", row, "claim_id=?", new String[]{safe(claimId)}
        );
        if (changed > 0) audit(core, "CLAIM", claimId, "TEXT_UPDATED", compact(statement, 240));
        return changed > 0;
    }

    public static boolean setClaimStatus(
            IndexCoreDatabase core,
            String claimId,
            ClaimStatus status
    ) {
        if (core == null || safe(claimId).isEmpty() || status == null) return false;
        ContentValues row = new ContentValues();
        row.put("status", status.name());
        row.put("updated_at", System.currentTimeMillis());
        int changed = core.getWritableDatabase().update(
                "idx_research_claims", row, "claim_id=?", new String[]{safe(claimId)}
        );
        if (changed > 0) audit(core, "CLAIM", claimId, "STATUS", status.name());
        return changed > 0;
    }

    public static boolean deleteClaim(IndexCoreDatabase core, String claimId) {
        if (core == null || safe(claimId).isEmpty()) return false;
        audit(core, "CLAIM", claimId, "DELETED", "");
        return core.getWritableDatabase().delete(
                "idx_research_claims", "claim_id=?", new String[]{safe(claimId)}
        ) > 0;
    }

    public static List<Claim> claims(IndexCoreDatabase core) {
        if (core == null) return Collections.emptyList();
        String sql = "SELECT c.claim_id,c.title,c.statement,c.status,c.created_at,c.updated_at,"
                + "COUNT(e.unit_id),COUNT(DISTINCT u.source_id),"
                + "SUM(CASE WHEN e.role='SUPPORTS' THEN 1 ELSE 0 END),"
                + "SUM(CASE WHEN e.role='CONTRADICTS' THEN 1 ELSE 0 END),"
                + "SUM(CASE WHEN e.role='NUANCES' THEN 1 ELSE 0 END),"
                + "SUM(CASE WHEN e.role='EXEMPLIFIES' THEN 1 ELSE 0 END),"
                + "SUM(CASE WHEN e.role='CONTEXTUALIZES' THEN 1 ELSE 0 END) "
                + "FROM idx_research_claims c "
                + "LEFT JOIN idx_claim_evidence e ON e.claim_id=c.claim_id "
                + "LEFT JOIN idx_semantic_units u ON u.unit_id=e.unit_id "
                + "GROUP BY c.claim_id,c.title,c.statement,c.status,c.created_at,c.updated_at "
                + "ORDER BY c.updated_at DESC,c.created_at DESC";
        List<Claim> out = new ArrayList<>();
        try (Cursor c = core.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new Claim(
                        c.getString(0), c.getString(1), c.getString(2),
                        claimStatus(c.getString(3)), c.getLong(4), c.getLong(5),
                        c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9),
                        c.getInt(10), c.getInt(11), c.getInt(12)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static Claim claim(IndexCoreDatabase core, String claimId) {
        if (core == null || safe(claimId).isEmpty()) return null;
        for (Claim claim : claims(core)) if (claim.id.equals(claimId)) return claim;
        return null;
    }

    public static boolean linkEvidence(
            IndexCoreDatabase core,
            String claimId,
            String unitId,
            EvidenceRole role,
            String note
    ) {
        if (core == null || safe(claimId).isEmpty() || safe(unitId).isEmpty() || role == null) return false;
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("claim_id", safe(claimId));
        row.put("unit_id", safe(unitId));
        row.put("role", role.name());
        row.put("note", safe(note));
        row.put("linked_at", now);
        long id = core.getWritableDatabase().insertWithOnConflict(
                "idx_claim_evidence", null, row, SQLiteDatabase.CONFLICT_REPLACE
        );
        if (id >= 0) {
            touchClaim(core, claimId);
            audit(core, "CLAIM", claimId, "EVIDENCE_" + role.name(), unitId);
            return true;
        }
        return false;
    }

    public static boolean unlinkEvidence(
            IndexCoreDatabase core,
            String claimId,
            String unitId,
            EvidenceRole role
    ) {
        if (core == null || safe(claimId).isEmpty() || safe(unitId).isEmpty() || role == null) return false;
        int changed = core.getWritableDatabase().delete(
                "idx_claim_evidence",
                "claim_id=? AND unit_id=? AND role=?",
                new String[]{safe(claimId), safe(unitId), role.name()}
        );
        if (changed > 0) {
            touchClaim(core, claimId);
            audit(core, "CLAIM", claimId, "EVIDENCE_REMOVED", unitId + "|" + role.name());
        }
        return changed > 0;
    }

    public static List<Evidence> evidence(IndexCoreDatabase core, String claimId) {
        if (core == null || safe(claimId).isEmpty()) return Collections.emptyList();
        String sql = "SELECT e.claim_id,e.unit_id,e.role,e.note,e.linked_at,"
                + "u.source_id,s.title,s.author,u.page,u.paragraph_index,u.sentence_index,u.clause_index,"
                + "u.unit_kind,COALESCE(o.title,''),u.function,u.relation,"
                + "COALESCE(GROUP_CONCAT(DISTINCT t.topic_title),''),"
                + "COALESCE(GROUP_CONCAT(DISTINCT t.surface),'') "
                + "FROM idx_claim_evidence e "
                + "JOIN idx_semantic_units u ON u.unit_id=e.unit_id "
                + "JOIN idx_sources s ON s.source_id=u.source_id "
                + "LEFT JOIN idx_outlines o ON o.outline_id=u.outline_id "
                + "LEFT JOIN idx_unit_topics t ON t.unit_id=u.unit_id "
                + "WHERE e.claim_id=? "
                + "GROUP BY e.claim_id,e.unit_id,e.role,e.note,e.linked_at,u.source_id,s.title,s.author,"
                + "u.page,u.paragraph_index,u.sentence_index,u.clause_index,u.unit_kind,o.title,u.function,u.relation "
                + "ORDER BY CASE e.role "
                + "WHEN 'CONTRADICTS' THEN 0 WHEN 'SUPPORTS' THEN 1 WHEN 'NUANCES' THEN 2 "
                + "WHEN 'EXEMPLIFIES' THEN 3 ELSE 4 END,"
                + "s.title COLLATE NOCASE,"
                + "CASE WHEN u.page GLOB '[0-9]*' THEN CAST(u.page AS INTEGER) ELSE 2147483647 END,"
                + "u.page,u.paragraph_index,u.sentence_index,u.clause_index";
        List<Evidence> out = new ArrayList<>();
        try (Cursor c = core.getReadableDatabase().rawQuery(sql, new String[]{safe(claimId)})) {
            while (c.moveToNext()) {
                out.add(new Evidence(
                        c.getString(0), c.getString(1), evidenceRole(c.getString(2)),
                        c.getString(3), c.getLong(4), c.getString(5), c.getString(6),
                        c.getString(7), c.getString(8), c.getInt(9), c.getInt(10),
                        c.getInt(11), c.getString(12), c.getString(13), c.getString(14),
                        c.getString(15), c.getString(16), c.getString(17)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<Audit> auditTrail(IndexCoreDatabase core, int limit) {
        if (core == null) return Collections.emptyList();
        String sql = "SELECT audit_id,entity_type,entity_id,action,detail,created_at "
                + "FROM idx_research_audit ORDER BY created_at DESC,audit_id DESC LIMIT "
                + Math.max(1, Math.min(10000, limit));
        List<Audit> out = new ArrayList<>();
        try (Cursor c = core.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new Audit(
                        c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getLong(5)
                ));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static int evidenceLinkCount(IndexCoreDatabase core) {
        if (core == null) return 0;
        try (Cursor c = core.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM idx_claim_evidence", null
        )) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static void touchClaim(IndexCoreDatabase core, String claimId) {
        ContentValues row = new ContentValues();
        row.put("updated_at", System.currentTimeMillis());
        core.getWritableDatabase().update(
                "idx_research_claims", row, "claim_id=?", new String[]{safe(claimId)}
        );
    }

    private static void audit(
            IndexCoreDatabase core,
            String entityType,
            String entityId,
            String action,
            String detail
    ) {
        if (core == null) return;
        ContentValues row = new ContentValues();
        row.put("entity_type", safe(entityType).toUpperCase(Locale.ROOT));
        row.put("entity_id", safe(entityId));
        row.put("action", safe(action).toUpperCase(Locale.ROOT));
        row.put("detail", safe(detail));
        row.put("created_at", System.currentTimeMillis());
        core.getWritableDatabase().insert("idx_research_audit", null, row);
    }

    private static String criticismAuditDetail(SourceCriticism value) {
        List<String> fields = new ArrayList<>();
        if (!value.sourceClass.isEmpty()) fields.add("class=" + value.sourceClass);
        if (!value.genre.isEmpty()) fields.add("genre=" + value.genre);
        if (!value.dating.isEmpty()) fields.add("dating=" + value.dating);
        if (!value.origin.isEmpty()) fields.add("origin=" + value.origin);
        if (!value.language.isEmpty()) fields.add("language=" + value.language);
        return compact(String.join(" | ", fields), 300);
    }

    private static SourceCriticism emptyCriticism(String sourceId) {
        return new SourceCriticism(
                sourceId, "", "", "", "", "", "", "", "", "", "", "", "", "", 0L
        );
    }

    private static ClaimStatus claimStatus(String value) {
        try { return ClaimStatus.valueOf(safe(value).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return ClaimStatus.OPEN; }
    }

    private static EvidenceRole evidenceRole(String value) {
        try { return EvidenceRole.valueOf(safe(value).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return EvidenceRole.CONTEXTUALIZES; }
    }

    private static String compact(String value, int max) {
        String clean = safe(value).replaceAll("\\s+", " ");
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
