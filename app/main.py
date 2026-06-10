from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel


APP_NAME = "sw2-ai-aws"
BASE_DIR = Path(__file__).resolve().parent.parent
IMAGE_DIR = BASE_DIR / "evidencias" / "imagenes"
AUDIO_DIR = BASE_DIR / "evidencias" / "audios"

IMAGE_DIR.mkdir(parents=True, exist_ok=True)
AUDIO_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title=APP_NAME, version="1.0.0")


class SpeechToTextResponse(BaseModel):
    texto: str
    simulado: bool


class ClasificarIncidenteRequest(BaseModel):
    texto: str


class ClasificarIncidenteResponse(BaseModel):
    categoria: str
    prioridad: str
    confidence: float


def build_safe_filename(prefix: str, original_name: str) -> str:
    extension = Path(original_name or "").suffix.lower()[:10]
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    return f"{prefix}_{timestamp}_{uuid4().hex[:8]}{extension}"


async def save_upload_file(upload: UploadFile, target_dir: Path, emergencia_id: str) -> dict:
    filename = build_safe_filename(emergencia_id, upload.filename or "archivo.bin")
    file_path = target_dir / filename
    content = await upload.read()
    file_path.write_bytes(content)

    return {
        "emergencia_id": emergencia_id,
        "filename_original": upload.filename,
        "filename_guardado": filename,
        "content_type": upload.content_type,
        "size_bytes": len(content),
        "ruta": str(file_path.relative_to(BASE_DIR)),
        "creado_en": datetime.now(timezone.utc).isoformat(),
    }


def classify_text(texto: str) -> ClasificarIncidenteResponse:
    normalized = texto.casefold()

    if "accidente" in normalized:
        return ClasificarIncidenteResponse(
            categoria="accidente",
            prioridad="CRITICA",
            confidence=0.98,
        )
    if "motor" in normalized:
        return ClasificarIncidenteResponse(
            categoria="falla_motor",
            prioridad="ALTA",
            confidence=0.93,
        )
    if "bateria" in normalized:
        return ClasificarIncidenteResponse(
            categoria="bateria",
            prioridad="MEDIA",
            confidence=0.89,
        )
    if "combustible" in normalized:
        return ClasificarIncidenteResponse(
            categoria="combustible",
            prioridad="BAJA",
            confidence=0.86,
        )
    if "llaves" in normalized or "cerrajeria" in normalized:
        return ClasificarIncidenteResponse(
            categoria="cerrajeria",
            prioridad="BAJA",
            confidence=0.87,
        )

    return ClasificarIncidenteResponse(
        categoria="general",
        prioridad="MEDIA",
        confidence=0.6,
    )


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": APP_NAME}


@app.post("/ai/evidencias/imagen")
async def upload_image(
    emergencia_id: str = Form(...),
    imagen: UploadFile = File(...),
) -> dict:
    metadata = await save_upload_file(imagen, IMAGE_DIR, emergencia_id)
    metadata["tipo"] = "imagen"
    return metadata


@app.post("/ai/evidencias/audio")
async def upload_audio(
    emergencia_id: str = Form(...),
    audio: UploadFile = File(...),
) -> dict:
    metadata = await save_upload_file(audio, AUDIO_DIR, emergencia_id)
    metadata["tipo"] = "audio"
    return metadata


@app.post("/ai/speech-to-text", response_model=SpeechToTextResponse)
async def speech_to_text(
    emergencia_id: str = Form(...),
    audio: UploadFile = File(...),
) -> SpeechToTextResponse:
    await save_upload_file(audio, AUDIO_DIR, emergencia_id)
    return SpeechToTextResponse(
        texto="Transcripcion simulada: vehiculo con inconveniente reportado por el cliente.",
        simulado=True,
    )


@app.post("/ai/clasificar-incidente", response_model=ClasificarIncidenteResponse)
def clasificar_incidente(
    payload: ClasificarIncidenteRequest,
) -> ClasificarIncidenteResponse:
    return classify_text(payload.texto)
