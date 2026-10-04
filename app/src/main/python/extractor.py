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
                'User-Agent': 'com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip'
            }
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            formats = info.get('formats', [])

            if mode == "audio":
                # Sirf pure audio streams (m4a/aac preferred jo har music player me chalta hai)
                audio_streams = [
                    f for f in formats 
                    if f.get('url') and f.get('vcodec') == 'none' and f.get('acodec') != 'none'
                ]
                # Pehle m4a dhundho
                m4a_streams = [f for f in audio_streams if f.get('ext') == 'm4a']
                if m4a_streams:
                    return m4a_streams[-1]['url']
                elif audio_streams:
                    return audio_streams[-1]['url']
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
