"""Seed realistic demo data into YouMo database via API."""
import requests
import json
import sys

BASE = "http://localhost:8080/api"
TOKEN = None

def api(method, path, data=None):
    url = f"{BASE}{path}"
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    if method == "GET":
        r = requests.get(url, headers=headers)
    elif method == "POST":
        r = requests.post(url, headers=headers, json=data)
    elif method == "PUT":
        r = requests.put(url, headers=headers, json=data)
    else:
        raise ValueError(method)
    if r.status_code >= 400:
        print(f"  ERROR {r.status_code}: {r.text[:200]}")
        return None
    return r.json().get("data", r.json())

def login():
    global TOKEN
    # Register if needed
    r = requests.post(f"{BASE}/users/register", json={
        "email": "writer@youmo.com", "username": "writer", "password": "writer123"
    })
    resp = requests.post(f"{BASE}/users/login", json={
        "account": "writer", "password": "writer123"
    })
    data = resp.json()["data"]
    TOKEN = data["token"]
    print(f"登录成功: {data['user']['username']}")

def create_book(title, core_idea, length_type, creation_mode):
    r = api("POST", "/books", {
        "title": title,
        "core_idea": core_idea,
        "length_type": length_type,
        "creation_mode": creation_mode,
    })
    if r:
        print(f"  书籍: {title} (id={r['id']})")
    return r

def create_character(book_id, name, gender, depth_level, identity, origin, age_desc, appearance, race=None):
    data = {
        "book_id": book_id, "name": name, "gender": gender,
        "depth_level": depth_level, "identity": identity,
        "origin": origin, "age_description": age_desc, "appearance": appearance,
    }
    if race:
        data["race"] = race
    return api("POST", f"/books/{book_id}/characters", data)

def save_world(book_id, data):
    return api("PUT", f"/books/{book_id}/world-setting", data)

def create_outline_node(book_id, data):
    return api("POST", f"/books/{book_id}/outline/node", data)

def create_foreshadowing(book_id, description, fs_type, importance, status, target_entity=""):
    return api("POST", f"/books/{book_id}/foreshadowings", {
        "description": description,
        "foreshadowing_type": fs_type,
        "importance": importance,
        "status": status,
        "target_entity": target_entity,
    })


# ═══════════════════════════════════════
# MAIN
# ═══════════════════════════════════════
login()

# ── BOOK 1: 仙途 (Xianxia) ──
print("\n=== 第一本：仙途 ===")
b1 = create_book(
    "仙途",
    "凡人少年林砚因一枚神秘玉简觉醒上古剑仙传承，在弱肉强食的修真界中步步登仙。从宗门底层杂役到万剑归宗，斩妖除魔、逆天改命。然而大道尽头，他发现真正的敌人并非外魔，而是万年前那场导致天地灵气衰竭的「天道崩碎」——自己正是天道碎片所化的棋子。",
    "LONG", "LINEAR"
)
if not b1: sys.exit(1)
b1_id = b1["id"]

# Characters
print("  创建角色...")
chars_1 = [
    ("林砚", "男", "L3", "剑修 / 天剑宗首席", "青云镇猎户之子，父母早亡", "十八岁，剑眉星目，身形清瘦",
     "黑发束冠，着青色道袍，背负三尺青锋「断念」。右手虎口有剑气刻痕，瞳孔深处隐现金色剑芒。", "人族"),
    ("苏晚晴", "女", "L2", "丹霞谷传人 / 炼丹师", "丹霞谷谷主之女，自幼研习药道", "十七岁，容貌清丽，气质温婉",
     "身着淡紫色长裙，腰间系丹炉配饰。左手腕有一串灵草编织的手环，发间簪花暗藏解毒丹。", "人族"),
    ("楚渊", "男", "L2", "魔教少主 / 双面卧底", "天魔教教主私生子，被安插进天剑宗", "十九岁，俊美阴郁，左眼下方有暗红魔纹",
     "常着玄黑衣袍，指尖缠绕一缕黑气。战斗时魔纹蔓延至半张脸，瞳孔变为血红。", "人族"),
    ("云鹤真人", "男", "L1", "天剑宗宗主", "散修出身，百年前以一己之力开创天剑宗", "外貌约四十岁，实际三百余岁",
     "白发如雪，面容却如中年。常着一袭白色道袍，手持拂尘，仙风道骨。", "人族"),
    ("白璃", "女", "L1", "九尾天狐一族末裔", "妖族没落贵族，被林砚所救后追随左右", "外貌十六岁，实际三百岁",
     "银白长发及腰，琥珀色竖瞳，头顶一对毛茸茸的白色狐耳。身后九条尾巴可收放。", "妖族"),
    ("古尘", "男", "L0", "天剑宗藏经阁守阁人", "曾是天剑宗天才剑修，渡劫失败后隐退守阁", "外貌六旬，灰袍素衣",
     "佝偻老者，右腿微瘸。总是抱着一本残破古卷在阁前打盹，无人知其真实修为。", "人族"),
    ("墨夭", "女", "L0", "散修 / 阵法天才", "阵道世家遗孤，以卖阵图为生", "十五岁，娇小玲珑",
     "扎着双马尾，脸上常有墨渍。腰间挂满各种阵盘，说话快言快语。", "人族"),
]
for args in chars_1:
    create_character(b1_id, *args)

