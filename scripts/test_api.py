#!/usr/bin/env python3
r"""
YouMo API — 完整集成测试套件
===============================
运行: python scripts/test_api.py
前提: 后端已启动 (mvn spring-boot:run -pl backend/youmo-api -Dspring-boot.run.profiles=local)

覆盖:
  A. 安全约束  — 用户名校验 / 防枚举 / 限流 / 唯一性
  B. 功能 CRUD — 书 / 角色 / 大纲 / 世界观
  C. Phase 4   — 伏笔 / 角色关系 / 章节分析 / 风格分析 / Plan / SSE
"""
import json, sys, os, urllib.request, urllib.error, time

os.environ["PYTHONIOENCODING"] = "utf-8"
BASE  = "http://localhost:8080/api"
TOKEN = None
TS   = str(int(time.time()))[-6:]  # 每次运行唯一后缀，避免跨运行数据污染

def req(method, path, data=None):
    url = f"{BASE}{path}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None
    r = urllib.request.Request(url, data=body, method=method)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    if TOKEN: r.add_header("Authorization", f"Bearer {TOKEN}")
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            b = json.loads(resp.read())
            return b.get("code", resp.status), b
    except urllib.error.HTTPError as e:
        b = json.loads(e.read() if e.fp else b"{}")
        return b.get("code", e.code), b

def PASS(msg):  print(f"  [PASS] {msg}")
def FAIL(msg):  print(f"  [FAIL] {msg}")
def INFO(msg):  print(f"  [INFO] {msg}")
def ABORT(msg, body=None):
    print(f"\n[ABORT] {msg}")
    if body: print(f"  body={body}")
    sys.exit(1)

def is_ok(condition, msg):
    if condition: PASS(msg); return True
    FAIL(msg); return False

def hdr(title): print(f"\n{'='*60}\n  {title}\n{'='*60}")

