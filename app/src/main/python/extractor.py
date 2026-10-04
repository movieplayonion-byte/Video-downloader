import yt_dlp

def get_stream(url, mode="video", quality="720"):
    url = url.strip()
    
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'skip_download': True,
        # Android/iOS clients bina JS engine ke progressive mp4 stream dete hain
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
                # Audio stream filter
                audios = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none' and f.get('url')]
                if audios:
                    return audios[-1]['url']
            else:
                # Progressive MP4 (jisme video + audio dono ho)
                mp4s = [f for f in formats if f.get('vcodec') != 'none' and f.get('acodec') != 'none' and f.get('ext') == 'mp4' and f.get('url')]
                for f in mp4s:
                    if quality in str(f.get('height', '')):
                        return f['url']
                if mp4s:
                    return mp4s[-1]['url']

            # Agar progressive na mile toh direct single stream format
            valid_streams = [f for f in formats if f.get('url')]
            if valid_streams:
                return valid_streams[-1]['url']

            return "ERR_NO_FORMAT: No playable media stream found"
    except Exception as e:
        return f"ERR_PY: {str(e)}"
