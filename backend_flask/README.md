# ChatGPTShutter Flask Backend

This is the secure backend for the ChatGPTShutter Android app. It handles the image analysis requests, securely communicates with the OpenAI REST API using your server-side API key, and returns the response to the mobile app.

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
   Open the `.env` file in Notepad and replace `YOUR_ACTUAL_OPENAI_KEY_HERE` with your real OpenAI API key.

6. **Start the server:**
   ```powershell
   python app.py
   ```
   *Note: Ensure your Windows Firewall prompts allow Python to communicate on private networks, so your phone can reach it.*

## Local Phone Testing (From Laptop)

To test with a physical Android device on the same Wi-Fi network:

1. **Find your Laptop's LAN IP Address:**
   Open a new PowerShell window and run:
   ```powershell
   ipconfig | findstr IPv4
   ```
   Look for the IP address starting with `192.168.x.x` or `10.x.x.x`.

2. **Configure the Android App:**
   Open `ApiClient.kt` in Android Studio and set the URL using the IP you found. For example:
   ```kotlin
   const val BACKEND_URL = "http://192.168.1.100:5000"
   ```

## Termux (Android) Setup Instructions

If you are running this backend directly on your Android device using Termux, the official `openai` Python SDK might fail to install because of Rust dependencies (`jiter`). This backend now uses the standard `requests` library to completely bypass that issue.

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

4. **Configure the API Key:**
   ```bash
   cp .env.example .env
   nano .env
   ```
   Add your OpenAI API key and save.

5. **Start the server:**
   ```bash
   python app.py
   ```

6. **Configure the Android App (Termux Localhost):**
   When running the backend directly on the same phone in Termux, you *can* use localhost because the server is running on the device itself.
   Open `ApiClient.kt` in Android Studio and update:
   ```kotlin
   const val BACKEND_URL = "http://127.0.0.1:5000"
   ```
