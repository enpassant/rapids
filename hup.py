import json
import re
import requests
from bs4 import BeautifulSoup
import uuid6
from kafka import KafkaProducer
import json
import time
from markdownify import markdownify as md
from datetime import datetime, UTC

# fileName = "hup-130291.html"
# fileName = "hup-20260604.html"
# fileName = "hup-154515.html"
fileName = "hup-141565.html"

token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDcyNjg4MjQ2NjA3OTg5NjA4MjYiLCJleHAiOjE3ODE3ODAzMzcsImp0aSI6IjcyZjhhODc4LThhZjktNDZhMC04Zjc4LTA3NjFlNTkxOTc5ZiIsIm5hbWUiOiJGZXJlbmMgS8OhbG3DoW4iLCJyb2xlcyI6WyJ1c2VyIl19ICA=.w39u3J+A7UCSzdcOIdVkWlSS7Wn4+38X4jAreHns2Hs="
valid_to = 1781780337

id_dict = {}
blog_id = None

# Producer létrehozása JSON szerializálóval
producer = KafkaProducer(
    bootstrap_servers=['localhost:9094'],
    # A kulcsot egyszerű stringként kezeljük és UTF-8 bájtokká alakítjuk
    key_serializer=lambda k: k.encode('utf-8'),
    # Automatikusan bájtokká és JSON formátummá alakítja a Python szótárakat
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

def send_kafka(topic, key, value):
    future = producer.send(topic, key=key, value=value)
    try:
        # Megvárjuk, amíg a Kafka visszaigazolja a sikeres fogadást (szinkron blokkolás)
        record_metadata = future.get(timeout=10)
        print(f"Üzenet elküldve! Topic: {record_metadata.topic}, Partíció: {record_metadata.partition}")
    except Exception as e:
        print(f"Hiba történt a küldés során: {e}")

def parse_comment(article, processed_count):
    """Kinyeri a komment adatait az új HUP struktúrából szövegalapú regex-szel."""
    # 1. Komment ID kinyerése
    comment_id_attr = article.get("id", "")
    comment_id = (
        comment_id_attr.replace("comment-", "") if comment_id_attr else None
    )
    if not comment_id:
        comment_id = f"temp_{processed_count}"

    id_dict[comment_id] = uuid6.uuid7()

    # 2. Szülő ID kinyerése a horgony linkekből
    parent_id = None
    parent_uuid = None
    for link in article.find_all("a"):
        href = link.get("href", "")
        if (
            "Szülő" in link.get_text() or "szülő" in link.get_text()
        ) and "#comment-" in href:
            parent_id = href.split("#comment-")[-1]
            parent_uuid = id_dict[parent_id]
            break

    # 3. Szabványos UTC dátum kinyerése az attribútumból
    utc_datetime_str = ""

    try:
        timestamp_attr = article.div.footer.mark.get("data-comment-timestamp")
    except Exception:
        return None

    if timestamp_attr:
        try:
            # Az attribútum értékét egésszé alakítjuk, majd UTC datetime-má konvertáljuk
            ts = int(timestamp_attr)
            dt_utc = datetime.fromtimestamp(ts, UTC)
            # ISO 8601 formátumra alakítjuk 'Z' (Zulu/UTC) jelöléssel a végén
            utc_datetime_str = dt_utc.strftime("%Y-%m-%dT%H:%M:%SZ")
        except (ValueError, TypeError):
            return None
    else:
        return None

    # 4. Felhasználónév kinyerése (az osztály alapú keresés most már stabilan működik)
    username = ""
    author_link = article.div.footer.p.span
    if author_link:
        username = author_link.get_text(strip=True)

    # 5. HTML tartalom kinyerése
    content_div = article.find("div", class_="content")
    content_html = str(content_div.div) if content_div else ""

    return {
        "id": comment_id,
        "uuid": id_dict[comment_id],
        "parent_id": parent_id,
        "parent_uuid": parent_uuid,
        "username": username,
        "datetime": utc_datetime_str,
        "content": content_html,
        "children": [],
    }

def downloadUrl(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    print("Oldal letöltése...")
    response = requests.get(url, headers=headers)
    if response.status_code != 200:
        print(f"Hiba az oldal letöltésekor: {response.status_code}")
        return ""
    return response.text

def readFile(fileName):
    with open(fileName, 'r', encoding='utf-8') as fajl:
        return fajl.read()

def createBlog(title, content, user_id, datetime):
    return {
        '_t': 'CreateBlog',
        'title': title,
        'content': content,
        'datetime': datetime,
        "loggedIn" : {
            "_t" : "LoggedIn",
            "userId" : user_id,
            "userName" : user_id,
            "token" : token,
            "validTo" : valid_to,
            "created" : 0
        }
    }

def addComment(comment_id, content, user_id, datetime):
    return {
        '_t': 'AddComment',
        'id': comment_id,
        'content': content,
        'datetime': datetime,
        "loggedIn" : {
            "_t" : "LoggedIn",
            "userId" : user_id,
            "userName" : user_id,
            "token" : token,
            "validTo" : valid_to,
            "created" : 0
        }
    }

def replyComment(comment_id, parent_id, content, user_id, datetime):
    return {
        '_t': 'ReplyComment',
        'id': comment_id,
        'parentId': parent_id,
        'content': content,
        'datetime': datetime,
        "loggedIn" : {
            "_t" : "LoggedIn",
            "userId" : user_id,
            "userName" : user_id,
            "token" : token,
            "validTo" : valid_to,
            "created" : 0
        }
    }


def scrape_hup_comments(text):
    soup = BeautifulSoup(text, "html.parser")

    title = soup.h1.span.string
    content = "".join(
        map(
            lambda tag: str(tag),
            soup.article.find_all("div", "node__content")[0].select("div.text-formatted")
        )
    )
    user_id = soup.article.find_all("div", "node__submitted")[0].span.span.string
    datetime = soup.article.find_all("time", "datetime")[0]["datetime"]

    create_blog = createBlog(title, md(content), user_id, datetime)
    blog_id = str(uuid6.uuid7())
    send_kafka("blog-command", blog_id, create_blog)

    time.sleep(3)

    comment_articles = soup.find_all("article", class_=lambda x: x and "comment" in x)

    total_comments = len(comment_articles)
    print(
        f"Összesen {total_comments} kommentet találtam. Feldolgozás megkezdése..."
    )

    if total_comments == 0:
        return []

    flat_comments = {}
    processed_count = 0

    # 1. Lépés: Adatok kinyerése egy átmeneti szótárba
    for article in comment_articles:
        comment_data = parse_comment(article, processed_count)
        if not comment_data:
            continue
        flat_comments[comment_data["id"]] = comment_data

        processed_count += 1
        if processed_count % 100 == 0 or processed_count == total_comments:
            print(f"-> Feldolgozva: {processed_count} / {total_comments} komment")

    for c_id, comment in flat_comments.items():
        p_id = comment["parent_id"]

        comment_uuid = str(comment["uuid"])
        parent_uuid = str(comment["parent_uuid"])
        username = comment["username"]
        datetime = comment["datetime"]

        if p_id:
            reply_comment = replyComment(comment_uuid, parent_uuid, md(comment["content"]), username, datetime)
            send_kafka("discussion-command", "disc-" + blog_id, reply_comment)
        else:
            add_comment = addComment(comment_uuid, md(comment["content"]), username, datetime)
            send_kafka("discussion-command", "disc-" + blog_id, add_comment)

    print("Hierarchia (fa struktúra) felépítése...")
#
#     root_comments = []
#     # 2. Lépés: A fastruktúra összeállítása
#     for c_id, comment in flat_comments.items():
#         p_id = comment["parent_id"]
#
#         comment_uuid = str(comment["uuid"])
#         parent_uuid = str(comment["parent_uuid"])
#
#         clean_comment = {
#             "id": comment_uuid,
#             "parent_id": parent_uuid,
#             "username": comment["username"],
#             "date": comment["date"],
#             "time": comment["time"],
#             "content": comment["content"],
#             "children": comment["children"],
#         }
#
#         if p_id and p_id in flat_comments:
#             flat_comments[p_id]["children"].append(clean_comment)
#         else:
#             root_comments.append(clean_comment)
#
#     return root_comments
    return flat_comments


if __name__ == "__main__":
#     target_url = "https://hup.hu/node/130291"
#     text = downloadUrl(target_url)

    text = readFile(fileName)

    comments_tree = scrape_hup_comments(text)

    if comments_tree:
        # Puffer ürítése és lezárás
        producer.flush()
        producer.close()

#         output_filename = "hup_comments.json"
#         with open(output_filename, "w", encoding="utf-8") as f:
#             json.dump(comments_tree, f, ensure_ascii=False, indent=4)
#         print(f"\nSikeres mentés! A fába rendezett fájl neve: {output_filename}")
#     else:
#         print("\nNem sikerült adatot menteni.")
