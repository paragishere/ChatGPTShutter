import unittest
from unittest.mock import patch, MagicMock
import app
import io
import json
import os

class TestBackend(unittest.TestCase):
    def setUp(self):
        app.app.config['TESTING'] = True
        self.client = app.app.test_client()

    @patch('app.get_api_keys')
    @patch('requests.post')
    def test_successful_request_first_key(self, mock_post, mock_get_keys):
        mock_get_keys.return_value = ["key1"]

        mock_response = MagicMock()
        mock_response.ok = True
        mock_response.json.return_value = {
            "candidates": [
                {"content": {"parts": [{"text": "Success response"}]}}
            ]
        }
        mock_post.return_value = mock_response

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(json.loads(response.data)["response"], "Success response")
        self.assertEqual(mock_post.call_count, 1)

        # Verify the secure header is used
        headers = mock_post.call_args[1]['headers']
        self.assertEqual(headers["x-goog-api-key"], "key1")

    @patch('app.get_api_keys')
    @patch('requests.post')
    def test_fallback_on_429_quota(self, mock_post, mock_get_keys):
        mock_get_keys.return_value = ["key1", "key2"]

        resp_429 = MagicMock()
        resp_429.ok = False
        resp_429.status_code = 429
        resp_429.json.return_value = {"error": {"message": "Quota exceeded"}}

        resp_200 = MagicMock()
        resp_200.ok = True
        resp_200.json.return_value = {
            "candidates": [
                {"content": {"parts": [{"text": "Success on second key"}]}}
            ]
        }

        mock_post.side_effect = [resp_429, resp_200]

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(json.loads(response.data)["response"], "Success on second key")
        self.assertEqual(mock_post.call_count, 2)

        # Verify both keys were tried via headers
        first_call_headers = mock_post.call_args_list[0][1]['headers']
        second_call_headers = mock_post.call_args_list[1][1]['headers']
        self.assertEqual(first_call_headers["x-goog-api-key"], "key1")
        self.assertEqual(second_call_headers["x-goog-api-key"], "key2")

    @patch('app.get_api_keys')
    @patch('requests.post')
    def test_all_keys_quota_exhausted(self, mock_post, mock_get_keys):
        mock_get_keys.return_value = ["key1", "key2"]

        resp_429 = MagicMock()
        resp_429.ok = False
        resp_429.status_code = 429
        resp_429.json.return_value = {"error": {"message": "Quota exceeded"}}

        mock_post.side_effect = [resp_429, resp_429]

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 429)
        self.assertIn("rate limit or quota reached on all configured projects", json.loads(response.data)["error"])
        self.assertEqual(mock_post.call_count, 2)

    @patch('app.get_api_keys')
    @patch('requests.post')
    def test_invalid_key_fallback(self, mock_post, mock_get_keys):
        mock_get_keys.return_value = ["key1", "key2"]

        resp_401 = MagicMock()
        resp_401.ok = False
        resp_401.status_code = 401
        resp_401.json.return_value = {"error": {"message": "Invalid key"}}

        resp_200 = MagicMock()
        resp_200.ok = True
        resp_200.json.return_value = {
            "candidates": [
                {"content": {"parts": [{"text": "Success on second key"}]}}
            ]
        }

        mock_post.side_effect = [resp_401, resp_200]

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 200)

    @patch('app.get_api_keys')
    @patch('requests.post')
    def test_400_bad_request_no_retry(self, mock_post, mock_get_keys):
        mock_get_keys.return_value = ["key1", "key2"]

        resp_400 = MagicMock()
        resp_400.ok = False
        resp_400.status_code = 400
        resp_400.json.return_value = {"error": {"message": "Bad request payload"}}

        mock_post.side_effect = [resp_400]

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 400)
        self.assertEqual(mock_post.call_count, 1)

    @patch('app.get_api_keys')
    def test_no_keys_configured(self, mock_get_keys):
        mock_get_keys.return_value = []

        data = {
            'prompt': 'test prompt',
            'image': (io.BytesIO(b"fake image data"), 'test.jpg')
        }

        response = self.client.post('/api/analyze-photo', data=data, content_type='multipart/form-data')
        self.assertEqual(response.status_code, 500)
        self.assertIn("Gemini API keys are not configured", json.loads(response.data)["error"])

    @patch.dict(os.environ, {"GEMINI_API_KEYS": "  key1,  , key2  ,key3", "GEMINI_API_KEY": "should_be_ignored"})
    def test_env_parsing_comma_separated(self):
        # Directly test the get_api_keys parsing logic
        keys = app.get_api_keys()
        self.assertEqual(keys, ["key1", "key2", "key3"])

if __name__ == '__main__':
    unittest.main()
