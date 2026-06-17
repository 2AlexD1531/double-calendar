from flask import Flask, request
import requests
import random
import os
import logging
import sys

# Настройка логирования
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    stream=sys.stdout,
    force=True
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

VK_TOKEN = os.getenv('VK_WEBHOOK_TOKEN')
VK_ID = os.getenv('VK_WEBHOOK_ID')

@app.route('/vk-alert', methods=['POST'])
def vk_alert():
    logger.debug("="*50)
    logger.debug("Получен POST запрос на /vk-alert")
    logger.debug(f"Headers: {dict(request.headers)}")

    data = request.json
    logger.debug(f"Received data: {data}")

    message = data.get('message', 'Alert')
    logger.debug(f"Message to send: {message}")

    logger.debug(f"VK_TOKEN exists: {bool(VK_TOKEN)}")
    logger.debug(f"VK_ID: {VK_ID}")

    try:
        resp = requests.post('https://api.vk.com/method/messages.send', data={
            'access_token': VK_TOKEN,
            'v': '5.131',
            'random_id': random.randint(1, 999999999),
            'peer_id': VK_ID,
            'message': message
        })
        logger.debug(f"VK API response status: {resp.status_code}")
        logger.debug(f"VK API response: {resp.text}")
        return resp.json()
    except Exception as e:
        logger.error(f"Error sending to VK: {e}")
        return {"error": str(e)}, 500

@app.route('/health', methods=['GET'])
def health():
    return 'OK', 200

if __name__ == '__main__':
    logger.info("Starting webhook server")
    logger.info(f"VK_TOKEN set: {bool(VK_TOKEN)}")
    logger.info(f"VK_ID: {VK_ID}")
    app.run(host='0.0.0.0', port=5000)