"""Compare AI generation output across different parameter combinations.

Usage: python scripts/compare_params.py
Output: scripts/compare_results/ — one file per param set + summary.txt
"""
import json, os, time
import requests
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = 'http://localhost:8080'
CONTEXT = '李明推开包厢的门，里面已经坐了五个人。主位上的男人抬起头，笑了一下："来得正好，我们刚说到你。"'
INSTRUCTIONS = '续写下一段'

# ── Parameter sets to compare ──
SETS = [
    {
        'name': 'A-当前默认',
        'params': {'temperature': 1.0, 'top_p': 1.0, 'frequency_penalty': 0.0, 'presence_penalty': 0.0, 'max_tokens': 300},
    },
    {
        'name': 'B-高温度',
        'params': {'temperature': 1.3, 'top_p': 1.0, 'frequency_penalty': 0.0, 'presence_penalty': 0.0, 'max_tokens': 300},
    },
    {
        'name': 'C-防重复',
        'params': {'temperature': 1.0, 'top_p': 1.0, 'frequency_penalty': 0.5, 'presence_penalty': 0.0, 'max_tokens': 300},
    },
    {
        'name': 'D-综合最优',
        'params': {'temperature': 1.2, 'top_p': 0.95, 'frequency_penalty': 0.3, 'presence_penalty': 0.2, 'max_tokens': 300},
    },
]

def login():
    body = {'email': 'test@test.com', 'password': '123456'}
    resp = requests.post(f'{BASE}/api/users/login', json=body)
    return resp.json()['data']['token']

def generate(token, params):
    body = {
        'context': CONTEXT,
        'instructions': INSTRUCTIONS,
        **params,
    }
    resp = requests.post(
        f'{BASE}/api/generation/continue',
        json=body,
        headers={'Authorization': f'Bearer {token}'},
        stream=True,
        timeout=120,
    )
    resp.encoding = 'utf-8'

    full = []
    for line in resp.iter_lines(decode_unicode=True):
        if not line: continue
        if line.startswith('data:'):
            chunk = line[5:].strip()
            if chunk:
                full.append(chunk)
        if 'event:done' in line:
            break
        if 'event:error' in line:
            return f'ERROR: {line}'
    return ''.join(full)

if __name__ == '__main__':
    out_dir = os.path.join(os.path.dirname(__file__), 'compare_results')
    os.makedirs(out_dir, exist_ok=True)

    token = login()
    print(f'Login OK\n')

    results = []
    for s in SETS:
        name = s['name']
        params = s['params']
        print(f'--- {name} ---')
        print(f'    参数: {json.dumps(params, ensure_ascii=False)}')

        try:
            text = generate(token, params)
            results.append({'name': name, 'params': params, 'text': text})

            # Save individual result
            fname = os.path.join(out_dir, f'{name}.txt')
            with open(fname, 'w', encoding='utf-8') as f:
                f.write(f"参数: {json.dumps(params, ensure_ascii=False)}\n\n")
                f.write(f"前文: {CONTEXT}\n\n")
                f.write(f"续写:\n{text}")
            print(f'    {len(text)} 字 -> {fname}')
            print(f'    前80字: {text[:80]}...')
        except Exception as e:
            print(f'    失败: {e}')
            results.append({'name': name, 'params': params, 'text': f'FAILED: {e}'})

        time.sleep(1)  # avoid rate limiting

    # Write summary
    summary_path = os.path.join(out_dir, 'summary.txt')
    with open(summary_path, 'w', encoding='utf-8') as f:
        f.write(f"前文: {CONTEXT}\n\n")
        for r in results:
            f.write(f"{'='*60}\n")
            f.write(f"{r['name']}: {json.dumps(r['params'], ensure_ascii=False)}\n")
            f.write(f"字数: {len(r['text']) if 'FAILED' not in r['text'] else 'N/A'}\n")
            f.write(f"内容:\n{r['text']}\n\n")

    print(f'\nDone — summary saved to {summary_path}')
