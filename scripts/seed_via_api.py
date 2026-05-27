"""Batch generate demo books via AI APIs — each call produces unique results.
Order: book → world setting → outline → characters → chapter content
"""
import requests
import json
import time

BASE = "http://localhost:8080/api"
TOKEN = None

GENRES = [
    {"genre": "仙侠修真", "hint": "传统修仙世界，有独特的修炼体系"},
    {"genre": "科幻星际", "hint": "人类星际殖民时代，有外星文明遗迹"},
    {"genre": "悬疑探案", "hint": "古风悬疑，朝堂与江湖交织"},
    {"genre": "都市异能", "hint": "现代都市背景下出现超自然能力"},
    {"genre": "末世生存", "hint": "末日废土，人类挣扎求生"},
    {"genre": "奇幻史诗", "hint": "西方奇幻背景，多种族并存"},
]

def login():
    global TOKEN
    resp = requests.post(f"{BASE}/users/login", json={"account": "writer", "password": "writer123"})
    TOKEN = resp.json()["data"]["token"]
    print("✅ 登录成功")

def api(method, path, data=None):
    h = {"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}"}
    r = requests.request(method, f"{BASE}{path}", headers=h, json=data)
    if r.status_code >= 400:
        print(f"  ❌ {r.status_code}: {r.text[:300]}")
        return None
    ct = r.headers.get("content-type", "")
    if "application/json" in ct:
        return r.json()
    return r.text

def random_gen(endpoint, book_id=None, extra=None):
    """Call a random generation endpoint. Returns parsed JSON or None."""
    path = f"/generation/random/{endpoint}"
    if book_id:
        path += f"/{book_id}"
    return api("POST", path, extra or {})

# ══════════════════════════════════════════════
# Step 1: Generate book idea & create book
# ══════════════════════════════════════════════
def step_create_book(genre_info):
    idea = random_gen("book-idea", extra={"genre": genre_info["genre"]})
    if not idea:
        return None

    title = idea.get("title", f"{genre_info['genre']}测试")
    core = idea.get("core_idea", "")
    mode = idea.get("creation_mode", "LINEAR")
    length = idea.get("target_length", "MEDIUM")
    one_sentence = idea.get("one_sentence", "")

    result = api("POST", "/books", {
        "title": title,
        "core_idea": core,
        "creation_mode": mode,
        "target_length": length,
        "length_type": length,
        "one_sentence": one_sentence,
    })
    if not result or "data" not in result:
        return None

    book = result["data"]
    print(f"\n📖 {book['title']} (id={book['id']})")
    print(f"   创意: {core[:60]}...")
    return book

# ══════════════════════════════════════════════
# Step 2: Generate & save world setting
# ══════════════════════════════════════════════
def step_world_setting(book):
    ws = random_gen("world-setting", book["id"])
    if not ws:
        print("   ⚠️ 世界观生成失败")
        return False

    result = api("PUT", f"/books/{book['id']}/world-setting", {
        "era": ws.get("era", ""),
        "geography": ws.get("geography", ""),
        "history_events": ws.get("history_events", ""),
        "politics": ws.get("politics", ""),
        "economy": ws.get("economy", ""),
        "culture": ws.get("culture", ""),
        "military": ws.get("military", ""),
        "core_rule_type": ws.get("core_rule_type", ""),
        "core_rule_summary": ws.get("core_rule_summary", ""),
    })
    if result:
        print(f"   🌍 世界观已保存 ({ws.get('era', '未知时代')})")
    return result is not None

# ══════════════════════════════════════════════
# Step 3: Generate & save outline tree
# ══════════════════════════════════════════════
def step_outline(book):
    outline = random_gen("outline", book["id"])
    if not outline:
        print("   ⚠️ 大纲生成失败")
        return 0, []

    volumes = outline.get("volumes", [])
    node_count = 0
    section_ids = []  # track leaf SECTION node IDs for content generation

    for vi, vol in enumerate(volumes):
        vol_title = vol.get("title", f"第{vi+1}卷")
        vn = api("POST", f"/books/{book['id']}/outline/node", {
            "node_type": "VOLUME",
            "title": vol_title,
            "sequence": vi,
            "writing_goal": vol.get("summary", ""),
        })
        if not vn or "data" not in vn:
            continue
        vol_node = vn["data"]
        node_count += 1

        for ci, ch in enumerate(vol.get("chapters", [])):
            ch_title = ch.get("title", f"第{ci+1}章")
            cn = api("POST", f"/books/{book['id']}/outline/node", {
                "node_type": "CHAPTER",
                "title": ch_title,
                "parent_id": vol_node["id"],
                "sequence": ci,
            })
            if not cn or "data" not in cn:
                continue
            ch_node = cn["data"]
            node_count += 1

            for si, sc in enumerate(ch.get("scenes", [])):
                sc_title = sc.get("title", f"第{si+1}节")
                sn = api("POST", f"/books/{book['id']}/outline/node", {
                    "node_type": "SCENE",
                    "title": sc_title,
                    "parent_id": ch_node["id"],
                    "sequence": si,
                })
                if sn and "data" in sn:
                    node_count += 1
                    section_ids.append(sn["data"]["id"])

    print(f"   📑 {node_count} 个大纲节点 ({len(section_ids)} 个可写节)")
    return node_count, section_ids