# World setting
print("  设定世界观...")
save_world(b1_id, {
    "era": "九州大陆，灵气复苏后的第三纪元。上古万族大战导致天地崩碎，灵气稀薄，修真界进入末法时代。各大宗门退守洞天福地，凡人世界与修真界隔绝。",
    "geography": "九州大陆分为上三州（灵州、剑州、云州）和下六州。天剑宗位于剑州天断山脉，丹霞谷坐落灵州东域。魔教总坛隐匿于极西荒漠之下，妖族余部退守北境十万大山。中州有一座通天古塔，据传是上古连接仙界的通道。",
    "history_events": "【上古纪元】万族共存，仙帝统御九州 → 【天道崩碎】万年前仙界通道断裂，灵气开始衰竭 → 【黑暗千年】妖族魔族争夺大陆霸权，人族沦为奴隶 → 【人皇崛起】三千年前人皇带领人族逆袭，建立修真宗门体系 → 【末法时代】近千年灵气持续衰减，元婴期以上修士几乎绝迹",
    "politics": "宗门林立、强者为尊。上三州由三大宗门联合统治（天剑宗、丹霞谷、万象阁）。散修联盟和猎妖公会代表中小势力。中央修真议会名义管辖，实则三大宗门暗中制衡。魔教虽被镇压但暗中渗透各宗门。",
    "economy": "灵石为通用货币，分下品/中品/上品/极品四等。宗门以丹药、法器、阵法作为主要贸易品。凡人世界使用金银铜钱。散修多靠接取猎妖任务、贩卖灵材维生。黑市流通禁术秘籍和魔道丹药。",
    "culture": "修真界重师承、论辈分、敬强者。每十年举办九州大比，各宗门天骄争辉。剑修崇尚「一剑破万法」，丹修炼器各有传承。凡人对修真者既敬畏又向往，民间流传大量仙人传说。",
    "military": "天剑宗设有剑堂、执法堂、外事堂三大武力机构。整体修真界战力以修士境界划分：炼气→筑基→金丹→元婴→化神→合体→大乘→渡劫。当前最强战力为化神期。魔教暗中培养血煞军团，妖族有天赋神通。",
    "core_rule_type": "修仙等级体系",
    "core_rule_summary": "修炼境界：炼气 → 筑基 → 金丹 → 元婴 → 化神（每一大境界分初期/中期/后期/圆满）。灵根决定修炼天赋（五行灵根+变异灵根）。剑气分为九重，剑意有杀意/守护/寂灭三种。天道碎片共有九枚，集齐可重开仙界通道。但每用一次碎片，宿主的寿命就会大幅衰减。"
})

