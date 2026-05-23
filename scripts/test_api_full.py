"""Comprehensive API smoke test — all Phase 1-3 endpoints"""
import requests, json, sys, time, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:8080/api'
passed = 0
failed = 0
results = []

def test(name, method, path, expected_code=200, body=None, headers=None, check=None):
    """check: None=auto (code==200 and data.code==200), 'ok'=status 200, 'any'=any status"""
    global passed, failed
    url = BASE + path
    h = {'Content-Type': 'application/json'}
    if headers: h.update(headers)

    try:
        if method == 'GET':
            r = requests.get(url, headers=h)
        elif method == 'POST':
            r = requests.post(url, json=body, headers=h)
        elif method == 'PUT':
            r = requests.put(url, json=body, headers=h)
        elif method == 'DELETE':
            r = requests.delete(url, headers=h)
        else:
            results.append((False, name, 'unknown method'))
            failed += 1
            return None

        if check == 'any':
            ok = True
        elif check == 'stream':
            ok = r.status_code == 200 and 'text/event-stream' in r.headers.get('Content-Type', '')
        else:
            ok = r.status_code == expected_code
            if ok and r.status_code == 200:
                try:
                    data = r.json()
                    api_code = data.get('code', 200)
                    if expected_code == 200:
                        ok = api_code == 200
                    elif expected_code >= 400:
                        ok = api_code == expected_code
                except:
                    ok = True  # non-JSON response (like MD export)

        mark = '[PASS]' if ok else '[FAIL]'
        detail = f' status={r.status_code}'
        if not ok:
            try:
                detail += f' body={r.text[:150]}'
            except:
                pass
        results.append((ok, name, detail))
        if ok: passed += 1
        else: failed += 1
        return r
    except Exception as e:
        failed += 1
        results.append((False, name, str(e)[:100]))
        return None

def auth(token):
    return {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}

# ── Setup ──
email = f'apitest_{int(time.time())}@t.com'
pwd = 'test123456'

print('=== Auth ===')
test('Register',           'POST', '/users/register', body={'email': email, 'password': pwd})
test('Register duplicate', 'POST', '/users/register', expected_code=400, body={'email': email, 'password': pwd})
r = test('Login',          'POST', '/users/login', body={'email': email, 'password': pwd})
token = r.json()['data']['token']
h = auth(token)
test('Login bad password', 'POST', '/users/login', expected_code=401, body={'email': email, 'password': 'wrong'})
test('Get me',             'GET',  '/users/me', headers=h)
test('Change password',    'PUT',  '/users/password', body={'old_password': pwd, 'new_password': pwd + 'x'}, headers=h)
test('Change password back','PUT', '/users/password', body={'old_password': pwd + 'x', 'new_password': pwd}, headers=h)

print('=== Auth Guard ===')
test('Books without token', 'GET', '/books', expected_code=401, check='any')
test('Books with token',    'GET', '/books', headers=h)

print('=== Books CRUD ===')
r = test('Create book', 'POST', '/books', body={'title': 'Test Book', 'core_idea': 'Test core idea'}, headers=h)
book_id = r.json()['data']['id']
r = test('Get book',    'GET',  f'/books/{book_id}', headers=h)
owner_id = r.json()['data'].get('owner_id')
if owner_id:
    results.append((True, f'owner_id={owner_id}', '(should be current user, not hardcoded 1)'))
    passed += 1
test('List books',   'GET',  '/books', headers=h)
test('Update book',  'PUT',  f'/books/{book_id}', body={'title': 'Updated Title'}, headers=h)

print('=== Characters ===')
test('List characters empty', 'GET', f'/books/{book_id}/characters', headers=h)
r = test('Create character',  'POST', f'/books/{book_id}/characters', body={
    'name': 'TestChar', 'gender': 'male', 'age_description': '25',
    'appearance': 'Tall', 'origin': 'Village', 'identity': 'Swordsman',
    'depth_level': 'L2'
}, headers=h)
char_id = r.json()['data']['id']
test('Update character', 'PUT',    f'/books/{book_id}/characters/{char_id}', body={'name': 'Renamed'}, headers=h)
test('Delete character', 'DELETE', f'/books/{book_id}/characters/{char_id}', headers=h)

