require('dotenv').config();
const express = require('express');
const multer = require('multer');
const { OpenAI } = require('openai');
const fs = require('fs');
const cors = require('cors');

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());

// Configure Multer for handling file uploads (multipart/form-data)
const upload = multer({ dest: 'uploads/' });

// Initialize OpenAI Client
const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY,
});

app.post('/api/analyze-photo', upload.single('image'), async (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ error: 'No image uploaded' });
        }

        const prompt = req.body.prompt || "Analyze this image.";

        // Convert image file to base64 for OpenAI Vision
        const imageAsBase64 = fs.readFileSync(req.file.path, 'base64');
        const mimeType = req.file.mimetype || 'image/jpeg';
        const dataUrl = `data:${mimeType};base64,${imageAsBase64}`;

        console.log(`Received request. Prompt: "${prompt}". Image size: ${req.file.size} bytes`);

        const response = await openai.chat.completions.create({
            model: "gpt-4o",
            messages: [
                {
                    role: "user",
                    content: [
                        { type: "text", text: prompt },
                        {
                            type: "image_url",
                            image_url: {
                                url: dataUrl,
                                detail: "auto"
                            }
                        }
                    ],
                }
            ],
            max_tokens: 500,
        });

        const aiText = response.choices[0].message.content;

        // Clean up the temporary uploaded file
        fs.unlinkSync(req.file.path);

        res.json({ response: aiText });
    } catch (error) {
        console.error("Error communicating with OpenAI:", error.message);
        // Clean up on error too
        if (req.file && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }
        res.status(500).json({ error: 'Failed to process image with OpenAI API' });
    }
});

app.listen(port, () => {
    console.log(`ChatGPTShutter secure backend listening on port ${port}`);
    if (!process.env.OPENAI_API_KEY) {
        console.warn("WARNING: OPENAI_API_KEY is not set in the environment variables!");
    }
});