# Outline
print("  创建大纲...")
vol1 = create_outline_node(b1_id, {"title": "卷一：凡尘觉醒", "node_type": "VOLUME", "parent_id": None, "sequence": 0})
if vol1:
    create_outline_node(b1_id, {"title": "第1章 青云镇的猎户少年", "node_type": "CHAPTER", "parent_id": vol1["id"], "sequence": 0})
    create_outline_node(b1_id, {"title": "第2章 玉简中的剑鸣", "node_type": "CHAPTER", "parent_id": vol1["id"], "sequence": 1})
    create_outline_node(b1_id, {"title": "第3章 踏入修行之门", "node_type": "CHAPTER", "parent_id": vol1["id"], "sequence": 2})
    create_outline_node(b1_id, {"title": "第4章 天剑宗入门试炼", "node_type": "CHAPTER", "parent_id": vol1["id"], "sequence": 3})
    create_outline_node(b1_id, {"title": "第5章 杂役弟子的一天", "node_type": "CHAPTER", "parent_id": vol1["id"], "sequence": 4})

vol2 = create_outline_node(b1_id, {"title": "卷二：剑宗风云", "node_type": "VOLUME", "parent_id": None, "sequence": 1})
if vol2:
    create_outline_node(b1_id, {"title": "第6章 内门大比", "node_type": "CHAPTER", "parent_id": vol2["id"], "sequence": 0})
    create_outline_node(b1_id, {"title": "第7章 剑阁传承", "node_type": "CHAPTER", "parent_id": vol2["id"], "sequence": 1})
    create_outline_node(b1_id, {"title": "第8章 白璃的契约", "node_type": "CHAPTER", "parent_id": vol2["id"], "sequence": 2})
    create_outline_node(b1_id, {"title": "第9章 暗流涌动", "node_type": "CHAPTER", "parent_id": vol2["id"], "sequence": 3})
    create_outline_node(b1_id, {"title": "第10章 卧底的真面目", "node_type": "CHAPTER", "parent_id": vol2["id"], "sequence": 4})

vol3 = create_outline_node(b1_id, {"title": "卷三：天道碎片", "node_type": "VOLUME", "parent_id": None, "sequence": 2})
if vol3:
    create_outline_node(b1_id, {"title": "第11章 古塔之谜", "node_type": "CHAPTER", "parent_id": vol3["id"], "sequence": 0})
    create_outline_node(b1_id, {"title": "第12章 碎片的代价", "node_type": "CHAPTER", "parent_id": vol3["id"], "sequence": 1})
    create_outline_node(b1_id, {"title": "第13章 仙界之门", "node_type": "CHAPTER", "parent_id": vol3["id"], "sequence": 2})

# Foreshadowings
print("  创建伏笔...")
fs_1 = [
    ("林砚猎户父亲临终前交给他一枚残破玉简，说「这东西不属于这个世界」", "ITEM", "HIGH", "ACTIVE", "玉简"),
    ("云鹤真人初见林砚时瞳孔微缩，低语「终于来了」", "CHARACTER", "HIGH", "ACTIVE", "云鹤真人"),
    ("白璃提到「九尾天狐一族在千年前曾辅佐过一位剑仙」", "RELATIONSHIP", "MEDIUM", "ACTIVE", "白璃"),
    ("楚渊在卧底期间多次暗中帮助林砚，但每次事后都假装是巧合", "RELATIONSHIP", "MEDIUM", "ACTIVE", "楚渊"),
    ("万象阁从不参与任何争斗，却在暗中收集所有天道碎片的线索", "EVENT", "HIGH", "ACTIVE", "万象阁"),
    ("古尘守阁老人给林砚看了一本「无名剑谱」，书中剑招与玉简中的完全一致", "ITEM", "HIGH", "RECYCLED", "古尘"),
    ("苏晚晴的丹炉底部刻有一行古字：「以命为引，可炼仙丹」", "ITEM", "MEDIUM", "ACTIVE", "苏晚晴"),
    ("中州古塔每当月圆之夜会发出微光，光的方向始终指向天剑宗后山", "EVENT", "MEDIUM", "ACTIVE", "古塔"),
]
for args in fs_1:
    create_foreshadowing(b1_id, *args)


# ── BOOK 2: 星海迷途 (Sci-fi) ──
print("\n=== 第二本：星海迷途 ===")
b2 = create_book(
    "星海迷途",
    "公元2287年，人类建立了横跨猎户臂的星际联邦。深空勘探官陆辰在一次例行任务中，于一颗死寂星球上发现了一艘百万年前的未知文明方舟。方舟苏醒的那一刻，联邦高层、星际财阀、地外势力三方角逐的暗战被引爆——而方舟搭载的AI只是一句话：「你们不是我们要等的人。」",
    "MEDIUM", "LINEAR"
)
if not b2: sys.exit(1)
b2_id = b2["id"]

