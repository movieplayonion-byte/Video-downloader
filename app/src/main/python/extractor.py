import yt_dlp
import re

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

        # Attempt 1: Normal extraction (pehle jaise)
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                res = _extract_url(info, mode, quality)
                if res:
                    return res
        except Exception as e_initial:
            if not is_instagram:
                raise e_initial

        # Attempt 2: Instagram specific smart fallback (agar attempt 1 fail hua)
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
