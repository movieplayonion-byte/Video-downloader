import yt_dlp
import re
import urllib.request
import json
import ssl

def get_instagram_stream(url):
    try:
        # Shortcode nikaalna: /reel/CODE/ ya /p/CODE/
        match = re.search(r'instagram\.com/(?:reel|p|tv)/([^/?&#]+)', url)
        if not match:
            return None
        shortcode = match.group(1)

        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

        embed_url = f"https://www.instagram.com/p/{shortcode}/embed/captioned/"
        req = urllib.request.Request(
            embed_url,
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
                'Accept-Language': 'en-US,en;q=0.9',
                'Sec-Fetch-Mode': 'navigate'
            }
        )
        html = urllib.request.urlopen(req, context=ctx, timeout=12).read().decode('utf-8', errors='ignore')

        # 1. Embed page JSON / video_url search
        v_match = re.search(r'\\?"video_url\\?":\\?"([^"]+)\\?"', html)
        if v_match:
            raw_url = v_match.group(1).replace(r'\/', '/').replace('&amp;', '&')
            raw_url = raw_url.encode().decode('unicode-escape')
            return raw_url

        # 2. Direct video src tag in embed
        src_match = re.search(r'<video[^>]+src=["\']([^"\']+)["\']', html)
        if src_match:
            return src_match.group(1).replace('&amp;', '&')

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

        # 1. Agar Instagram link hai to embed resolver try karo
        if "instagram.com" in url:
            insta_stream = get_instagram_stream(url)
            if insta_stream and insta_stream.startswith("http"):
                return insta_stream

        # 2. YouTube aur normal flows (100% UNTOUCHED yt-dlp)
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