print("  创建角色...")
chars_2 = [
    ("陆辰", "男", "L3", "联邦深空勘探官 / 上尉", "出生于火星殖民地，父亲是矿工，母亲是小学教师", "三十二岁，面容坚毅，左眉有一道旧伤疤",
     "短发干练，常穿联邦深空勘探署的深蓝色连体制服。左手前臂植入多功能数据终端。", "人类"),
    ("艾琳·冯", "女", "L2", "联邦科学院首席考古语言学家", "月球贵族世家出身，精通十二种地外文明语言", "二十八岁，金发碧眼，戴着智能数据分析眼镜",
     "总是穿着实验室白大褂，口袋里插满了各种扫描探头。耳垂上的珍珠耳环其实是微型量子计算机。", "人类"),
    ("K-7", "男", "L2", "生化人 / 方舟守护者", "方舟文明用生物科技创造的守护者，沉睡百万年", "外貌二十五岁，银色短发，瞳孔是淡金色的数据流",
     "体型与人类无异，但皮肤下隐约可见纳米电路。说话时瞳孔中的金色数据流会加速旋转。", "生化人"),
    ("赵朔", "男", "L1", "联邦安全局特派员", "地球本土势力安插在勘探队的监视者", "三十五岁，国字脸，眼神锐利",
     "西装革履，从不穿军装。右手无名指戴着联邦安全局的暗纹戒指，是身份的象征也是微型武器。", "人类"),
    ("索菲亚·李", "女", "L1", "科尔森星际财阀执行副总裁", "财阀继承人，以商业手段收集外星科技", "三十七岁，东方与拉美混血，气场凌厉",
     "高定西装裙，左手戴着方舟科技逆向工程的手环。笑容迷人但从不真笑，谈判桌上从无败绩。", "人类"),
    ("老杰克", "男", "L0", "深空勘探舰「曙光号」轮机长", "在太空漂泊四十年的老太空人", "六十二岁，满脸皱纹，右腿是机械假肢",
     "永远叼着一根不点着的雪茄，穿旧皮夹克。机械假肢是他自己用报废零件改装的，走起来咔咔作响。", "人类"),
]
for args in chars_2:
    create_character(b2_id, *args)

print("  设定世界观...")
save_world(b2_id, {
    "era": "公元2287年，人类进入星际联邦时代。曲速引擎使跨恒星系旅行成为日常，人类已殖民37个星系。地外文明的存在已在百年前被确认，但至今只发现了遗迹和残骸——从未遇到过活的外星智慧生命。",
    "geography": "星际联邦版图横跨猎户臂，以太阳系为中心向外辐射。核心区（Sol-α）是政治经济中心，边疆区（Rim-7）是深空勘探的前沿。火星是第二大人口星球，月球是贵族居住地。方舟发现位置：猎户臂边缘的NGC-2244星区，一颗被命名为「回声」的死寂行星。",
    "history_events": "【2050年代】第三次世界大战，地球生态崩溃 → 【2100年】曲速引擎突破，第一批火星殖民地建立 → 【2157年】首次发现地外文明遗迹（天狼星系） → 【2200年】星际联邦成立 → 【2245年】科尔森财阀垄断外星科技逆向工程 → 【2287年】发现活的外星方舟",
    "politics": "联邦议会（地球本土派 vs 殖民地派）、联邦军方、安全局三方制衡。科尔森财阀以经济控制影响力渗透议会。存在地下组织「开放派」主张完全公开方舟技术给平民。联邦总统是名义元首但实权分散。",
    "economy": "联邦信用点（FCP）为统一货币。科尔森财阀控制星际贸易的60%。地外科技专利是最值钱的资产。边疆区以采矿和勘探为主要经济来源。黑市上流通的外星文物价值连城。",
    "culture": "核心区居民生活优渥，享受高度自动化的生活。边疆殖民者吃苦耐劳，崇尚开拓精神。地外文明狂热（Xeno-mania）是一种流行文化现象，年轻人收集外星文物仿制品。联邦官方立场：地外文明为「已消亡的远古文明，无现实威胁」。",
    "military": "联邦太空舰队拥有十二支主力舰队。旗舰「联邦号」是泰坦级星际战列舰。安全局独立拥有情报网络和特种部队。科尔森财阀有私人安保公司「科尔森防卫」，兵力约等于一支小型舰队。",
    "core_rule_type": "科技体系",
    "core_rule_summary": "科技等级L0-L5：L0原始文明 → L1行星文明 → L2恒星系文明 → L3曲速文明（人类当前） → L4维度文明（方舟建造者） → L5神级文明。曲速引擎分9级，当前人类掌握5级。地外科技「逆向适配度」衡量人类能否安全使用：低于30%为危险，50%以上可尝试复制。方舟AI的指令遵循「造物主协议」：未经认证的物种不得获取核心技术。"
})

