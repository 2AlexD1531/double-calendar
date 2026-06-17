FROM python:3.11-alpine

WORKDIR /app

RUN pip install --no-cache-dir flask requests

COPY webhook-server.py .

EXPOSE 5000

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:5000/health || exit 1

CMD ["python", "webhook-server.py"]