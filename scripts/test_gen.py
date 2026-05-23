"""Test AI continuation SSE endpoint using requests (proper UTF-8 + chunked handling).

Usage:
    python scripts/test_gen.py              # stream to console + save to test_gen_output.txt
    python scripts/test_gen.py --save-only  # only save to file, no console streaming
"""
import json, sys, os
import requests

BASE = 'http://localhost:8080'

def login():
    body = {'email': 'test@test.com', 'password': '123456'}
    resp = requests.post(f'{BASE}/api/users/login', json=body)
    if resp.status_code != 200:
        requests.post(f'{BASE}/api/users/register', json=body)
        resp = requests.post(f'{BASE}/api/users/login', json=body)
    data = resp.json()
    if data.get('code') != 200:
        raise RuntimeError(f"Login failed: {data}")
    return data['data']['token']

def stream_generate(token, context, instructions='续写下一段', temperature=1.0, max_tokens=800):
    body = {
        'context': context,
        'instructions': instructions,
        'temperature': temperature,
        'max_tokens': max_tokens,
    }
    resp = requests.post(
        f'{BASE}/api/generation/continue',
        json=body,
        headers={'Authorization': f'Bearer {token}'},
        stream=True,
        timeout=120,
    )
    resp.raise_for_status()

    full_text = []
    last_event = ''
    for line in resp.iter_lines(decode_unicode=True):
        if not line:
            last_event = ''
            continue
        if line.startswith('event:'):
            last_event = line[6:].strip()
        elif line.startswith('data:'):
            data = line[5:].strip()
            if last_event == 'chunk' or not last_event:
                full_text.append(data)
                yield 'chunk', data
            elif last_event == 'done':
                yield 'done', ''.join(full_text)
                return
            elif last_event == 'error':
                yield 'error', data
                return

if __name__ == '__main__':
    save_only = '--save-only' in sys.argv

    token = login()
    print(f"Login OK\n")

    context = '夜幕降临，整个城市陷入了沉寂。李明站在楼顶，望着远处的灯火。'
    output_path = os.path.join(os.path.dirname(__file__), 'test_gen_output.txt')

    if save_only:
        full = []
        for evt, data in stream_generate(token, context):
            if evt == 'chunk':
                full.append(data)
            elif evt == 'done':
                result = ''.join(full)
                with open(output_path, 'w', encoding='utf-8') as f:
                    f.write(f"=== 前文 ===\n{context}\n\n=== AI 续写 ===\n{result}")
                print(f"Saved to {output_path} ({len(result)} chars)")
            elif evt == 'error':
                print(f"Error: {data}")
    else:
        print(f"=== 前文 ===\n{context}\n")
        print("=== AI 续写 ===")
        for evt, data in stream_generate(token, context):
            if evt == 'chunk':
                print(data, end='', flush=True)
            elif evt == 'done':
                print(f"\n\n--- 完成，共 {len(data)} 字 ---")
                with open(output_path, 'w', encoding='utf-8') as f:
                    f.write(f"=== 前文 ===\n{context}\n\n=== AI 续写 ===\n{data}")
                print(f"已保存到 {output_path}")
            elif evt == 'error':
                print(f"\n错误: {data}")