print("  创建大纲...")
v1 = create_outline_node(b2_id, {"title": "第一部：回声", "node_type": "VOLUME", "parent_id": None, "sequence": 0})
if v1:
    for i, ch in enumerate(["深空中的异常信号", "死寂行星的方舟", "苏醒的守护者", "联邦的密令", "不是你们", "科尔森的触角"]):
        create_outline_node(b2_id, {"title": f"第{i+1}章 {ch}", "node_type": "CHAPTER", "parent_id": v1["id"], "sequence": i})

v2 = create_outline_node(b2_id, {"title": "第二部：三方游戏", "node_type": "VOLUME", "parent_id": None, "sequence": 1})
if v2:
    for i, ch in enumerate(["安全局介入", "方舟的第一个秘密", "月球的交易", "生化人之问", "失控的逆向工程", "赵朔的选择"]):
        create_outline_node(b2_id, {"title": f"第{i+7}章 {ch}", "node_type": "CHAPTER", "parent_id": v2["id"], "sequence": i})

print("  创建伏笔...")
for args in [
    ("方舟AI苏醒后说的第一句话是「你们不是我们要等的人」——但没说等的是谁", "EVENT", "HIGH", "ACTIVE", "方舟AI"),
    ("陆辰的DNA扫描显示他有0.3%的基因与方舟建造者匹配", "CHARACTER", "HIGH", "ACTIVE", "陆辰"),
    ("K-7在休眠舱中梦到了百万年前方舟建造者的最后一幕：他们不是灭绝了，而是「升维」了", "PLOT_TWIST", "HIGH", "ACTIVE", "K-7"),
    ("科尔森财阀实际已秘密掌握一套来自另一艘沉没方舟的动力系统，正在火星地下测试", "ITEM", "MEDIUM", "ACTIVE", "科尔森财阀"),
    ("联邦安全局在十年前就发现了另一处方舟文明的信号，但选择隐瞒并以「军事禁区」封锁了整片星区", "EVENT", "HIGH", "ACTIVE", "安全局"),
    ("赵朔在执行监视任务中逐渐发现联邦高层有内鬼，真正的威胁来自内部", "CHARACTER", "MEDIUM", "ACTIVE", "赵朔"),
    ("艾琳破译方舟文字时发现「等待」这个词重复了整整一千次，占据了一面墙", "ITEM", "MEDIUM", "ACTIVE", "艾琳"),
    ("老杰克的机械腿偶然接触方舟金属后，金属表面浮现出一行未知文字", "ITEM", "MEDIUM", "RECYCLED", "老杰克"),
]:
    create_foreshadowing(b2_id, *args)


# ── BOOK 3: 长安秘录 (Historical Mystery) ──
print("\n=== 第三本：长安秘录 ===")
b3 = create_book(
    "长安秘录",
    "大唐开元十七年，长安城表面繁华之下暗潮涌动。大理寺新科丞沈惊墨因一起离奇的「牡丹花妖案」卷入一连串超自然谜案中。每破一案，真相都指向十二年前那场被朝廷封存禁谈的「玄武门异变」。而他的搭档——那位来历不明的女仵作苏念——似乎比所有案件都更接近那个秘密的核心。",
    "MEDIUM", "LINEAR"
)
if not b3: sys.exit(1)
b3_id = b3["id"]

