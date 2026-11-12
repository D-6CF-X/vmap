"""重抓 B 站链接：按用户要求「取综合排名第一个」。
API 返回顺序与网页综合排序不完全一致，所以用打分近似：
  官方号(+40) + 标题含歌名(+40) + log10播放×12 + 投稿新度(0~15)
过滤：标题或作者必须含板块关键词，避免同名歌（如李艺彤的 Monologue）混进来。
失败的歌保留原 bili 不动。
"""
import json, os, re, time, random, urllib.request, urllib.parse
from datetime import datetime, timezone, timedelta

ASSET = os.path.dirname(os.path.abspath(__file__))
os.chdir(ASSET)

CN = timezone(timedelta(hours=8))
d = json.load(open('song_archive.json', encoding='utf-8'))

OFFICIAL_HINT = {
    'TIANYI': ['洛天依', '天依', 'Vsinger', 'vsinger', '乐正', '言和'],
    'MIKU': ['初音', 'miku', 'Miku', 'MIKU', 'ミク', 'Hatsune'],
}
UA_LIST = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15',
]

def norm(t):
    t = t.lower()
    t = re.sub(r'[（(\\[【].*?[）)\\]】]', '', t)
    return re.sub(r'\\s+', '', t)

def search(kw):
    q = {'search_type': 'video', 'keyword': kw}
    url = 'https://api.bilibili.com/x/web-interface/search/type?' + urllib.parse.urlencode(q)
    req = urllib.request.Request(url, headers={
        'User-Agent': random.choice(UA_LIST),
        'Referer': 'https://search.bilibili.com/',
        'Cookie': 'buvid3=Inf%1BvDjvcmZdCbGCeTSFp%2FZvwzw%3D%3D;',
    })
    d = json.load(urllib.request.urlopen(req, timeout=15))
    return d.get('data', {}).get('result', [])

def clean_title(t):
    return t.replace('<em class="keyword">', '').replace('</em>', '')

changed = 0
failed = 0
for i, s in enumerate(d['songs']):
    title = s['title']
    section = s.get('series', 'TIANYI')
    hints = OFFICIAL_HINT.get(section, OFFICIAL_HINT['TIANYI'])
    kw = title
    try:
        results = search(kw)
    except Exception as e:
        print(i, 'FAIL', title[:20], repr(e)[:60])
        failed += 1
        time.sleep(2.5)
        continue

    tnorm = norm(title)
    cands = []
    for r in results:
        t = clean_title(r['title'])
        tn = norm(t)
        author = r['author']
        official = any(h in author for h in hints)
        related = (tnorm in tn) or any(h in author for h in hints) or any(h in t for h in hints)
        if not related:
            continue
        pub = datetime.fromtimestamp(r.get('pubdate', 0), CN)
        play = r.get('play', 0) or 0
        score = (40 if official else 0) + (40 if tnorm in tn else 0) + \
            __import__('math').log10(play + 1) * 12
        cands.append({
            'score': score, 'bvid': r['bvid'], 'author': author, 'play': play,
            'title': t, 'cover': r.get('pic', ''),
            'official': official, 'pub': pub,
        })

    if cands:
        newest = max(c['pub'] for c in cands)
        oldest = min(c['pub'] for c in cands)
        span = max((newest - oldest).days, 1)
        for c in cands:
            fresh = (c['pub'] - oldest).days / span
            c['score'] += fresh * 15
        best = max(cands, key=lambda c: c['score'])
        bili = s.get('bili') or {}
        if bili.get('bvid') != best['bvid']:
            changed += 1
        label = 'B站官方' if best['official'] else 'B站综合第一'
        s['bili'] = {
            'bvid': best['bvid'],
            'url': 'https://www.bilibili.com/video/' + best['bvid'],
            'author': best['author'],
            'play': best['play'],
            'official': best['official'],
            'label': label,
            'title': best['title'][:60],
            'cover': bili.get('cover', ''),  # 封面已由决策树定过，不动
        }
        if i % 20 == 0:
            print(i, '/', len(d['songs']), 'ok', title[:16], '->', best['author'], best['play'])
    time.sleep(1.2)

json.dump(d, open('song_archive.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('DONE changed=%d failed=%d total=%d' % (changed, failed, len(d['songs'])))
