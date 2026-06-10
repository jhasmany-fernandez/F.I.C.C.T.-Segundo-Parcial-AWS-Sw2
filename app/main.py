import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel


APP_NAME = "sw2-ai-aws"
BASE_DIR = Path(__file__).resolve().parent.parent
IMAGE_DIR = BASE_DIR / "evidencias" / "imagenes"
AUDIO_DIR = BASE_DIR / "evidencias" / "audios"
STORAGE_DIR = BASE_DIR / "storage"
S3_IMAGE_DIR = STORAGE_DIR / "s3" / "evidencias" / "imagenes"
S3_AUDIO_DIR = STORAGE_DIR / "s3" / "evidencias" / "audios"
DYNAMODB_FILE = STORAGE_DIR / "dynamodb" / "evidencias_metadata.json"
BLOCKCHAIN_FILE = STORAGE_DIR / "blockchain" / "audit_chain.json"

IMAGE_DIR.mkdir(parents=True, exist_ok=True)
AUDIO_DIR.mkdir(parents=True, exist_ok=True)
S3_IMAGE_DIR.mkdir(parents=True, exist_ok=True)
S3_AUDIO_DIR.mkdir(parents=True, exist_ok=True)
DYNAMODB_FILE.parent.mkdir(parents=True, exist_ok=True)
BLOCKCHAIN_FILE.parent.mkdir(parents=True, exist_ok=True)

if not DYNAMODB_FILE.exists():
    DYNAMODB_FILE.write_text("[]", encoding="utf-8")

if not BLOCKCHAIN_FILE.exists():
    BLOCKCHAIN_FILE.write_text("[]", encoding="utf-8")

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


class DynamoEvidenceRequest(BaseModel):
    emergencia_id: str
    bucket: str
    key: str
    tipo: str
    url_simulada: str
    size_bytes: int
    content_type: str | None = None


class BlockchainRegisterRequest(BaseModel):
    emergencia_id: str
    evento: str
    timestamp: str | None = None


class N8NWebhookRequest(BaseModel):
    emergencia_id: str
    descripcion: str


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


def read_json_file(file_path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(file_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=500, detail=f"JSON invalido en {file_path.name}") from exc

    if not isinstance(data, list):
        raise HTTPException(status_code=500, detail=f"Estructura invalida en {file_path.name}")

    return data


def write_json_file(file_path: Path, data: list[dict[str, Any]]) -> None:
    file_path.write_text(json.dumps(data, indent=2, ensure_ascii=True), encoding="utf-8")


def resolve_s3_target(content_type: str | None, filename: str | None) -> tuple[Path, str]:
    normalized_type = (content_type or "").lower()
    normalized_name = (filename or "").lower()

    if normalized_type.startswith("audio/") or normalized_name.endswith((".mp3", ".wav", ".ogg", ".m4a")):
        return S3_AUDIO_DIR, "audios"
    return S3_IMAGE_DIR, "imagenes"


def build_simulated_hash(payload: dict[str, Any]) -> str:
    serialized = json.dumps(payload, sort_keys=True, ensure_ascii=True)
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


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


@app.post("/aws-s3/upload-evidencia")
async def aws_s3_upload_evidencia(
    emergencia_id: str = Form(...),
    archivo: UploadFile = File(...),
) -> dict:
    target_dir, evidence_type = resolve_s3_target(archivo.content_type, archivo.filename)
    metadata = await save_upload_file(archivo, target_dir, emergencia_id)
    key = f"evidencias/{evidence_type}/{metadata['filename_guardado']}"

    return {
        "bucket": "sw2-ai-aws-simulado",
        "key": key,
        "url_simulada": f"s3://sw2-ai-aws-simulado/{key}",
        "size_bytes": metadata["size_bytes"],
    }


@app.post("/dynamodb/evidencia")
def dynamodb_create_evidencia(payload: DynamoEvidenceRequest) -> dict:
    items = read_json_file(DYNAMODB_FILE)
    record = {
        **payload.model_dump(),
        "registrado_en": datetime.now(timezone.utc).isoformat(),
    }
    items.append(record)
    write_json_file(DYNAMODB_FILE, items)
    return record


@app.get("/dynamodb/evidencias/{emergencia_id}")
def dynamodb_get_evidencias(emergencia_id: str) -> dict:
    items = read_json_file(DYNAMODB_FILE)
    filtered = [item for item in items if item.get("emergencia_id") == emergencia_id]
    return {"emergencia_id": emergencia_id, "total": len(filtered), "items": filtered}


@app.post("/blockchain/registrar")
def blockchain_registrar(payload: BlockchainRegisterRequest) -> dict:
    chain = read_json_file(BLOCKCHAIN_FILE)
    previous_hash = chain[-1]["hash"] if chain else "GENESIS"
    timestamp = payload.timestamp or datetime.now(timezone.utc).isoformat()
    block_core = {
        "emergencia_id": payload.emergencia_id,
        "evento": payload.evento,
        "timestamp": timestamp,
        "previous_hash": previous_hash,
    }
    block = {
        "index": len(chain) + 1,
        **block_core,
        "hash": build_simulated_hash(block_core),
    }
    chain.append(block)
    write_json_file(BLOCKCHAIN_FILE, chain)
    return block


@app.get("/blockchain/chain")
def blockchain_chain() -> dict:
    chain = read_json_file(BLOCKCHAIN_FILE)
    return {"total": len(chain), "chain": chain}


@app.post("/ai/deep-learning/vision")
async def deep_learning_vision(
    emergencia_id: str = Form(...),
    imagen: UploadFile = File(...),
) -> dict:
    metadata = await save_upload_file(imagen, IMAGE_DIR, emergencia_id)
    signal = f"{metadata['filename_original']}|{metadata['size_bytes']}".casefold()
    deteccion = "falla_motor"
    confidence = 0.91

    if "bateria" in signal:
        deteccion = "bateria_descargada"
        confidence = 0.89
    elif "llanta" in signal or "neumatico" in signal:
        deteccion = "pinchazo_llanta"
        confidence = 0.9

    return {
        "modelo": "cnn_simulado",
        "deteccion": deteccion,
        "confidence": confidence,
    }


@app.post("/automation/n8n/webhook")
def automation_n8n_webhook(payload: N8NWebhookRequest) -> dict:
    clasificacion = classify_text(payload.descripcion)
    return {
        "emergencia_id": payload.emergencia_id,
        "flujo": "n8n_simulado",
        "resultado_ia": clasificacion.model_dump(),
        "notificacion": (
            f"Email simulado generado para emergencia {payload.emergencia_id} "
            f"con categoria {clasificacion.categoria}"
        ),
        "pasos_ejecutados": 3,
    }


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