print("  创建角色...")
chars_3 = [
    ("沈惊墨", "男", "L3", "大理寺丞 / 探案天才", "长安落魄书香门第之后，父亲因卷入十二年前的玄武门异变而被革职流放", "二十四岁，眉清目秀，常带着一丝若有若无的笑",
     "身着大理寺深绯色官袍，腰间悬铜鱼袋。左手拇指戴有一枚墨玉扳指——那是父亲留给他的唯一遗物。", "人族"),
    ("苏念", "女", "L2", "大理寺仵作 / 神秘医者", "来历不明，自称来自西域碎叶城，但口音和习俗皆非西域", "二十一岁，容貌清冷如霜，右眼角有一颗泪痣",
     "常着素白色胡服短打，腰间挂一套银质仵作器具。颈间挂着一枚半截的龟钮铜印——另一半不知所踪。", "人族"),
    ("李玄素", "男", "L1", "金吾卫中郎将 / 武状元", "陇西李氏旁支，以军功入仕", "二十七岁，身形魁梧，剑术超群",
     "着金吾卫明光铠，腰间一柄环首横刀从不离身。说话声如洪钟，但在苏念面前会不自觉地压低声音。", "人族"),
    ("慧明禅师", "男", "L1", "大慈恩寺住持 / 前朝国师之徒", "曾为先帝座上宾，十二年前突然闭关于大慈恩寺后山精舍", "七十余岁，白眉垂肩，目如深潭",
     "身披褪色袈裟，手持一串108颗紫檀佛珠——其中三颗为白玉所制。", "人族"),
    ("裴雨棠", "女", "L1", "长安第一歌姬 / 情报贩子", "教坊司出身，实则是地下情报网「风吟阁」的实际掌控者", "二十岁，风华绝代",
     "云鬓凤钗，一袭石榴红裙。指尖弹奏的琵琶曲之下，藏着长安城所有的秘密。", "人族"),
    ("张直", "男", "L0", "大理寺捕快 / 沈惊墨心腹", "草根出身，被沈惊墨从市井提拔", "十九岁，一脸憨厚实则心细如发",
     "灰布衙役服，脚踩麻鞋。腰间别着一根铁尺，口袋里永远装着半包炒豆子——那是他跟踪监视时的干粮。", "人族"),
    ("夜七", "男", "L0", "风吟阁暗桩 / 前禁军暗杀营", "禁军暗杀营「夜不收」的幸存者，被裴雨棠所救", "约三十岁，面容平平无奇，混入人群即消失",
     "一身不起眼的灰衣，但袖中藏着七把薄如蝉翼的飞刀。从不说话——据说他的舌头在禁军时被割了。", "人族"),
]
for args in chars_3:
    create_character(b3_id, *args)

print("  设定世界观...")
save_world(b3_id, {
    "era": "大唐开元十七年（公元729年），唐玄宗李隆基在位。此时为开元盛世顶峰，长安城人口逾百万，万邦来朝。但表象之下，武周旧臣残余势力、世家门阀、佛道之争、西域势力暗流涌动。十二年前（开元五年）发生的「玄武门异变」被朝廷全面封口，所有相关记录被销毁。",
    "geography": "长安城（西京），以朱雀大街为中轴线，分东西两市、一百零八坊。皇城和宫城在北，大明宫居中。本故事主要场景：大理寺（皇城内）、西市、平康坊（歌姬坊）、大慈恩寺（晋昌坊）、曲江池。城外地标：终南山、骊山、渭水。",
    "history_events": "【开元元年】李隆基发动先天政变，诛太平公主，改元开元 → 【开元五年】玄武门异变（全封存） → 【开元八年】姚崇罢相，宋璟继任 → 【开元十五年】慧明禅师突然闭关 → 【开元十七年】牡丹花妖案发生，即本作开始",
    "politics": "宰相张说与宇文融两派角力。世家门阀（崔卢李郑王）与科举新贵对立。宦官势力开始抬头但尚未坐大（高力士已为玄宗心腹）。金吾卫与禁军职责交叉常有摩擦。大理寺名义上独立办案但处处受制于朝中势力。",
    "economy": "开元通宝为流通货币，绢帛亦作等价物。西市是国际贸易中心，胡商云集（波斯、大食、粟特）。长安地价高昂，西市一间铺面年租可抵寻常人家十年开销。盐铁仍是朝廷专营。飞钱（早期汇票）在商人中开始使用。",
    "culture": "唐诗盛世：王维、孟浩然、李白（时年28岁初入长安）、杜甫（尚为少年）。胡风盛行：胡服、胡食（胡饼、葡萄酒）、胡乐（琵琶、羌笛）大受欢迎。佛教与道教并重，大慈恩寺是佛教中心。科举取士形成文人阶层。",
    "military": "府兵制已名存实亡，逐步转为募兵制。禁军六军拱卫京师。金吾卫掌宫中和京城巡夜。边镇节度使势力开始膨胀（范阳、河西、陇右等）。",
    "core_rule_type": "低魔悬疑设定",
    "core_rule_summary": "「妖异」并非真正的超自然，而是被古人以「妖怪」话语体系解释的：1）特殊药物致幻（迷迭香+曼陀罗）2）精巧机械装置（墨家机关术残篇）3）罕见的自然现象 4）人为制造的假象。但有一个贯穿全书的悬念：十二年前玄武门异变中发生过一件无法用以上任何一条解释的事。那一晚，30名禁军同时看到了「某个东西」，全部当场失忆。"
})

