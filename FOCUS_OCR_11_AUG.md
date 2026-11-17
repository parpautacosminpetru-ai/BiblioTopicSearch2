# OCR FOCUS — versiunea 11 august 2026

Bazat exclusiv pe sursa din arhiva ZIP a commitului e139cc98818f8f8e3716ee202d2d987dc237d89b.

## Problema
În implementarea originală, OverlayView.onDraw() desenează toate chenarurile OCR, deși limita de 8 se aplică numai etichetelor complete. Repetarea aceluiași concept colorează numeroase cuvinte din aceeași pagină.

## Modificare
- Păstrează toate detecțiile în memoria sesiunii: nu modifică TopicMatcher și nici indexul.
- Selectează pentru afișare maximum 5 concepte distincte, câte o apariție pe concept.
- Prioritizează expresii mai lungi / cu mai multe cuvinte și conceptele cu mai puține apariții.
- Evită dreptunghiurile care se suprapun cu mai mult de 30% din suprafața celui mai mic.
- Etichetele și atingerea ecranului urmează selecția vizibilă.
- Nu schimbă culorile, importul indexului sau interfața de editare.

## Construire APK
În GitHub Actions, selectați ramura fix/aug11-ocr-focus-overlay și rulați manual workflow-ul „Build BiblioTopicSearch APK”.
Acesta dezarhivează BiblioTopicSearch_FINAL.zip și execută Gradle :app:assembleDebug.

## Limitări
Modificarea este o politică de afișare, nu un analizor semantic. Primele 5 potriviri sunt ordonate după criteriile de mai sus și nu presupun înțelegerea istorică a contextului. Nu a fost testată încă pe un telefon fizic.