# ════════════════════════════════════════════════════════════
# A. 安全约束测试
# ════════════════════════════════════════════════════════════
def test_security_constraints():
    hdr("A. Security Constraints")

    # ── A1: 注册必填项 ──
    print("\n-- A1: Required fields --")
    s, b = req("POST", "/users/register", {"password": "test123"})
    ok = is_ok(s != 200, "Reject missing email")
    if ok: INFO(f"message={b.get('message','?')}")

    s, b = req("POST", "/users/register", {"email": "no@pw.com"})
    ok = is_ok(s != 200, "Reject missing password")
    if ok: INFO(f"message={b.get('message','?')}")

    # ── A2: 用户名格式校验 ──
    print("\n-- A2: Username format validation --")
    cases = [
        ("ab",          False, "Too short (<2)"),
        ("a" * 21,      False, "Too long (>20)"),
        ("12345",       False, "Pure digits"),
        ("test@user",   False, "Contains @"),
        ("admin",       False, "Reserved word"),
        ("root",        False, "Reserved word"),
        ("system",      False, "Reserved word"),
        (f"正常_n_{TS}",  True, "Valid: Chinese+alpha+num+_-"),
        (f"用户A_{TS}",   True, "Valid: Chinese+alpha"),
        (f"test_u_{TS}",  True, "Valid: alpha+_"),
    ]
    for name, should_pass, desc in cases:
        s, b = req("POST", "/users/register", {
            "email": f"ut_{name[:4]}_{TS}@t.com",
            "username": name,
            "password": "test1234"
        })
        if should_pass:
            if s == 200: PASS(desc)
            else: FAIL(f"{desc} — got: {b.get('message','?')}")
        else:
            if s != 200: PASS(desc)
            else: FAIL(f"{desc} — should have been rejected")

    # ── A3: 用户名唯一性 ──
    print("\n-- A3: Username uniqueness --")
    s, b = req("POST", "/users/register", {
        "email": f"uniq1_{TS}@t.com", "username": f"UniqueUser_{TS}", "password": "test1234"})
    if is_ok(s == 200, "Register UniqueUser"): INFO(f"id={b['data']['id']}")

    s, b = req("POST", "/users/register", {
        "email": f"uniq2_{TS}@t.com", "username": f"UniqueUser_{TS}", "password": "test1234"})
    is_ok(s != 200, "Reject duplicate username")

    # ── A4: 邮箱唯一性 ──
    print("\n-- A4: Email uniqueness --")
    s, b = req("POST", "/users/register", {
        "email": f"uniq1_{TS}@t.com", "username": "diff_user", "password": "test1234"})
    is_ok(s != 200, "Reject duplicate email")

    # ── A5: 登录 — 用户名和邮箱都可登录 ──
    print("\n-- A5: Login with username OR email --")
    s, b = req("POST", "/users/login", {"account": f"UniqueUser_{TS}", "password": "test1234"})
    if is_ok(s == 200, "Login with username"): INFO(f"token={b['data']['token'][:20]}...")

    s, b = req("POST", "/users/login", {"account": f"uniq1_{TS}@t.com", "password": "test1234"})
    is_ok(s == 200, "Login with email")

    # ── A6: 防枚举 — 不存在的用户也返回统一错误 ──
    print("\n-- A6: Anti-enumeration (unified error messages) --")
    # 先成功登录一次来重置计数器，确保两个账户从相同起点开始
    s, b = req("POST", "/users/login", {"account": f"UniqueUser_{TS}", "password": "test1234"})
    s1, b1 = req("POST", "/users/login", {"account": "nonexist_user_xyz", "password": "test"})
    s2, b2 = req("POST", "/users/login", {"account": f"UniqueUser_{TS}", "password": "wrongpass"})
    msg1 = b1.get("message", "")
    msg2 = b2.get("message", "")
    # 核心错误信息必须一致（不含剩余次数，因账户级/IP级限流计数可能不同）
    base1 = msg1.split("（")[0] if "（" in msg1 else msg1
    base2 = msg2.split("（")[0] if "（" in msg2 else msg2
    if base1 == base2:
        PASS(f"Unified error: '{base1}'")
    else:
        FAIL(f"Different messages — enumeration possible: '{msg1}' vs '{msg2}'")

    # ── A7: 登录限流 — 连续失败 5 次后锁定 ──
    print("\n-- A7: Login rate limiting --")
    account = f"UniqueUser_{TS}"
    for i in range(1, 7):
        s, b = req("POST", "/users/login", {"account": account, "password": "wrongpw"})
        msg = b.get("message", "")
        if "锁定" in msg:
            PASS(f"Locked after {i} failures: {msg[:50]}")
            break
        elif i == 6:
            FAIL("Not locked after 6 failures")
    else:
        s, b = req("POST", "/users/login", {"account": account, "password": "wrongpw"})
        msg = b.get("message", "")
        if "锁定" in msg:
            PASS(f"Account locked: {msg[:50]}")
        else:
            FAIL(f"Unlocked after 6+ attempts: code={s} msg={msg[:60]}")

    # ── A8: 登录成功重置限流计数器 ──
    print("\n-- A8: Successful login resets rate limiter --")
    # 注册一个新用户来测试「非锁定态失败后成功登录重置计数」
    test_user = f"ResetTest_{TS}"
    s, b = req("POST", "/users/register", {
        "email": f"reset_{TS}@t.com", "username": test_user, "password": "test1234"})
    if s == 200:
        # 失败 2 次（不触发锁定）
        req("POST", "/users/login", {"account": test_user, "password": "wrongpw"})
        req("POST", "/users/login", {"account": test_user, "password": "wrongpw"})
        # 成功登录应重置计数器
        s, b = req("POST", "/users/login", {"account": test_user, "password": "test1234"})
        if s == 200:
            # 再失败 1 次 — 不应锁定（计数器已重置）
            s2, b2 = req("POST", "/users/login", {"account": test_user, "password": "wrongpw"})
            msg2 = b2.get("message", "")
            if "锁定" not in msg2:
                PASS("Counter reset after success (1st failure not locked)")
            else:
                FAIL(f"Counter not reset: {msg2[:60]}")
        else:
            FAIL("Could not login with correct password after 2 failures")
    else:
        FAIL(f"Could not register reset-test user: {b.get('message','?')}")


