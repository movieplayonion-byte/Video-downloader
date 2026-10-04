import yt_dlp
import re

def get_stream(url, mode="video", quality="720"):
    url = url.strip()
    
    # Auto-fix incomplete URL prefixes
    if url.startswith("/live/"):
        url = "https://www.youtube.com" + url
    elif url.startswith(".be/"):
        url = "https://youtu" + url
    elif not url.startswith("http://") and not url.startswith("https://"):
        url = "https://" + url

    # Remove tracking ?si= parameter
    url = re.sub(r'(\?|&)si=[^&]+', '', url)

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
            'User-Agent': 'com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip'
        }
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            formats = info.get('formats', [])

            if mode == "audio":
                audios = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none' and f.get('url')]
                if audios:
                    return audios[-1]['url']
            else:
                mp4s = [f for f in formats if f.get('vcodec') != 'none' and f.get('acodec') != 'none' and f.get('ext') == 'mp4' and f.get('url')]
                for f in mp4s:
                    if quality in str(f.get('height', '')):
                        return f['url']
                if mp4s:
                    return mp4s[-1]['url']

            valid = [f for f in formats if f.get('url')]
            if valid:
                return valid[-1]['url']

            return "ERR_NO_STREAM: Stream format not available"
    except Exception as e:
        return f"ERR_PY: {str(e)}"
