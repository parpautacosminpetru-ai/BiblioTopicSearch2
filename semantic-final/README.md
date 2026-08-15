# semantic-final

Acest director conține upgrade-ul Lupa Semantică 2.2 aplicat peste proiectul păstrat în `BiblioTopicSearch_FINAL.zip`.

- `patch/` — motor semantic live, START→END, topic/nod, categorii, zoom și căutare semantică.
- `notebook/` — notebook local de surse cu căutare semantică și rezultate verbatim.
- `MODEL_NOTICE.md` — modelul semantic offline și pipeline-ul de embeddings.
- `PRODUCT_RULES.md` — regulile explicite: fără implicit, parafrază, rezumat sau interpretare.
- `VALIDATION.md` — rezultatul build-ului validat și SHA-256 al APK-ului.

Workflow-ul descarcă modelul la build din revizia fixată, îl include în APK și verifică faptul că runtime-ul final nu cere permisiunea INTERNET.
