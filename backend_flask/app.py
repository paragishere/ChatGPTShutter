import os
import base64
import requests

from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# Ensure .env is loaded reliably regardless of the working directory
dotenv_path = os.path.join(os.path.dirname(__file__), '.env')
load_dotenv(dotenv_path)

app = Flask(__name__)
CORS(app)

GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.6-flash")
GEMINI_API_URL = (
    "https://generativelanguage.googleapis.com/v1beta/"
    f"models/{GEMINI_MODEL}:generateContent"
)

def get_api_keys():
    """
    Returns a list of API keys.
    Supports GEMINI_API_KEYS (comma separated) or GEMINI_API_KEY as fallback.
    """
    keys_str = os.environ.get("GEMINI_API_KEYS") or os.environ.get("GEMINI_API_KEY")
    if not keys_str:
        return []
    return [k.strip() for k in keys_str.split(",") if k.strip()]

@app.route("/api/analyze-photo", methods=["POST"])
def analyze_photo():
    api_keys = get_api_keys()

    if not api_keys:
        return jsonify({
            "response": None,
            "error": "Gemini API keys are not configured."
        }), 500

    if "image" not in request.files:
        return jsonify({
            "response": None,
            "error": "No image uploaded."
        }), 400

    file = request.files["image"]

    if not file.filename:
        return jsonify({
            "response": None,
            "error": "Empty file name."
        }), 400

    image_bytes = file.read()

    if not image_bytes:
        return jsonify({
            "response": None,
            "error": "Uploaded image is empty."
        }), 400

    mime_type = file.mimetype or "image/jpeg"

    if not mime_type.startswith("image/"):
        return jsonify({
            "response": None,
            "error": "Uploaded file must be an image."
        }), 400

    prompt = request.form.get(
        "prompt",
        "Please analyze this image."
    )

    print(f"Processing image: {len(image_bytes)} bytes")

    payload = {
        "contents": [{
            "parts": [
                {"text": prompt},
                {
                    "inline_data": {
                        "mime_type": mime_type,
                        "data": base64.b64encode(
                            image_bytes
                        ).decode("utf-8")
                    }
                }
            ]
        }],
        "generationConfig": {
            "maxOutputTokens": 500
        }
    }

    last_error_message = "Unknown error"
    last_status_code = 502

    # Loop through configured keys until one succeeds
    for attempt, api_key in enumerate(api_keys):
        try:
            # Use the secure x-goog-api-key header for Gemini API requests
            headers = {
                "x-goog-api-key": api_key,
                "Content-Type": "application/json"
            }

            response = requests.post(
                GEMINI_API_URL,
                headers=headers,
                json=payload,
                timeout=90
            )

            if not response.ok:
                try:
                    data = response.json()
                    error = data.get("error", {})
                    message = error.get(
                        "message",
                        f"Gemini returned HTTP {response.status_code}"
                    )
                    print(f"Gemini API error (Key {attempt + 1}) {response.status_code}: {message}")
                except ValueError:
                    message = f"Gemini returned HTTP {response.status_code}"

                status = response.status_code
                last_error_message = message
                last_status_code = status

                # If Bad Request (400), it's a malformed payload, no point retrying
                if status == 400:
                    return jsonify({
                        "response": None,
                        "error": message
                    }), 400

                # For 429 (Quota), 401/403 (Auth), 5xx (Server Error), attempt next key
                continue

            # Parse successful response
            data = response.json()
            candidates = data.get("candidates", [])

            if not candidates:
                print("Gemini returned no candidates.")
                return jsonify({
                    "response": None,
                    "error": (
                        "Gemini returned no answer. "
                        "The image or prompt may have been blocked."
                    )
                }), 502

            parts = candidates[0].get("content", {}).get("parts", [])
            ai_text = "\n".join(
                part["text"]
                for part in parts
                if "text" in part
            ).strip()

            if not ai_text:
                return jsonify({
                    "response": None,
                    "error": "Gemini returned an empty response."
                }), 502

            return jsonify({
                "response": ai_text,
                "error": None
            })

        except requests.exceptions.Timeout:
            print(f"Key {attempt + 1} timed out.")
            last_error_message = "Gemini API request timed out."
            last_status_code = 504
            continue

        except requests.exceptions.RequestException as exc:
            print(f"Key {attempt + 1} network error: {type(exc).__name__}")
            last_error_message = "Could not connect to Gemini API."
            last_status_code = 502
            continue

        except Exception as exc:
            print(f"Key {attempt + 1} backend error: {type(exc).__name__}: {exc}")
            last_error_message = "Unexpected backend error."
            last_status_code = 500
            continue

    # If we exited the loop, all available keys were exhausted or failed.
    if last_status_code == 429:
        final_error = (
            "Gemini API rate limit or quota reached on all configured projects. "
            "Check Google AI Studio usage and limits."
        )
    elif last_status_code in [401, 403]:
        final_error = "Configured API keys are invalid or lack permissions."
    else:
        final_error = f"Failed to get response after trying all keys: {last_error_message}"

    return jsonify({
        "response": None,
        "error": final_error
    }), last_status_code

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
