# Semantic live OCR patch

This branch overlays three Java sources onto the archived Android project before build:

- `TopicMatcher.java` — expands mapped terms with local dictionary synonyms and emits a 0..1 relevance score.
- `MatchHit.java` — carries semantic/relevance metadata while preserving the original constructor/API.
- `OverlayView.java` — renders temporally smoothed OCR regions as animated semantic echoes (pulse rings + sweep), using relevance as visual intensity.

The build workflow copies `patches/app/` over the unzipped project and then runs the existing `:app:assembleDebug` task.

No camera frame or OCR text is sent to a remote semantic service by this patch.
