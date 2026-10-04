import yt_dlp
import re

def get_stream(url, mode="video", quality="720"):
    try:
        url = str(url).strip()
        
        # Incomplete / Shortened URLs fix karna
        if url.startswith("/live/"):
            url = "https://www.youtube.com" + url
        elif url.startswith(".be/") or url.startswith("tu.be/"):
            url = "https://you" + url.lstrip(".")
        elif not url.startswith("http://") and not url.startswith("https://"):
            url = "https://" + url

        # Query tracking remove karna
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
                # Audio stream: best audio stream with valid URL
                audio_streams = [
                    f for f in formats 
                    if f.get('url') and (f.get('vcodec') == 'none' or f.get('acodec') != 'none')
                ]
                if audio_streams:
                    # Return best audio
                    return audio_streams[-1]['url']
            else:
                # Video stream: progressive mp4 jisme audio + video dono ho
                prog_mp4 = [
                    f for f in formats 
                    if f.get('url') and f.get('vcodec') != 'none' and f.get('acodec') != 'none' and f.get('ext') == 'mp4'
                ]
                for f in prog_mp4:
                    if str(quality) in str(f.get('height', '')):
                        return f['url']
                if prog_mp4:
                    return prog_mp4[-1]['url']

            # Fallback agar specified format na mile
            for f in formats:
                if f.get('url'):
                    return f['url']

            return "ERR_NO_STREAM: Stream format not available"
    except Exception as e:
        return f"ERR_PY: {str(e)}"
