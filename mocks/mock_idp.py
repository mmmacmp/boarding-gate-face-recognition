"""mock-idp: a MOCK stand-in for an external government identity provider.

Hypothetical API invented for this POC; it does not model any real system's interface.
It holds reference images (the identity anchor), answers a 1:1 confirmation, and supports fault injection.
"""
import asyncio
import random
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

Status = Literal["VALID", "INVALID"]


class Faults(BaseModel):
    latency_ms: int = Field(0, ge=0, le=30_000)
    error_rate: float = Field(0.0, ge=0.0, le=1.0)
    down: bool = False


class VerifyRequest(BaseModel):
    profile_id: str
    flight_id: str


class VerifyResponse(BaseModel):
    profile_id: str
    flight_id: str
    result: Status


class StatusUpdate(BaseModel):
    status: Status


# In-memory state: a mock, lost on restart (re-run tools/seed.py).
_faults = Faults()
_profiles: dict[str, dict] = {}  # profile_id -> {"image": bytes, "content_type": str, "status": Status}


def reset():
    global _faults
    _faults = Faults()
    _profiles.clear()


async def apply_faults():
    """Runs before every public mock-idp endpoint (not the admin ones), so faults can be switched off again."""
    if _faults.down:
        raise HTTPException(503, "mock-idp is down (fault injection)")
    if _faults.latency_ms:
        await asyncio.sleep(_faults.latency_ms / 1000)
    if _faults.error_rate and random.random() < _faults.error_rate:
        raise HTTPException(500, "mock-idp injected error")


router = APIRouter(tags=["mock-idp"], dependencies=[Depends(apply_faults)])
admin = APIRouter(prefix="/admin", tags=["mock-idp admin"])


@router.get("/v1/profiles/{profile_id}/reference-image")
def reference_image(profile_id: str) -> Response:
    p = _profiles.get(profile_id)
    if p is None:
        raise HTTPException(404, "unknown profile")
    return Response(content=p["image"], media_type=p["content_type"])


@router.post("/v1/verify", response_model=VerifyResponse)
def verify(req: VerifyRequest) -> VerifyResponse:
    # 1:1 confirmation that this profile is valid for travel. Unknown profiles are INVALID, not an error.
    p = _profiles.get(req.profile_id)
    result = p["status"] if p else "INVALID"
    return VerifyResponse(profile_id=req.profile_id, flight_id=req.flight_id, result=result)


@admin.put("/profiles/{profile_id}/reference-image", status_code=204)
async def put_reference_image(profile_id: str, request: Request):
    data = await request.body()
    if not data:
        raise HTTPException(400, "empty body")
    content_type = request.headers.get("content-type", "image/jpeg")
    status = _profiles.get(profile_id, {}).get("status", "VALID")
    _profiles[profile_id] = {"image": data, "content_type": content_type, "status": status}


@admin.put("/profiles/{profile_id}/status", status_code=204)
def put_status(profile_id: str, update: StatusUpdate):
    if profile_id not in _profiles:
        raise HTTPException(404, "unknown profile")
    _profiles[profile_id]["status"] = update.status


@admin.get("/profiles")
def list_profiles() -> dict[str, Status]:
    return {pid: p["status"] for pid, p in _profiles.items()}  # ids and status only, never images


@admin.get("/faults", response_model=Faults)
def get_faults() -> Faults:
    return _faults


@admin.post("/faults", response_model=Faults)
def set_faults(faults: Faults) -> Faults:
    global _faults
    _faults = faults
    return _faults
