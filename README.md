# SW2-AI-AWS

Microservicio IA simulado y liviano construido con FastAPI y Docker, pensado para una VM con poco espacio en disco.

## Endpoints

- `GET /health`
- `POST /ai/evidencias/imagen`
- `POST /ai/evidencias/audio`
- `POST /ai/speech-to-text`
- `POST /ai/clasificar-incidente`

## Reglas de clasificacion

- `motor` -> `ALTA`
- `accidente` -> `CRITICA`
- `bateria` -> `MEDIA`
- `combustible` -> `BAJA`
- `llaves` o `cerrajeria` -> `BAJA`

## Ejecutar con Docker

```bash
docker compose up -d --build
```

## Validacion

```bash
curl http://localhost:8010/health
curl -X POST http://localhost:8010/ai/clasificar-incidente \
  -H "Content-Type: application/json" \
  -d '{"texto":"accidente en ruta con falla de motor"}'
```

## Notas

- No usa modelos pesados.
- No usa Whisper real.
- No usa OpenCV.
- No usa TensorFlow.
- No usa PyTorch.
