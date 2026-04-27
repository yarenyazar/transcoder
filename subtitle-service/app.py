import os
import tempfile
import traceback
from flask import Flask, request, jsonify
from faster_whisper import WhisperModel
import subprocess

app = Flask(__name__)

# Initialize the model once when the container starts.
# Upgrade to 'base' for better accuracy than 'tiny'
print("Loading Whisper model (base)...")
model = WhisperModel("base", device="cpu", compute_type="int8")
print("Whisper model loaded!")

def to_vtt_time(seconds):
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = seconds % 60
    return f"{hours:02d}:{minutes:02d}:{secs:06.3f}"

@app.route('/generate-subtitles', methods=['POST'])
def generate_subtitles():
    data = request.json
    video_path = data.get('video_path')
    output_dir = data.get('output_dir')
    target_lang = data.get('language', 'tr').lower()
    
    if not video_path or not os.path.exists(video_path):
        return jsonify({"error": f"Video file not found at: {video_path}"}), 400

    if not output_dir:
        return jsonify({"error": "Output directory missing"}), 400
        
    os.makedirs(output_dir, exist_ok=True)
    vtt_path = os.path.join(output_dir, f"{target_lang}.vtt")

    try:
        # 1. Extract audio from video to a temporary WAV file using FFmpeg
        print(f"--- STARTING NEW JOB ---")
        print(f"Target Language: {target_lang}")
        print(f"Extracting audio from {video_path}...")
        temp_audio = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
        temp_audio.close()
        
        command = [
            'ffmpeg', '-y', '-i', video_path, 
            '-vn', '-acodec', 'pcm_s16le', '-ar', '16000', '-ac', '1', 
            temp_audio.name
        ]
        
        # Capture stderr for debugging
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"FFmpeg Error: {result.stderr}")
            return jsonify({"error": "FFmpeg audio extraction failed", "details": result.stderr}), 500

        # 2. Run Whisper on the extracted audio
        print(f"Running Whisper (base model) for {video_path}...")
        
        # Determine task: transcribe (same lang) or translate (source -> english)
        # Note: Whisper's translate task only supports translating to English.
        task = "transcribe"
        
        # Detect language first if target is English or if we want better results
        # We'll pass the requested language to 'transcribe' if it's not 'auto'
        whisper_lang = target_lang if target_lang != 'auto' else None
        
        # If user wants English, we might be translating from another language
        if target_lang == 'en':
            # Run a quick detection if lang is unknown, but Whisper info does this automatically
            # We'll set task to translate if we know or suspect it's not English
            # For simplicity, we can let Whisper detect and translate to English if task="translate"
            task = "translate"
            print("Detected target 'en', using task='translate'")
        else:
            print(f"Using task='transcribe' for language: {target_lang}")

        segments, info = model.transcribe(
            temp_audio.name, 
            language=whisper_lang, 
            task=task,
            beam_size=5
        )

        print(f"Detected language: {info.language} (probability: {info.language_probability:.2f})")

        # 3. Write output to VTT
        print(f"Writing VTT to {vtt_path}...")
        with open(vtt_path, 'w', encoding='utf-8') as f:
            f.write("WEBVTT\n\n")
            # Segments is an iterator, we need to iterate to actually run the transcription
            segment_list = list(segments)
            for segment in segment_list:
                start_time = to_vtt_time(segment.start)
                end_time = to_vtt_time(segment.end)
                f.write(f"{start_time} --> {end_time}\n")
                f.write(f"{segment.text.strip()}\n\n")

        # Cleanup temp audio
        if os.path.exists(temp_audio.name):
            os.remove(temp_audio.name)
            
        print(f"Transcription complete! Created {len(segment_list)} segments.")
        return jsonify({
            "status": "success",
            "vtt_path": vtt_path,
            "detected_language": info.language,
            "requested_language": target_lang,
            "task_performed": task
        }), 200

    except Exception as e:
        print(f"Error during transcription: {str(e)}")
        print(traceback.format_exc())
        return jsonify({"error": str(e), "traceback": traceback.format_exc()}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
