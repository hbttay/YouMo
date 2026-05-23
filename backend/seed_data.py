# -*- coding: utf-8 -*-
"""Seed data for YouMo dev database."""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"

def post(path, body, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data,
        headers={"Content-Type": "application/json; charset=utf-8"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:

def put(path, body, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data,
        headers={"Content-Type": "application/json; charset=utf-8"}, method="PUT")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code}: {body}")

# ── 1. Register ──
print("[1/7] Registering user...")
reg = post("/users/register", {"email": "writer@youmo.com", "password": "123456"})
print(f"  user id={reg['data']['id']}")

# ── 2. Login ──
print("[2/7] Logging in...")
login_res = post("/users/login", {"email": "writer@youmo.com", "password": "123456"})
token = login_res["data"]["token"]
print(f"  token={token[:40]}...")

# ── 3. Create book ──
print("[3/7] Creating book...")
book = post("/books", {
    "title": "星辰永不坠落",
    "theme": "科幻 / 冒险 / 人性",
    "core_idea": "一个失去记忆的宇航员在陌生星球上寻找回家之路的故事。",
    "one_sentence": "当星空不再是目的地，回家的路比宇宙更遥远。",
    "target_reader_profile": "25-40岁科幻爱好者",
    "violence_level": 4,
    "romance_level": 3,
    "politics_level": 2,
    "civility_level": 6,
    "creation_mode": "LINEAR",
    "target_length": "LONG",
    "estimated_words": 300000,
    "extra_attributes": json.dumps({
        "tasks": [
            {"id": "t1", "text": "完成第一章草稿", "done": True},
            {"id": "t2", "text": "修订主角人物弧光", "done": False},
            {"id": "t3", "text": "确定世界观核心规则", "done": False},
        ]
    }, ensure_ascii=False)
}, token)
book_id = book["data"]["id"]
print(f"  book id={book_id}, title={book['data']['title']}")

# ── 4. Create characters ──
print("[4/7] Creating characters...")
chars = [
    ("林寒", "男", "32岁，身高182cm，前舰长",
     "短发微卷，深邃的灰蓝色眼睛，身材精瘦有力。左臂有半条机械纹身——这是他唯一携带的「过去」。",
     "地球，国际航天联盟总部",
     "普罗米修斯号舰长，失忆的星际探险者"),
    ("艾莉西亚", "女", "28岁，身高165cm，工程师",
     "微卷的红棕色长发常扎成马尾，脸上有雀斑，明亮的绿眼睛里总带着好奇。",
     "矿业殖民地 Kepler-9",
     "殖民地出生的机械工程师，飞船维修专家"),
    ("泽洛斯", "男", "外观约40岁，实际年龄未知，硅基生命体",
     "身高约2米，半透明的晶体状躯体折射出彩虹色光芒。没有固定五官，但可变化出类人面孔。",
     "行星艾瑞达-4",
     "艾瑞达文明最后一位守护者，孤独守护300年，寻求与人类共存"),
]
for name, gender, age, appearance, origin, identity in chars:
    r = post(f"/books/{book_id}/characters", {
        "name": name,
        "gender": gender,
        "age_description": age,
        "appearance": appearance,
        "origin": origin,
        "identity": identity,
    }, token)
    print(f"  {name}: id={r['data']['id']}")

# ── 5. Create outline ──
print("[5/7] Creating outline...")

def add_node(title, node_type, parent_id, seq):
    r = post(f"/books/{book_id}/outline/node", {
        "title": title,
        "node_type": node_type,
        "parent_id": parent_id,
        "sequence": seq,
    }, token)
    return r["data"]["id"]

vol1_id = add_node("第一卷：坠落", "VOLUME", None, 0)
print(f"  Volume 1: id={vol1_id}")

ch1_id = add_node("第一章：醒来", "CHAPTER", vol1_id, 0)
ch2_id = add_node("第二章：废墟之语", "CHAPTER", vol1_id, 1)
ch3_id = add_node("第三章：归途无期", "CHAPTER", vol1_id, 2)
print(f"  Chapters: {ch1_id}, {ch2_id}, {ch3_id}")

# Scenes for Chapter 1
s1a = add_node("第一节：警报", "SCENE", ch1_id, 0)
s1b = add_node("第二节：空白", "SCENE", ch1_id, 1)
s1c = add_node("第三节：第一次足迹", "SCENE", ch1_id, 2)
print(f"  Ch1 scenes: {s1a}, {s1b}, {s1c}")

# Scenes for Chapter 2
s2a = add_node("第一节：废墟回音", "SCENE", ch2_id, 0)
s2b = add_node("第二节：泽洛斯的觉醒", "SCENE", ch2_id, 1)
print(f"  Ch2 scenes: {s2a}, {s2b}")

# Scenes for Chapter 3
s3a = add_node("第一节：灯塔", "SCENE", ch3_id, 0)
s3b = add_node("第二节：抉择", "SCENE", ch3_id, 1)
print(f"  Ch3 scenes: {s3a}, {s3b}")

# ── 6. Write chapter content ──
print("[6/7] Writing chapter content...")

def write_content(sid, text, status="DRAFT"):
    clean = text.replace(" ", "").replace("\n", "")
    r = post(f"/chapters/{sid}/content", {
        "content": text,
        "source": "USER_EDITED",
        "storage_type": "FULL",
        "word_count": len(clean),
        "status": status,
    }, token)
    return r["data"]["status"]

# Chapter 1 intro
write_content(ch1_id, """飞船坠毁已经过去七十二小时。林寒从医疗舱中醒来，记忆像被打碎的镜子，只留下模糊的残片。

窗外是一片陌生的紫色天空。两颗卫星同时升起，把大地染成银白色。

他不知道自己是谁，不知道为什么会在这里。但当他的目光落在控制台的闪烁红点上时，一种直觉告诉他——他属于别处。

他要回家。只是，回家的路，比他想象的要漫长得多。""")

# Chapter 1, Scene 1
write_content(s1a, """警报声撕裂了飞船的寂静。

林寒睁开眼时，首先看到的是头顶那盏闪烁的红灯。它每闪一次，就有一声低沉的蜂鸣从控制台方向传来。

"飞船受损严重，请立即撤离。"合成语音重复了不知多少遍，声音已经有些变调。

他试着动了动手指——能动。抬臂——正常。但当他想回忆自己的名字时，脑海里只有一片灰白的噪声。

那种感觉就像你在梦里知道自己是谁，但醒来后什么都抓不住。

他挣扎着从医疗舱中坐起，透明凝胶从身上滑落。医疗舱旁边的显示屏上仍然写着他的名字："林寒，舰长，授权码■■■■。"

授权码被抹掉了。

就像有人故意擦去了他身份中最重要的那一部分。""")

# Chapter 1, Scene 2
write_content(s1b, """林寒花了整整一天来习惯自己的名字。

他不知道"林寒"意味着什么——不知道它背后的荣誉、责任和承诺。他只知道，在那块闪烁的屏幕上，这个名字是他唯一的坐标。

飞船上还有四个幸存者。他们在另一间医疗舱中被唤醒，和他一样，什么都不记得。

"你记得我们为什么来这里吗？"一个红发的年轻女子问他。她看起来不到三十岁，眼睛里有种不服输的光。

"不记得。"林寒说。

"我是谁？"她又问。

林寒看了看她手腕上的ID手环："艾莉西亚。"

"好吧。"她苦笑，"至少有人知道。"

外面，紫色的夜幕开始降临这个星球。

林寒站在破损的飞船门口，第一次望向这片未知的大陆。某处深处，有个声音在说：你答应过要回去。""")

# Chapter 1, Scene 3
write_content(s1c, """第三天清晨，林寒迈出了离开飞船的第一步。

脚下的土地松软而干燥，像被烤过的细沙。空气中弥漫着一股焦糖般的甜味。他蹲下来，用手捻了一撮土——在这个距离上，他看到土壤中含有微小的晶体颗粒。

"这不是普通的沙。"艾莉西亚的声音从背后传来。她也跟了出来，手里拿着一个便携扫描仪。

"含硅量很高，而且——"她顿了顿，"有生命迹象痕迹。"

痕迹。是的。那些散布在紫色平原上、排列成某种图案的碎石，不可能是自然形成的。

"这里有人。"林寒说。

他抬起头，望向地平线的方向。在那里，隐隐约约立着一座方尖碑般的高塔。

那是回家的线索。

也是故事真正的起点。""")

print("  Chapter 1 content written (1 intro + 3 scenes)")

# ── 7. Save world setting ──
print("[7/7] Saving world setting...")
r = put(f"/books/{book_id}/world-setting", {
    "era": "近未来（公元2347年），人类已掌握星际航行技术，文明的触角延伸到距地球约200光年。",
    "geography": "行星「艾瑞达-4」位于天鹅座方向，直径约为地球1.2倍。紫色大气层是紫外线与大气氩化合物反应的产物。地表覆盖约40%紫色平原、25%晶体山脉、20%荧光海洋。",
    "history_events": json.dumps([
        "2315年：国际航天联盟成立",
        "2332年：第一批殖民地建立",
        "2340年：发现艾瑞达星系",
        "2345年：林寒被任命为普罗米修斯号舰长",
        "2347年：故事发生",
    ], ensure_ascii=False),
    "politics": "人类世界由国际航天联盟统一管理，但偏远殖民地渴望更多自治权。殖民地与地球关系日趋紧张。",
    "economy": "以资源开采和贸易为核心。稀有元素「阿尔法矿」是最有价值的资源，也是故事核心驱动力之一。",
    "culture": "宇航员是最受尊敬的职业。殖民地居民形成了坚韧务实、不迷信权威的边疆文化。",
    "military": "ISA安全部队「星盾」主要负责打击星际海盗和保护贸易路线。艾瑞达人曾拥有极为先进的军事科技，但大多已失传。",
    "core_rule_type": "硬科幻",
    "core_rule_summary": "1. 超光速不存在——星际航行需要数月到数年\n2. 通讯不能超过光速——殖民地间信息延迟以年计\n3. 硅基生命在物理学上是可能的\n4. 记忆可以被移除但不能被真正销毁——核心伏笔",
}, token)
print(f"  code={r['code']}")

print()
print("Seed complete!")
print(f"  Login:  writer@youmo.com / 123456")
print(f"  Book:   {book['data']['title']} (id={book_id})")
print(f"  Chars:  3")
print(f"  Outline: 1 volume -> 3 chapters -> 7 scenes")
print(f"  Content: chapter 1 fully written")
print(f"  World:   configured")
