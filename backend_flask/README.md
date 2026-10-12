# Dont Touch (ChatGPTShutter) Flask Backend

This is the secure backend for the Dont Touch Android app. It handles image analysis requests, securely communicates with the Gemini REST API using your server-side API keys, and returns the response to the mobile app.

## Multi-Account / Multi-Project Support
This backend supports configuring multiple Gemini API credentials. If a key hits a transient failure or a 429 Rate Limit/Quota Exhaustion error, the server safely attempts the request using the next key in the list.

**Important Terms of Service Warning:**
Do not assume separate API keys provide separate quotas if they belong to the same Google Cloud or AI Studio project. The multi-key configuration is intended for load-balancing across *legitimate, distinct eligible projects* (e.g., separating production traffic across different billed projects). Do not implement account rotation to evade provider-imposed limits or enforcement; this violates Google's terms of service.

## Setup Instructions (Windows PowerShell)

1. **Navigate to the backend directory:**
   ```powershell
   cd C:\Users\4arag\AndroidStudioProjects\ChatGPTShutter\backend_flask
   ```

2. **Create a virtual environment:**
   ```powershell
   python -m venv venv
   ```

3. **Activate the virtual environment:**
   ```powershell
   .\venv\Scripts\Activate.ps1
   ```

4. **Install requirements:**
   ```powershell
   pip install -r requirements.txt
   ```

5. **Configure the API Key:**
   ```powershell
   copy .env.example .env
   ```
   Open the `.env` file in Notepad and replace the placeholder keys with your real Gemini API keys.

6. **Start the server:**
   ```powershell
   python app.py
   ```
   *Note: Ensure your Windows Firewall prompts allow Python to communicate on private networks, so your phone can reach it.*

## Termux (Android) Setup Instructions

1. **Install Python and Git in Termux:**
   ```bash
   pkg update
   pkg install python git
   ```

2. **Navigate to the backend directory:**
   *(Assuming you copied the `backend_flask` folder to your Termux home directory)*
   ```bash
   cd ~/backend_flask
   ```

3. **Install requirements:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Configure the API Keys:**
   ```bash
   cp .env.example .env
   nano .env
   ```
   Add your Gemini API keys as a comma-separated list (`GEMINI_API_KEYS=key1,key2`) and save.

5. **Start the server:**
   ```bash
   python app.py
   ```

6. **Configure the Android App (Termux Localhost):**
   When running the backend directly on the same phone in Termux, open `ApiClient.kt` in Android Studio and update:
   ```kotlin
   const val BACKEND_URL = "http://127.0.0.1:5000"
   ```