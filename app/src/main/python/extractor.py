import yt_dlp

def get_stream(url, mode="video", quality="720"):
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'skip_download': True
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
            
            if formats and 'url' in formats[-1]:
                return formats[-1]['url']
            return ""
    except Exception as e:
        return ""
