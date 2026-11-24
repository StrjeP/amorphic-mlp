from fastapi.testclient import TestClient

from pricing_calc.app import app

client = TestClient(app)


def test_convert():
    r = client.get("/convert", params={"amount": "10.02", "from": "GBP", "to": "USD"})
    assert r.status_code == 200
    assert r.json() == {"amount": "12.52", "currency": "USD"}


def test_convert_keeps_two_decimal_places():
    r = client.get("/convert", params={"amount": "100", "from": "GBP", "to": "USD"})
    assert r.json()["amount"] == "125.00"


def test_unsupported_pair_is_400():
    r = client.get("/convert", params={"amount": "1.00", "from": "USD", "to": "JPY"})
    assert r.status_code == 400
    assert "unsupported" in r.json()["error"]


def test_invalid_amount_is_400():
    for bad in ("abc", "NaN", "-1"):
        r = client.get("/convert", params={"amount": bad, "from": "GBP", "to": "USD"})
        assert r.status_code == 400, bad


def test_health():
    assert client.get("/health").json() == {"status": "ok"}


def test_meta_says_who_this_is(monkeypatch):
    monkeypatch.setenv("AMORPHIC_INSTANCE", "py-pricing-1.0")
    r = client.get("/meta")
    assert r.status_code == 200
    assert r.json() == {
        "identity": "py-pricing-1.0",
        "realises": "pkg:amorphic/pricing-calc@1.0?lang=python",
        "host": None,
    }


def test_meta_without_a_declared_identity_is_503(monkeypatch):
    monkeypatch.delenv("AMORPHIC_INSTANCE", raising=False)
    r = client.get("/meta")
    assert r.status_code == 503
    assert "AMORPHIC_INSTANCE" in r.json()["error"]

