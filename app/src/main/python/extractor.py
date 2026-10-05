import yt_dlp
import re
import urllib.request
import json

def get_instagram_fallback(url):
    try:
        clean_url = re.sub(r'\?.*$', '', url).rstrip('/') + '/'
        req = urllib.request.Request(
            clean_url,
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
                'Accept-Language': 'en-US,en;q=0.9'
            }
        )
        html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
        
        # 1. OpenGraph video meta tag
        og_match = re.search(r'<meta\s+property=["\']og:video(?::secure_url)?["\']\s+content=["\']([^"\']+)["\']', html)
        if og_match:
            return og_match.group(1).replace('&amp;', '&')
            
        # 2. JSON-LD / Page state video_url
        video_match = re.search(r'["\']video_url["\']\s*:\s*["\']([^"\']+)["\']', html)
        if video_match:
            raw_url = video_match.group(1).encode().decode('unicode-escape')
            return raw_url.replace('&amp;', '&')
    except Exception:
        pass
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

        # Agar Instagram link hai to clean URL bana kar pehle yt-dlp try karenge
        is_instagram = "instagram.com" in url

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

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                formats = info.get('formats', [])
        except Exception as e:
            # Agar yt-dlp Instagram par empty response de to HTML scraper chalega
            if is_instagram:
                direct_url = get_instagram_fallback(url)
                if direct_url:
                    return direct_url
            raise e

        if is_instagram and not formats:
            direct_url = get_instagram_fallback(url)
            if direct_url:
                return direct_url

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
