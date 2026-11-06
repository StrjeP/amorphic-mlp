from decimal import Decimal, InvalidOperation

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

from pricing_calc.convert import UnsupportedPair, convert

app = FastAPI(title="pricing-calc", version="1.0")


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
