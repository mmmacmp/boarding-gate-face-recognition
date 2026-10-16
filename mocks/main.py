"""MOCK external systems for the POC: mock-idp (identity provider stand-in) + dcs-stub (departure control).

Run: cd mocks; uvicorn main:app --port 8002
"""
from fastapi import FastAPI

import dcs_stub
import mock_idp

app = FastAPI(title="poc mocks: mock-idp + dcs-stub",
              description="Hypothetical mock APIs for a personal prototype. Not real integrations.")
app.include_router(mock_idp.router)
app.include_router(mock_idp.admin)
app.include_router(mock_idp.demo)
app.include_router(dcs_stub.router)
app.include_router(dcs_stub.admin)


@app.post("/admin/reset", status_code=204, tags=["admin"])
def reset_all():
    mock_idp.reset()
    dcs_stub.reset()


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