print("  创建大纲...")
v1b3 = create_outline_node(b3_id, {"title": "卷一：牡丹案", "node_type": "VOLUME", "parent_id": None, "sequence": 0})
if v1b3:
    for i, ch in enumerate(["牡丹花下尸", "西域奇香", "风吟阁的线索", "裴娘子的条件", "第一块铜印"]):
        create_outline_node(b3_id, {"title": f"第{i+1}章 {ch}", "node_type": "CHAPTER", "parent_id": v1b3["id"], "sequence": i})

v2b3 = create_outline_node(b3_id, {"title": "卷二：佛骨谜", "node_type": "VOLUME", "parent_id": None, "sequence": 1})
if v2b3:
    for i, ch in enumerate(["大慈恩寺的密室", "慧明禅师的三颗白玉珠", "暗杀营的幸存者", "玄武门的封印", "十二年前的真相"]):
        create_outline_node(b3_id, {"title": f"第{i+6}章 {ch}", "node_type": "CHAPTER", "parent_id": v2b3["id"], "sequence": i})

print("  创建伏笔...")
for args in [
    ("苏念的龟钮铜印只有半截——另外半截在十二年前玄武门异变的现场被发现，现封存在大理寺秘库", "ITEM", "HIGH", "ACTIVE", "苏念"),
    ("慧明禅师的三颗白玉佛珠中藏有三段不同的回忆——分别是三个幸存者对玄武门那一夜的描述，但三段描述相互矛盾", "ITEM", "HIGH", "ACTIVE", "慧明禅师"),
    ("牡丹花妖案的受害人全部在死前七天去过同一家西域香料铺", "EVENT", "MEDIUM", "RECYCLED", "香料铺"),
    ("夜七的舌头不是被割掉的，而是他自己咬断的——为了不泄露玄武门当晚他看到的秘密", "CHARACTER", "HIGH", "ACTIVE", "夜七"),
    ("沈惊墨的父亲在被流放前曾是大理寺最出色的仵作，他的最后一案正是玄武门异变", "RELATIONSHIP", "HIGH", "ACTIVE", "沈惊墨"),
    ("裴雨棠暗中资助沈惊墨查案，但她每提供一条线索，沈惊墨就欠她一个「人情」——这债迟早要还", "RELATIONSHIP", "MEDIUM", "ACTIVE", "裴雨棠"),
    ("长安城中有一家「无名当铺」，当品不是财宝，而是「记忆」——有人用记忆交换愿望", "PLOT_TWIST", "MEDIUM", "ACTIVE", "无名当铺"),
]:
    create_foreshadowing(b3_id, *args)


print("\n✅ 数据生成完成！")
print(f"  3 本书 → {b1['title']}, {b2['title']}, {b3['title']}")
print(f"  共 20 个角色、3 个世界观设定、3 套大纲、23 条伏笔")
