# -*- coding: utf-8 -*-
"""3 experiments: A=temperature, B=negative constraints, C=few-shot imitation."""
import json, os, time, http.client

API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
BASE = "api.deepseek.com"
CONTEXT = "林风在藏经阁发现了一本破旧的\"混沌诀\"秘籍。"

BASE_SYSTEM = "你是专业小说续写助手。续写 200-400 字。只输出正文。"

# Few-shot sample — Jin Yong style (public domain snippet, style reference only)
JIN_YONG_SAMPLE = """那少女道："我姓黄，单名一个蓉字。"郭靖道："黄姑娘。"那少女笑道：
"你叫我黄姑娘，我就叫你郭大哥。"郭靖道："好。"两人坐在树下，谈谈说说，
郭靖将自己自幼随母亲在蒙古大漠流落，以及江南七怪收他为徒的经过，都说了
出来。那少女听得津津有味，不住追问。"""


def call(system, user, temperature=0.8, extra_body=None):
    body = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": temperature,
        "max_tokens": 512,
        "stream": False,
    }
    if extra_body:
        body.update(extra_body)
    conn = http.client.HTTPSConnection(BASE, timeout=60)
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
    conn.request("POST", "/v1/chat/completions",
                 json.dumps(body, ensure_ascii=False).encode("utf-8"), headers)
    resp = conn.getresponse()
    data = json.loads(resp.read().decode("utf-8"))
    conn.close()
    if resp.status == 200:
        c = data["choices"][0]
        return c["message"]["content"], data["usage"]["total_tokens"], c["finish_reason"]
    return f"ERROR: {data}", 0, "error"


def run():
    results = []

    # ===== Experiment A: Temperature =====
    results.append("=" * 60)
    results.append("EXPERIMENT A — Temperature Comparison")
    results.append("=" * 60)
    results.append("")

    for temp in [0.3, 0.8, 1.2]:
        results.append(f"--- Temperature = {temp} ---")
        t0 = time.time()
        content, tokens, finish = call(BASE_SYSTEM, CONTEXT + "\n请续写：", temperature=temp)
        elapsed = time.time() - t0
        results.append(f"Time: {elapsed:.1f}s | Tokens: {tokens} | Finish: {finish}")
        results.append(content)
        results.append("")

    # ===== Experiment B: Negative Constraints =====
    results.append("=" * 60)
    results.append("EXPERIMENT B — Negative Constraints (forbidden phrases)")
    results.append("=" * 60)
    results.append("")

    no_control = BASE_SYSTEM
    with_blacklist = BASE_SYSTEM + """
严格禁止使用以下套话：心头一震、一股、若有若无、心下微凛、暗自嘀咕、深吸一口气。
违反任何一条都算失败。"""

    results.append("--- WITHOUT blacklist ---")
    t0 = time.time()
    content, tokens, finish = call(no_control, CONTEXT + "\n请续写：", temperature=0.8)
    elapsed = time.time() - t0
    results.append(f"Time: {elapsed:.1f}s | Tokens: {tokens}")
    results.append(content)
    results.append("")

    results.append("--- WITH blacklist ---")
    t0 = time.time()
    content, tokens, finish = call(with_blacklist, CONTEXT + "\n请续写：", temperature=0.8)
    elapsed = time.time() - t0
    results.append(f"Time: {elapsed:.1f}s | Tokens: {tokens}")
    results.append(content)
    results.append("")

    # ===== Experiment C: Few-shot Imitation =====
    results.append("=" * 60)
    results.append("EXPERIMENT C — Few-shot Style Imitation")
    results.append("=" * 60)
    results.append("")

    results.append("--- Reference sample (Jin Yong style) ---")
    results.append(JIN_YONG_SAMPLE[:120] + "...")
    results.append("")

    few_shot_system = f"""你是小说续写助手。请模仿以下文风续写：

【风格样本】
{JIN_YONG_SAMPLE}

要求：200-400 字，只输出正文，不加解释。"""

    t0 = time.time()
    content, tokens, finish = call(few_shot_system, CONTEXT + "\n请续写：", temperature=0.8)
    elapsed = time.time() - t0
    results.append(f"Time: {elapsed:.1f}s | Tokens: {tokens} | Finish: {finish}")
    results.append(content)
    results.append("")

    results.append("=" * 60)
    results.append("ALL EXPERIMENTS COMPLETE")
    results.append("=" * 60)

    with open("scripts/_experiments.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(results))
    print("Done. Results: scripts/_experiments.txt")


if __name__ == "__main__":
    run()
