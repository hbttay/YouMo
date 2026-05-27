"""本地 Embedding 微服务 — BGE-small-zh (512维)
启动: python scripts/embedding_service.py
端口: 5010
"""

import json
import sys
from pathlib import Path
from flask import Flask, request, jsonify

app = Flask(__name__)

# 首次调用时加载模型（懒加载，先启动服务）
model = None

def get_model():
    global model
    if model is None:
        from sentence_transformers import SentenceTransformer
        print("[embed] Loading BGE-small-zh model...", flush=True)
        model = SentenceTransformer("BAAI/bge-small-zh-v1.5")
        # 预热
        _ = model.encode(["warmup"])
        print("[embed] Model ready.", flush=True)
    return model


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/v1/embeddings", methods=["POST"])
def embeddings():
    data = request.get_json(force=True)
    inp = data.get("input", "")
    if isinstance(inp, str):
        texts = [inp]
    else:
        texts = inp

    try:
        m = get_model()
        vectors = m.encode(texts, normalize_embeddings=True).tolist()

        result_data = [
            {"object": "embedding", "index": i, "embedding": v}
            for i, v in enumerate(vectors)
        ]
        return jsonify({
            "object": "list",
            "data": result_data,
            "model": "BAAI/bge-small-zh-v1.5",
            "usage": {"prompt_tokens": sum(len(t) for t in texts), "total_tokens": sum(len(t) for t in texts)}
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5010
    print(f"[embed] Starting on port {port}...", flush=True)
    # 启动时立即加载模型
    get_model()
    app.run(host="127.0.0.1", port=port, debug=False)
