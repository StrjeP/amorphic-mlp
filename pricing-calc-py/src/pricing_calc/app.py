import os
from decimal import Decimal, InvalidOperation

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

from pricing_calc.convert import UnsupportedPair, convert

VERSION = "1.0"
# What this service realises, from the code itself, so it can't claim a version it isn't (spec 03, section 6).
REALISES = f"pkg:amorphic/pricing-calc@{VERSION}?lang=python"

app = FastAPI(title="pricing-calc", version=VERSION)


def _error(message: str) -> JSONResponse:
    return JSONResponse(status_code=400, content={"error": message})


@app.get("/convert")
def convert_endpoint(
    amount: str,
    from_currency: str = Query(alias="from"),
    to_currency: str = Query(alias="to"),
):
    try:
        value = Decimal(amount)
    except InvalidOperation:
        return _error(f"invalid amount '{amount}'")
    if not value.is_finite():
        return _error(f"invalid amount '{amount}'")
    try:
        result = convert(value, from_currency, to_currency)
    except UnsupportedPair as e:
        return _error(str(e))
    except ValueError as e:
        return _error(str(e))
    return {"amount": str(result), "currency": to_currency}


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/meta")
def meta():
    """Who this service is, for the caller's handshake. The identity is the one it was declared with, passed in by
    whoever deployed it; with none, it says so rather than make one up."""
    identity = os.environ.get("AMORPHIC_INSTANCE", "").strip()
    if not identity:
        return JSONResponse(
            status_code=503,
            content={"error": "no identity was declared for this instance (AMORPHIC_INSTANCE is not set)"},
        )
    return {"identity": identity, "realises": REALISES, "host": None}
