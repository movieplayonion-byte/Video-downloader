import yt_dlp
import re
import urllib.request
import http.cookiejar
import json

IG_COOKIE = "Csrftoken=1k8TTqxOBC25HLYEroY7d2LaBIkCiFwU; datr=lf_GaqbhwWbSMYwkIH-9qV20; ig_did=8BD699EA-C578-4B00-B065-EB08D31611E5; wd=378x706; dpr=2.857142857142857; mid=asb_lQABAAFaXL8YC5w9S7kHbLFx; ds_user_id=23695702041; sessionid=23695702041%3APle5WrdBcbvdIu%3A5%3AAYnU15iwdj71axkDB0Ji4cYyJ3HlAIObVrIyOZweIA; rur=VLL%2C17841423701251962%2C1792636205%3A01ffc14b9872e55e87d60078c45152006858510bd442d82279b9396cb90ad24240943d86;"

def _extract_url(info, mode, quality):
    formats = info.get('formats', [])
    if mode == "audio":
        audios = [
            f for f in formats
            if f.get('url') and f.get('acodec') != 'none' and (f.get('vcodec') == 'none' or f.get('vcodec') is None)
        ]
        if audios:
            audios.sort(key=lambda x: x.get('abr') or 0)
            return audios[-1]['url']
        for f in formats:
            if f.get('url') and f.get('acodec') != 'none':
                return f['url']
    else:
        prog_mp4 = [
            f for f in formats
            if f.get('url') and f.get('vcodec') != 'none' and f.get('acodec') != 'none' and f.get('ext') == 'mp4'
        ]
        for f in prog_mp4:
            if str(quality) in str(f.get('height', '')):
                return f['url']
        if prog_mp4:
            return prog_mp4[-1]['url']
        for f in formats:
            if f.get('url'):
                return f['url']
    return None

def _extract_tiktok_direct(url, mode):
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    opener.addheaders = [
        ('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'),
        ('Accept-Language', 'en-US,en;q=0.9'),
    ]
    resp = opener.open(url, timeout=15)
    html = resp.read().decode('utf-8', errors='ignore')

    m = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html)
    if not m:
        return None

    data = json.loads(m.group(1))
    detail = data.get("__DEFAULT_SCOPE__", {}).get("webapp.video-detail", {})
    item = detail.get("itemInfo", {}).get("itemStruct", {})

    if mode == "audio":
        media_url = item.get("music", {}).get("playUrl")
    else:
        media_url = item.get("video", {}).get("playAddr") or item.get("video", {}).get("downloadAddr")

    if not media_url:
        return None

    cookie_str = "; ".join([f"{c.name}={c.value}" for c in cj])
    if cookie_str:
        return f"{media_url}|COOKIE={cookie_str}"
    return media_url

def get_stream(url, mode="video", quality="720"):
    try:
        url = str(url).strip()
        if url.startswith("/live/"):
            url = "https://www.youtube.com" + url
        elif url.startswith(".be/") or url.startswith("tu.be/"):
            url = "https://you" + url.lstrip(".")
        elif not url.startswith("http://") and not url.startswith("https://"):
            url = "https://" + url

        url = re.sub(r'(\?|&)si=[^&]+', '', url)

        is_youtube = ("youtube.com" in url) or ("youtu.be" in url)
        is_instagram = "instagram.com" in url
        is_tiktok = ("tiktok.com" in url) or ("vm.tiktok.com" in url) or ("vt.tiktok.com" in url)

        # TikTok Direct Extraction
        if is_tiktok:
            try:
                tt_res = _extract_tiktok_direct(url, mode)
                if tt_res:
                    return tt_res
            except Exception as e_tt:
                err_str = str(e_tt).lower()
                if "timed out" in err_str or "connection" in err_str or "reset" in err_str:
                    return "ERR_VPN: TikTok blocked! Please connect a VPN and try again."

        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'skip_download': True
        }

        if is_youtube:
            ydl_opts['extractor_args'] = {
                'youtube': {
                    'player_client': ['android', 'ios'],
                    'player_skip': ['webpage', 'configs', 'js']
                }
            }
            ydl_opts['http_headers'] = {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
            }

        # Attempt 1: Normal extraction
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if info and 'entries' in info:
                    info = info['entries'][0]
                res = _extract_url(info, mode, quality)
                if res:
                    return res
                if info and info.get('url'):
                    return info['url']
        except Exception as e_initial:
            if not is_instagram:
                raise e_initial

        # Attempt 2: Instagram specific smart fallback
        if is_instagram:
            ydl_opts_ig = ydl_opts.copy()
            ydl_opts_ig['http_headers'] = {
                'Cookie': IG_COOKIE,
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
                'Referer': 'https://www.instagram.com/'
            }
            with yt_dlp.YoutubeDL(ydl_opts_ig) as ydl:
                info = ydl.extract_info(url, download=False)
                res = _extract_url(info, mode, quality)
                if res:
                    return res

        return "ERR_NO_STREAM: Stream format not available"

    except Exception as e:
        return f"ERR_PY: {str(e)}"
        
