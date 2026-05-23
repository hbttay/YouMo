# -*- coding: utf-8 -*-
"""DeepSeek API quick test — no emoji, English-only output for Windows GBK safety."""
import os, json, time, http.client

API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
BASE = "api.deepseek.com"

SYSTEM_PROMPT = """你是一个专业的小说续写助手。根据提供的上下文续写下一段内容。
要求：
- 保持一致的文风、人物性格和世界观设定
- 续写长度 200-500 字
- 只输出续写内容，不输出解释和评价"""

USER_PROMPT = """上下文：
主角林风是一名刚加入修仙门派的杂役弟子，每天的工作是打扫藏经阁。
今天他打扫时，无意中翻到了一本破旧的秘籍，封面上写着"混沌诀"三个字。

请续写下一段："""


def request(path, body):
    conn = http.client.HTTPSConnection(BASE, timeout=60)
    payload = json.dumps(body, ensure_ascii=False)
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
    conn.request("POST", path, payload.encode("utf-8"))
    resp = conn.getresponse()
    raw = resp.read()
    conn.close()
    try:
        return resp.status, json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError:
        print(f"  [DEBUG] Raw response ({resp.status}): {raw[:300]}")
        return resp.status, {"_raw": raw.decode("utf-8", errors="replace")[:500]}


def test():
    if not API_KEY or "your-key" in API_KEY.lower():
        pass  # use embedded key

    print("=" * 50)
    print("Test 1: Non-streaming completion")
    print("=" * 50)

    body = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": USER_PROMPT},
        ],
        "temperature": 0.8,
        "max_tokens": 512,
        "stream": False,
    }

    start = time.time()
    status, data = request("/v1/chat/completions", body)
    elapsed = time.time() - start

    if status == 200:
        choice = data["choices"][0]
        usage = data["usage"]
        print(f"  Status: {status}")
        print(f"  Time: {elapsed:.1f}s")
        print(f"  Tokens: prompt={usage['prompt_tokens']} completion={usage['completion_tokens']} total={usage['total_tokens']}")
        print(f"  Finish: {choice['finish_reason']}")
        print(f"  Content:")
        print("  " + "-" * 45)
        print(choice["message"]["content"])
        print("  " + "-" * 45)
    else:
        print(f"  Failed ({status}): {data.get('error', data)}")

    # Test 2: Streaming
    print()
    print("=" * 50)
    print("Test 2: Streaming (SSE)")
    print("=" * 50)

    body["stream"] = True
    conn = http.client.HTTPSConnection(BASE, timeout=60)
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
    start = time.time()
    conn.request("POST", "/v1/chat/completions", json.dumps(body, ensure_ascii=False).encode("utf-8"), headers)
    resp = conn.getresponse()
    print(f"  Status: {resp.status}")

    if resp.status != 200:
        raw = resp.read().decode("utf-8", errors="replace")
        print(f"  Error: {raw[:300]}")
        conn.close()
        return

    chunk_count = 0
    for line in resp:
        line = line.decode("utf-8", errors="replace").strip()
        if line.startswith("data: "):
            chunk = line[6:]
            if chunk == "[DONE]":
                break
            try:
                d = json.loads(chunk)
                delta = d["choices"][0].get("delta", {})
                if "content" in delta and delta["content"]:
                    print(delta["content"], end="", flush=True)
                    chunk_count += 1
            except json.JSONDecodeError:
                pass
    conn.close()
    elapsed = time.time() - start
    print()
    print(f"  Stream time: {elapsed:.1f}s, chunks: {chunk_count}")
    print()
    print("Done.")


if __name__ == "__main__":
    test()