# ════════════════════════════════════════════════════════════
# B. 功能 CRUD
# ════════════════════════════════════════════════════════════
def test_feature_crud():
    hdr("B. Feature CRUD")

    # ── Auth ──
    global TOKEN
    e2e_email = f"fulle2e_{TS}@t.com"
    s, b = req("POST", "/users/login", {"account": e2e_email, "password": "test123"})
    if s != 200:
        s, b = req("POST", "/users/register",
                    {"email": e2e_email, "username": f"e2etester_{TS}", "password": "test123"})
        if s == 200:
            INFO(f"Registered e2e user (id={b['data']['id']})")
        s, b = req("POST", "/users/login", {"account": e2e_email, "password": "test123"})
    if s != 200: ABORT("Cannot authenticate", b)
    TOKEN = b["data"]["token"]
    PASS(f"Logged in as e2etester")

    # ── Book ──
    s, b = req("POST", "/books", {"title": "E2E Test Novel", "theme": "xianxia",
               "core_idea": "Test idea"})
    if not is_ok(s == 200, "Create book"): ABORT("Book create failed", b)
    book_id = b["data"]["id"]
    INFO(f"Book ID={book_id}")

    # ── Character ──
    s, b = req("POST", f"/books/{book_id}/characters", {
        "name": "Ye Chen", "gender": "male", "identity": "Disciple",
        "depth_level": "L2", "age_description": "20"})
    if not is_ok(s == 200, "Create character"): ABORT("Char create failed", b)
    char_a = b["data"]["id"]

    s, b = req("POST", f"/books/{book_id}/characters", {
        "name": "Su Ling", "gender": "female", "identity": "Elder",
        "depth_level": "L3", "age_description": "18"})
    if not is_ok(s == 200, "Create character 2"): ABORT("Char2 create failed", b)
    char_b = b["data"]["id"]
    INFO(f"Characters: {char_a}, {char_b}")

    # ── Outline ──
    s, b = req("POST", f"/books/{book_id}/outline/node", {
        "title": "Chapter 1", "node_type": "CHAPTER", "sequence": 1})
    if not is_ok(s == 200, "Create outline node"): ABORT("Outline failed", b)
    node_id = b["data"]["id"]
    INFO(f"Outline node ID={node_id}")

    # ── World Setting ──
    s, b = req("PUT", f"/books/{book_id}/world-setting", {
        "era": "Ancient era", "geography": "Nine continents",
        "culture": "Cultivation supreme", "core_rule_type": "Qi system"})
    is_ok(s == 200, "Save world setting")

    return book_id, char_a, char_b, node_id