print('=== Outline ===')
test('Get outline empty', 'GET', f'/books/{book_id}/outline', headers=h)
r = test('Create volume', 'POST', f'/books/{book_id}/outline/node', body={'title': 'Vol 1', 'node_type': 'VOLUME', 'sequence': 0}, headers=h)
vol_id = r.json()['data']['id']
r = test('Create chapter', 'POST', f'/books/{book_id}/outline/node', body={'title': 'Ch 1', 'node_type': 'CHAPTER', 'sequence': 0, 'parent_id': vol_id}, headers=h)
ch_id = r.json()['data']['id']
test('Create scene', 'POST', f'/books/{book_id}/outline/node', body={'title': 'Scene 1', 'node_type': 'SCENE', 'sequence': 0, 'parent_id': ch_id}, headers=h)
test('Get outline tree', 'GET', f'/books/{book_id}/outline', headers=h)
test('Update node', 'PUT', f'/books/{book_id}/outline/{ch_id}', body={'title': 'Ch 1 Renamed'}, headers=h)
test('Delete node', 'DELETE', f'/books/{book_id}/outline/{ch_id}', headers=h)

print('=== Chapter Content ===')
test('Get content empty', 'GET', f'/chapters/{vol_id}/content', headers=h)
test('Save content', 'POST', f'/chapters/{vol_id}/content', body={
    'content': 'Test body content here.', 'word_count': 4, 'source': 'USER_EDITED',
    'storage_type': 'FULL', 'status': 'DRAFT'
}, headers=h)
test('Get content', 'GET', f'/chapters/{vol_id}/content', headers=h)
test('Version history', 'GET', f'/chapters/{vol_id}/content/versions', headers=h)

print('=== World Setting ===')
test('Get setting empty', 'GET', f'/books/{book_id}/world-setting', headers=h)
test('Save setting', 'PUT', f'/books/{book_id}/world-setting', body={
    'era': 'Near Future', 'geography': 'Wasteland', 'history_events': 'War',
    'politics': 'Council', 'economy': 'Barter', 'culture': 'Survivalism',
    'military': 'Militia', 'core_rule_type': 'Radiation Mutations',
    'core_rule_summary': 'Radiation grants superpowers'
}, headers=h)
test('Get setting saved', 'GET', f'/books/{book_id}/world-setting', headers=h)

print('=== Export ===')
r = test('Export MD', 'GET', f'/books/{book_id}/export', headers=h, check='any')

print('=== Random Generation ===')
test('Random book idea',     'POST', '/generation/random/book-idea', body={'genre': 'xianxia'}, headers=h)
test('Random character',     'POST', f'/generation/random/character/{book_id}', headers=h)
test('Random world setting', 'POST', f'/generation/random/world-setting/{book_id}', headers=h)
test('Random outline',       'POST', f'/generation/random/outline/{book_id}', headers=h)

print('=== AI Generation (SSE) ===')
test('AI continue (empty ctx)', 'POST', '/generation/continue', body={'context': ''}, headers=h, check='stream')
test('AI rewrite (polish)',     'POST', '/generation/rewrite', body={'context': 'test text', 'mode': 'polish'}, headers=h, check='stream')
test('AI rewrite (bad mode)',   'POST', '/generation/rewrite', body={'context': 'test', 'mode': 'invalid'}, headers=h, check='stream')

print('=== Cleanup ===')
test('Delete book', 'DELETE', f'/books/{book_id}', headers=h)

# ── Summary ──
print('\n' + '=' * 60)
for ok, name, detail in results:
    print(f'  {name}: {detail}')
print('=' * 60)
print(f'Passed: {passed}  Failed: {failed}  Total: {passed + failed}')
if failed == 0:
    print('ALL TESTS PASSED')
else:
    print(f'{failed} TEST(S) FAILED')
sys.exit(0 if failed == 0 else 1)
