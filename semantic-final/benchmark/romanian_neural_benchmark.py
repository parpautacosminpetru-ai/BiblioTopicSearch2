import argparse
import hashlib
import json
import math
import re
import time
import unicodedata
from dataclasses import dataclass, asdict
from pathlib import Path

import numpy as np
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer

RERANKER_REPO = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"
RERANKER_REV = "1427fd652930e4ba29e8149678df786c240d8825"
RERANKER_FILE = "onnx/model_qint8_arm64.onnx"
RERANKER_SHA256 = "1825907d6c1a9001ff78124780bbde20a614a8c3df3b63409cf3c72c6fe5c8b4"

NLI_REPO = "onnx-community/multilingual-MiniLMv2-L6-mnli-xnli-ONNX"
NLI_REV = "ca5daf3d11b6c4b3143b1f4602a2edfb64c3ad7e"
NLI_FILE = "onnx/model_int8.onnx"
NLI_SHA256 = "55614f3c7da74184742eaa0006b978744437aa91de9ba4913db42f94d7844a8f"

# Must mirror DirectSupportVerifier.java. These are model calibration parameters, not content rules.
DIRECT_STRONG_RERANK = 6.0
DIRECT_MEDIUM_RERANK_MIN = 0.80
DIRECT_MEDIUM_RERANK_MAX = 4.0
DIRECT_MEDIUM_ENTAILMENT = 0.28
DIRECT_LOW_RERANK_MIN = -4.20
DIRECT_LOW_RERANK_MAX = 0.80
DIRECT_LOW_ENTAILMENT = 0.42
FORWARD_CONTRADICTION_MIN = 0.67
FORWARD_CONTRADICTION_MARGIN = 0.20
REVERSE_VETO_RERANK_MIN = 4.0
REVERSE_CONTRADICTION_MIN = 0.30
REVERSE_CONTRADICTION_MARGIN = 0.05


@dataclass(frozen=True)
class Case:
    id: str
    category: str
    text: str
    query: str
    direct: bool
    gate: bool = True


