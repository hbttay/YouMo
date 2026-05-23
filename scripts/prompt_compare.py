# -*- coding: utf-8 -*-
"""Prompt Engineering comparison — 3 prompt levels, same context, compare results."""
import json, os, time, http.client

API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
BASE = "api.deepseek.com"

CONTEXT = """林风在藏经阁发现了一本破旧的"混沌诀"秘籍。"""

# 3 levels of prompt quality
PROMPTS = {
    "Level 1 - Bare minimum": {
        "system": "续写故事。",
        "user": CONTEXT + "\n续写：",
    },
    "Level 2 - Role + constraints": {
        "system": "你是专业小说续写助手。续写长度 200-500 字。只输出正文。",
        "user": CONTEXT + "\n请续写下一段：",
    },
    "Level 3 - Detailed style guide": {
        "system": """你是修仙小说作家，擅长古风文笔和悬念铺设。
续写要求：
- 保持古朴文风，避免现代词汇
- 200-400 字
- 聚焦主角内心活动，不引入新角色
- 结尾留悬念钩子
- 只输出正文，不加任何解释""",
        "user": CONTEXT + "\n请续写：",
    },
}


def call(messages, temperature=0.8):
    body = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": 512,
        "stream": False,
    }
    conn = http.client.HTTPSConnection(BASE, timeout=60)
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
    conn.request("POST", "/v1/chat/completions",
                 json.dumps(body, ensure_ascii=False).encode("utf-8"), headers)
    resp = conn.getresponse()
    data = json.loads(resp.read().decode("utf-8"))
    conn.close()
    if resp.status == 200:
        c = data["choices"][0]
        return {
            "content": c["message"]["content"],
            "tokens": data["usage"]["total_tokens"],
            "finish": c["finish_reason"],
        }
    else:
        return {"error": str(data), "tokens": 0, "finish": "error"}


for name, prompt in PROMPTS.items():
    print("=" * 55)
    print(f"  {name}")
    print("  System: {:.60}...".format(prompt["system"]))
    print("-" * 55)
    start = time.time()
    result = call([
        {"role": "system", "content": prompt["system"]},
        {"role": "user", "content": prompt["user"]},
    ])
    elapsed = time.time() - start
    print(f"  Time: {elapsed:.1f}s | Tokens: {result['tokens']} | Finish: {result['finish']}")
    print("  Content:")
    for line in result.get("content", result.get("error", "?")).split("\n"):
        print(f"  | {line}")
    print()

print("Done — compare content quality at each level.")
