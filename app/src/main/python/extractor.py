import yt_dlp
import re
import urllib.request
import json
import ssl

def fetch_instagram_desktop(url):
    try:
        # URL se sabhi query tracking parameters saaf karna (?utm_source etc.)
        clean_url = re.sub(r'\?.*$', '', url).rstrip('/') + '/'
        
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

        # Desktop Chrome Browser Emulation Headers
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
            'Accept-Language': 'en-US,en;q=0.9',
            'Sec-Ch-Ua': '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
            'Sec-Ch-Ua-Mobile': '?0',
            'Sec-Ch-Ua-Platform': '"Windows"',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'none',
            'Sec-Fetch-User': '?1',
            'Upgrade-Insecure-Requests': '1'
        }

        req = urllib.request.Request(clean_url, headers=headers)
        html = urllib.request.urlopen(req, context=ctx, timeout=12).read().decode('utf-8', errors='ignore')

        # 1. Desktop HTML OpenGraph Video Tag
        og_match = re.search(r'<meta\s+property=["\']og:video(?::secure_url)?["\']\s+content=["\']([^"\']+)["\']', html)
        if og_match:
            return og_match.group(1).replace('&amp;', '&')

        # 2. Desktop JSON-LD / Video URL match
        v_match = re.search(r'["\']video_url["\']\s*:\s*["\']([^"\']+)["\']', html)
        if v_match:
            raw = v_match.group(1).encode().decode('unicode-escape')
            return raw.replace('&amp;', '&').replace('\\/', '/')

        # 3. CDN MP4 direct link pattern
        cdn_match = re.search(r'(https://[^"\'\s]+\.cdninstagram\.com/[^"\'\s]+\.mp4[^"\'\s]*)', html)
        if cdn_match:
            return cdn_match.group(1).replace('&amp;', '&').replace('\\/', '/')

    except Exception as e:
        return f"ERR_INSTA_DESKTOP: {str(e)}"
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

        # Agar Instagram link hai to Desktop browser mode se direct fetch karo
        if "instagram.com" in url:
            stream_url = fetch_instagram_desktop(url)
            if stream_url and stream_url.startswith("http"):
                return stream_url
            elif stream_url and stream_url.startswith("ERR_"):
                return stream_url

        # YouTube aur baaki sab platforms ke liye purana 100% working yt-dlp flow
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'skip_download': True,
            'extractor_args': {
                'youtube': {
                    'player_client': ['android', 'ios'],
                    'player_skip': ['webpage', 'configs', 'js']
                }
            },
            'http_headers': {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
            }
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
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

        return "ERR_NO_STREAM: Stream format not available"
    except Exception as e:
        return f"ERR_PY: {str(e)}"