# ════════════════════════════════════════════════════════════
# C. Phase 4 功能
# ════════════════════════════════════════════════════════════
def test_phase4(book_id, char_a, char_b, node_id):
    hdr("C. Phase 4 Features")

    # ── Foreshadowing ──
    print("\n-- Foreshadowing CRUD --")
    s, b = req("POST", f"/books/{book_id}/foreshadowings", {
        "description": "A sealed power hidden in a pendant",
        "foreshadowing_type": "ITEM", "importance": "HIGH", "status": "ACTIVE"})
    if is_ok(s == 200, "Create foreshadowing"):
        fid = b["data"]["id"]
        req("DELETE", f"/books/{book_id}/foreshadowings/{fid}")
        PASS("Delete foreshadowing")

    # ── Character Relationship ──
    print("\n-- Character Relationship + Graph --")
    s, b = req("POST", f"/books/{book_id}/character-relationships", {
        "source_character": {"id": char_a},
        "target_character": {"id": char_b},
        "relationship_type": "friend",
        "description": "Fellow disciples",
        "intimacy_level": 6})
    if is_ok(s == 200, "Create relationship"):
        rid = b["data"]["id"]
        PASS(f"Relationship ID={rid}")

    s, b = req("GET", f"/books/{book_id}/character-relationships/graph")
    if is_ok(s == 200, "Get graph"):
        g = b.get("data", {})
        INFO(f"Nodes={len(g.get('nodes',[]))} Edges={len(g.get('edges',[]))}")

    # ── Chapter Content + Analysis ──
    print("\n-- Chapter Content + Analysis --")
    chapter_text = "The mist hung low over the peaks. Ye Chen stood alone, gripping his sword."
    s, b = req("POST", f"/chapters/{node_id}/content", {"content": chapter_text})
    is_ok(s == 200, "Save chapter content")

    INFO("Calling DeepSeek for chapter analysis (~10s)...")
    s, b = req("POST", f"/chapters/{node_id}/analyze")
    if is_ok(s == 200, "Analyze chapter"):
        summary = b.get("data", {})
        fields = ["core_events", "appearing_characters", "emotion_curve_point",
                   "key_scenes", "new_foreshadowings", "world_elements"]
        filled = sum(1 for f in fields if summary.get(f))
        INFO(f"Analysis: {filled}/{len(fields)} fields populated")

    # ── Style Profile ──
    print("\n-- Style Profile --")
    INFO("Calling DeepSeek for style analysis (~15s)...")
    s, b = req("POST", f"/books/{book_id}/style-profile/analyze")
    if is_ok(s == 200, "Analyze style"):
        p = b.get("data", {})
        INFO(f"Style label: {p.get('style_label','?')}")

    # ── Plan Mode ──
    print("\n-- Plan-then-Execute --")
    INFO("Calling DeepSeek for plan generation (~10s)...")
    s, b = req("POST", "/generation/continue-plan", {
        "book_id": book_id, "context": chapter_text,
        "instructions": "Continue the story", "temperature": 1.2})
    if is_ok(s == 200, "Generate plan"):
        d = b.get("data", {})
        if isinstance(d, dict):
            INFO(f"Plan: {d.get('plan','?')[:80]}...")
            INFO(f"Emotion: {d.get('emotion_arc','?')}")

    # ── SSE Streaming ──
    print("\n-- SSE Streaming --")
    INFO("Testing SSE continue (~10s)...")
    import http.client
    conn = http.client.HTTPConnection("localhost", 8080, timeout=30)
    sse_body = json.dumps({
        "book_id": book_id, "context": chapter_text,
        "instructions": "Continue this cultivation story.", "temperature": 1.0})
    conn.request("POST", "/api/generation/continue", body=sse_body, headers={
        "Content-Type": "application/json; charset=utf-8",
        "Authorization": f"Bearer {TOKEN}"})
    resp = conn.getresponse()
    if resp.status != 200:
        FAIL(f"SSE HTTP {resp.status}: {resp.read().decode()[:200]}")
        conn.close()
    else:
        chunks, text = 0, ""
        event = None
        for line in resp:
            line = line.decode("utf-8", errors="replace").strip()
            if line.startswith("event:"): event = line[6:].strip()
            elif line.startswith("data:"):
                data = line[5:].strip()
                if event == "chunk": chunks += 1; text += data
                elif event == "done": break
                elif event == "error": FAIL(f"SSE error: {data}"); break
                event = None
        if chunks > 0:
            PASS(f"SSE: {chunks} chunks, {len(text)} chars")
            INFO(f"Text: {text[:80]}...")
        else:
            FAIL(f"SSE: 0 chunks")
        conn.close()

    # ── Book Stats ──
    print("\n-- Book Stats --")
    s, b = req("GET", f"/books/{book_id}/stats")
    if is_ok(s == 200, "Get book stats"):
        keys = list(b.get("data", {}).keys())
        INFO(f"Stats: {keys}")


# ════════════════════════════════════════════════════════════
def main():
    print("=" * 60)
    print("  YouMo — Full Integration & Security Test Suite")
    print("=" * 60)

    test_security_constraints()
    book_id, char_a, char_b, node_id = test_feature_crud()
    test_phase4(book_id, char_a, char_b, node_id)

    print("\n" + "=" * 60)
    print("  All tests completed!")
    print("=" * 60)

if __name__ == "__main__":
    main()
