"""dcs-stub: a MOCK departure control system. Provides the flight manifest and receives boarding events.

Hypothetical API invented for this POC.
"""
from datetime import datetime

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel


class Passenger(BaseModel):
    profile_id: str
    seat: str | None = None


class Manifest(BaseModel):
    flight_id: str
    departure_time: datetime
    passengers: list[Passenger]


class BoardingEvent(BaseModel):
    event_id: str       # the gate-service's id; used to de-duplicate at-least-once deliveries
    flight_id: str
    profile_id: str
    gate_id: str
    boarded_at: datetime


_manifests: dict[str, Manifest] = {}
_events: dict[str, BoardingEvent] = {}


def reset():
    _manifests.clear()
    _events.clear()


router = APIRouter(tags=["dcs-stub"])
admin = APIRouter(prefix="/admin", tags=["dcs-stub admin"])


@router.get("/v1/flights/{flight_id}/manifest", response_model=Manifest)
def manifest(flight_id: str) -> Manifest:
    m = _manifests.get(flight_id)
    if m is None:
        raise HTTPException(404, "unknown flight")
    return m


@router.post("/v1/boarding-events", status_code=202)
def boarding_event(event: BoardingEvent) -> dict:
    duplicate = event.event_id in _events
    _events.setdefault(event.event_id, event)  # idempotent consumer: a redelivered event changes nothing
    return {"accepted": True, "duplicate": duplicate}


@router.get("/v1/boarding-events", response_model=list[BoardingEvent])
def list_boarding_events(flight_id: str | None = None) -> list[BoardingEvent]:
    return [e for e in _events.values() if flight_id is None or e.flight_id == flight_id]


@admin.put("/flights/{flight_id}/manifest", status_code=204)
def put_manifest(flight_id: str, m: Manifest):
    if m.flight_id != flight_id:
        raise HTTPException(400, "flight_id in path and body differ")
    _manifests[flight_id] = m
