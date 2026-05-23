# -*- coding: utf-8 -*-
"""Update characters with Chinese content."""
import json, urllib.request

BASE = "http://localhost:8080/api"

def put(path, body, token):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data,
        headers={"Content-Type": "application/json; charset=utf-8"}, method="PUT")
    req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def post(path, body, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data,
        headers={"Content-Type": "application/json; charset=utf-8"})
    if token: req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

# Login
login = post("/users/login", {"email": "writer@youmo.com", "password": "123456"})
token = login["data"]["token"]

chars = [
    (1, {
        "name": "林寒",
        "gender": "男",
        "age_description": "32岁，身高182cm",
        "appearance": "短发微卷，深邃的灰蓝色眼睛，身材精瘦有力。左臂有半条机械纹身——这是他唯一携带的「过去」。",
        "origin": "地球，国际航天联盟总部",
        "identity": "普罗米修斯号舰长，失忆的星际探险者",
    }),
    (2, {
        "name": "艾莉西亚",
        "gender": "女",
        "age_description": "28岁，身高165cm",
        "appearance": "微卷的红棕色长发常扎成马尾，脸上有雀斑，明亮的绿眼睛里总带着好奇。",
        "origin": "矿业殖民地 Kepler-9",
        "identity": "殖民地出生的机械工程师，飞船维修专家",
    }),
    (3, {
        "name": "泽洛斯",
        "gender": "男",
        "age_description": "外观约40岁，实际年龄未知",
        "appearance": "身高约2米，半透明的晶体状躯体折射出彩虹色光芒。没有固定五官，但可变化出类人面孔。",
        "origin": "行星艾瑞达-4",
        "identity": "艾瑞达文明最后一位守护者，硅基生命体",
    }),
]

for cid, data in chars:
    r = put(f"/books/1/characters/{cid}", data, token)
    print(f"  {data['name']}: id={cid}, code={r['code']}")

print("Done!")