CASES = [
    Case("literal-broad", "literal_positive", "Dumnezeu este bun și milostiv.", "Dumnezeu", True),
    Case("literal-claim", "literal_positive", "Dumnezeu este bun și milostiv.", "Dumnezeu este bun", True),
    Case("semantic-goodness", "semantic_equivalent_positive", "Dumnezeu este bun și milostiv.", "bunătatea lui Dumnezeu", True),
    Case("contradiction-good-evil", "contradiction", "Dumnezeu este bun și milostiv.", "Dumnezeu este rău", False),
    Case("same-topic-love", "same_topic_neutral", "Dumnezeu este bun și milostiv.", "Dumnezeu și iubire", False),
    Case("narrow-love-absent", "narrow_query", "Dumnezeu este bun și milostiv.", "iubirea lui Dumnezeu față de om", False),
    Case("cause-reformation", "direct_paraphrase_semantic", "Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante.", "cauzele Reformei protestante", True),
    Case("effect-not-cause", "hard_negative", "Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante.", "consecințele Reformei protestante", False),
    Case("restriction-paraphrase", "direct_paraphrase_semantic", "Nu avea voie să părăsească orașul.", "interdicție de deplasare", True),
    Case("travel-association", "same_topic_neutral", "Privea mereu drumul spre exterior și își amintea de vremurile când călătorea.", "interdicție de deplasare", False),
    Case("love-direction-positive", "relation_tracking", "Dumnezeu iubește omul.", "iubirea lui Dumnezeu față de om", True),
    Case("love-direction-negative", "hard_negative", "Omul îl iubește pe Dumnezeu.", "iubirea lui Dumnezeu față de om", False),
    Case("actor-role-literal", "literal_positive", "Ion îl atacă pe Petru.", "Ion îl atacă pe Petru", True),
    Case("actor-role-reversed", "contradiction", "Petru îl atacă pe Ion.", "Ion îl atacă pe Petru", False),
    Case("wrong-object-hard-negative", "hard_negative", "Lipsa proviziilor și iarna grea au provocat retragerea armatei din război.", "cauzele Reformei protestante", False),
    Case("definition-direct", "semantic_equivalent_positive", "Prin secularizare se înțelege transferul bunurilor bisericești în proprietate laică.", "definiția secularizării", True),
    Case("definition-topic-only", "same_topic_neutral", "Secularizarea a fost discutată intens în epocă.", "definiția secularizării", False),
    Case("multi-sentence-direct", "multi_sentence", "Regele a refuzat cererea. Din acest motiv, delegația a părăsit curtea și negocierile au încetat.", "motivul plecării delegației", True),
    Case("partial-support", "partial_support", "Reforma a produs schimbări politice importante.", "cauzele și consecințele economice ale Reformei", False),
    # Coreference is present in the benchmark as required, but remains diagnostic until a dedicated
    # compact coreference model is validated on Android. The product may extend a span only if the
    # combined evidence passes the same direct-support verifier.
    Case("coreference-papa", "coreference", "Papa a trimis o delegație la curte. El a cerut apoi negocieri.", "cererea de negocieri a Papei", True, gate=False),
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn")
    s = re.sub(r"[^\w]+", " ", s, flags=re.UNICODE)
    return re.sub(r"\s+", " ", s).strip()


def literal(query, text):
    q = norm(query)
    t = norm(text)
    return bool(q) and re.search(r"(?:^| )" + re.escape(q) + r"(?: |$)", t) is not None


def softmax(logits):
    x = np.asarray(logits, dtype=np.float32)
    x = np.exp(x - x.max())
    return x / x.sum()


def make_feeds(session, batch):
    feeds = {}
    for inp in session.get_inputs():
        if inp.name in batch:
            feeds[inp.name] = np.asarray(batch[inp.name], dtype=np.int64)
    return feeds


def rerank(session, tokenizer, query, text):
    batch = tokenizer(query, text, truncation=True, max_length=256, return_tensors="np")
    raw = session.run(None, make_feeds(session, batch))[0]
    return float(np.asarray(raw, dtype=np.float32).reshape(-1)[0])


def nli(session, tokenizer, premise, hypothesis):
    batch = tokenizer(premise, hypothesis, truncation=True, max_length=256, return_tensors="np")
    raw = session.run(None, make_feeds(session, batch))[0]
    return softmax(np.asarray(raw, dtype=np.float32).reshape(-1)[:3])


def policy(r, fwd, rev):
    forward_contradiction = fwd[2] >= FORWARD_CONTRADICTION_MIN and fwd[2] - fwd[0] >= FORWARD_CONTRADICTION_MARGIN
    reverse_veto = r >= REVERSE_VETO_RERANK_MIN and rev[2] >= REVERSE_CONTRADICTION_MIN and rev[2] - rev[0] >= REVERSE_CONTRADICTION_MARGIN
    if forward_contradiction or reverse_veto:
        return False, "CONTRADICTION"
    strong = r >= DIRECT_STRONG_RERANK
    medium = DIRECT_MEDIUM_RERANK_MIN <= r < DIRECT_MEDIUM_RERANK_MAX and fwd[0] >= DIRECT_MEDIUM_ENTAILMENT
    weak = DIRECT_LOW_RERANK_MIN <= r < DIRECT_LOW_RERANK_MAX and fwd[0] >= DIRECT_LOW_ENTAILMENT
    return bool(strong or medium or weak), "DIRECT" if (strong or medium or weak) else "REJECT"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", default="romanian-neural-benchmark.json")
    ap.add_argument("--allow-gating-failures", action="store_true")
    args = ap.parse_args()

    reranker_path = hf_hub_download(RERANKER_REPO, filename=RERANKER_FILE, revision=RERANKER_REV)
    nli_path = hf_hub_download(NLI_REPO, filename=NLI_FILE, revision=NLI_REV)
    assert sha256(reranker_path) == RERANKER_SHA256
    assert sha256(nli_path) == NLI_SHA256

    reranker_tok = AutoTokenizer.from_pretrained(RERANKER_REPO, revision=RERANKER_REV, use_fast=True)
    nli_tok = AutoTokenizer.from_pretrained(NLI_REPO, revision=NLI_REV, use_fast=True)
    reranker_session = ort.InferenceSession(reranker_path, providers=["CPUExecutionProvider"])
    nli_session = ort.InferenceSession(nli_path, providers=["CPUExecutionProvider"])

    results = []
    inference_times = []
    tp = fp = tn = fn = 0
    gating_failures = []

    for case in CASES:
        if literal(case.query, case.text):
            predicted = True
            reason = "LITERAL"
            r = None
            fwd = rev = None
        else:
            # Android uses one generated XLM-R tokenizer graph for both models. Refuse to build if
            # the pinned tokenizers do not agree on actual Romanian benchmark pairs.
            ids_r = reranker_tok(case.query, case.text, truncation=True, max_length=256)["input_ids"]
            ids_n = nli_tok(case.query, case.text, truncation=True, max_length=256)["input_ids"]
            if ids_r != ids_n:
                raise AssertionError(f"Tokenizer mismatch on {case.id}: reranker={ids_r} nli={ids_n}")

            started = time.perf_counter()
            r = rerank(reranker_session, reranker_tok, case.query, case.text)
            fwd = nli(nli_session, nli_tok, case.text, case.query)
            rev = nli(nli_session, nli_tok, case.query, case.text) if r >= REVERSE_VETO_RERANK_MIN else np.array([0.0, 1.0, 0.0], dtype=np.float32)
            inference_times.append((time.perf_counter() - started) * 1000.0)
            predicted, reason = policy(r, fwd, rev)

        if case.direct and predicted:
            tp += 1
        elif case.direct and not predicted:
            fn += 1
        elif not case.direct and predicted:
            fp += 1
        else:
            tn += 1

        ok = predicted == case.direct
        if case.gate and not ok:
            gating_failures.append(case.id)
        row = {
            **asdict(case),
            "predicted_direct": predicted,
            "decision": reason,
            "rerank": None if r is None else round(r, 6),
            "nli_forward": None if fwd is None else [round(float(x), 6) for x in fwd],
            "nli_reverse": None if rev is None else [round(float(x), 6) for x in rev],
            "ok": ok,
        }
        results.append(row)
        print(json.dumps(row, ensure_ascii=False))

    precision = tp / max(1, tp + fp)
    recall = tp / max(1, tp + fn)
    report = {
        "models": {
            "reranker": {"repo": RERANKER_REPO, "revision": RERANKER_REV, "sha256": RERANKER_SHA256},
            "nli": {"repo": NLI_REPO, "revision": NLI_REV, "sha256": NLI_SHA256},
        },
        "counts": {"tp": tp, "fp": fp, "tn": tn, "fn": fn},
        "precision": precision,
        "recall": recall,
        "median_pair_ms_x64_ci": None if not inference_times else float(np.median(inference_times)),
        "gating_failures": gating_failures,
        "results": results,
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("REPORT", json.dumps({k: report[k] for k in ("counts", "precision", "recall", "median_pair_ms_x64_ci", "gating_failures")}, ensure_ascii=False))

    # Precision is the primary product invariant. All false positives are hard failures.
    if fp:
        raise SystemExit(f"Benchmark failed: {fp} false positives")
    if gating_failures and not args.allow_gating_failures:
        raise SystemExit("Benchmark gating failures: " + ", ".join(gating_failures))


if __name__ == "__main__":
    main()