# ══════════════════════════════════════════════
# Step 4: Generate characters
# ══════════════════════════════════════════════
def step_characters(book, count=6):
    levels = ["L3", "L3", "L2", "L2", "L1", "L0"]
    created = 0
    for i in range(min(count, len(levels))):
        depth = levels[i]
        char = random_gen("character", book["id"], extra={"depth_level": depth})
        if not char:
            continue
        result = api("POST", f"/books/{book['id']}/characters", {
            "name": char.get("name", f"角色{i+1}"),
            "gender": char.get("gender", "男"),
            "age_description": char.get("age_description", ""),
            "appearance": char.get("appearance", ""),
            "origin": char.get("origin", ""),
            "identity": char.get("identity", ""),
            "depth_level": depth,
            "race": char.get("race", "人类"),
        })
        if result and "data" in result:
            created += 1
        time.sleep(1.0)
    print(f"   👤 {created} 个角色")
    return created

# ══════════════════════════════════════════════
# Step 5: Generate chapter content via SSE
# ══════════════════════════════════════════════
def step_chapter_content(book, section_ids, max_sections=3):
    """Generate AI content for a few sections via SSE streaming."""
    generated = 0
    for sid in section_ids[:max_sections]:
        print(f"   ✍️ 生成节 id={sid} 内容...", end=" ", flush=True)
        try:
            resp = requests.post(f"{BASE}/generation/continue", headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {TOKEN}",
            }, json={
                "book_id": book["id"],
                "context": f"《{book['title']}》\n{book.get('core_idea', '')}\n\n章节开始。",
                "instructions": "请从本章开头写起，要有环境描写和人物出场",
                "temperature": 1.2,
                "max_tokens": 500,
                "structure_id": sid,
            }, stream=True, timeout=120)

            content_parts = []
            current_event = ""
            for line in resp.iter_lines(decode_unicode=True):
                if not line:
                    current_event = ""
                    continue
                line = line.strip()
                if line.startswith("event:"):
                    current_event = line[6:].strip()
                elif line.startswith("data:"):
                    data = line[6:].strip()
                    if current_event == "error":
                        print(f"❌ {data}")
                        break
                    if current_event == "done":
                        break
                    if current_event == "chunk" or not current_event:
                        content_parts.append(data)

            if content_parts:
                generated += 1
                print(f"✅ {len(''.join(content_parts))} 字")
            else:
                print("⚠️ 空内容")

        except Exception as e:
            print(f"❌ {e}")
        time.sleep(2)

    print(f"   📝 已为 {generated} 个节生成正文")
    return generated

# ══════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════
login()

# Clean old books
print("🧹 清理旧数据...")
books_resp = api("GET", "/books")
if books_resp and books_resp.get("data"):
    for b in books_resp["data"]:
        api("DELETE", f"/books/{b['id']}")
        print(f"  删除: {b['title']} (id={b['id']})")
    time.sleep(1)

# Generate!
print(f"\n🚀 批量生成 {len(GENRES)} 本书: 世界观 → 大纲 → 角色 → 章节正文")
print("=" * 55)

all_books = []
for gi, g in enumerate(GENRES):
    print(f"\n[{gi+1}/{len(GENRES)}] {g['genre']}")

    book = step_create_book(g)
    if not book:
        continue
    all_books.append(book)
    time.sleep(1)

    step_world_setting(book)
    time.sleep(1)

    _, section_ids = step_outline(book)
    time.sleep(1)

    step_characters(book, count=6)

    if section_ids:
        step_chapter_content(book, section_ids, max_sections=2)

print("\n" + "=" * 55)
print(f"✅ 完成！生成 {len(all_books)} 本书:")
for b in all_books:
    print(f"   📖 {b['title']} (id={b['id']})")
