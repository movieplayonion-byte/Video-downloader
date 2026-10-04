import yt_dlp
import re

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
                # Audio ke liye sabse reliable single audio stream jiska URL ho
                audios = [
                    f for f in formats 
                    if f.get('url') and f.get('acodec') != 'none' and (f.get('vcodec') == 'none' or f.get('vcodec') is None)
                ]
                # Best bitrate audio
                if audios:
                    # Sort by audio bitrate
                    audios.sort(key=lambda x: x.get('abr') or 0)
                    return audios[-1]['url']
                
                # Agar standalone audio na mile to lowest video-audio stream
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
