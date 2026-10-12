import os
import base64
import requests
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

app = Flask(__name__)
CORS(app) # Allow cross-origin requests if tested from web clients

OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"

@app.route('/api/analyze-photo', methods=['POST'])
def analyze_photo():
    api_key = os.environ.get("OPENAI_API_KEY")

    # 1. Configuration check
    if not api_key:
        return jsonify({"response": None, "error": "OpenAI API key is not configured on the server."}), 500

    # 2. Extract Multipart Request data
    if 'image' not in request.files:
        return jsonify({"response": None, "error": "No image uploaded"}), 400

    file = request.files['image']
    if file.filename == '':
        return jsonify({"response": None, "error": "Empty file name"}), 400

    prompt = request.form.get('prompt', 'Please analyze this image.')

    try:
        # 3. Read image and encode to base64 for OpenAI Vision
        image_bytes = file.read()
        base64_image = base64.b64encode(image_bytes).decode('utf-8')
        mime_type = file.mimetype or 'image/jpeg'

        print(f"Processing request... Image size: {len(image_bytes)} bytes")

        # 4. Secure server-to-server HTTP call to OpenAI via requests
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        }

        payload = {
            "model": "gpt-4o",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:{mime_type};base64,{base64_image}"
                            }
                        }
                    ]
                }
            ],
            "max_tokens": 500
        }

        # Make the request to OpenAI with a timeout
        response = requests.post(OPENAI_API_URL, headers=headers, json=payload, timeout=60)

        # Raise an exception for HTTP errors (4xx, 5xx)
        response.raise_for_status()

        # 5. Format success response matching the Android Client contract
        response_json = response.json()
        ai_text = response_json['choices'][0]['message']['content']
        return jsonify({"response": ai_text, "error": None})

    except requests.exceptions.Timeout:
        print("API Error: Request to OpenAI timed out.")
        return jsonify({"response": None, "error": "OpenAI API request timed out."}), 504

    except requests.exceptions.RequestException as e:
        print(f"API Request Error: {e}")
        error_msg = str(e)
        # Try to parse the OpenAI error message if available
        if e.response is not None:
            try:
                error_data = e.response.json()
                if 'error' in error_data and 'message' in error_data['error']:
                    error_msg = error_data['error']['message']
            except Exception:
                pass
        return jsonify({"response": None, "error": f"Failed to communicate with OpenAI: {error_msg}"}), 502

    except Exception as e:
        print(f"Server Error: {e}")
        # Format error response matching Android Client contract
        return jsonify({"response": None, "error": f"Failed to process image: {str(e)}"}), 500

if __name__ == '__main__':
    # Listen on all network interfaces to allow the Android phone to connect over LAN
    app.run(host='0.0.0.0', port=5000, debug=True)
