from decimal import ROUND_HALF_EVEN, Decimal

# Fixed rate table. Rates are chosen so some inputs convert to exactly half a
# penny (e.g. 10.02 GBP -> 12.525 USD), where the rounding rule matters.
RATES: dict[tuple[str, str], Decimal] = {
    ("GBP", "USD"): Decimal("1.25"),
    ("EUR", "USD"): Decimal("1.05"),
    ("GBP", "EUR"): Decimal("1.19"),
}

CENT = Decimal("0.01")


class UnsupportedPair(ValueError):
    pass


def convert(amount: Decimal, from_currency: str, to_currency: str) -> Decimal:
    """Convert amount and round to 2 dp using banker's rounding (HALF_EVEN).

    HALF_EVEN is also Python's default decimal context rounding; it is passed
    explicitly here because it is the behaviour every port must match.
    """
    if amount < 0:
        raise ValueError("amount must not be negative")
    rate = RATES.get((from_currency, to_currency))
    if rate is None:
        raise UnsupportedPair(f"unsupported currency pair {from_currency}->{to_currency}")
    return (amount * rate).quantize(CENT, rounding=ROUND_HALF_EVEN)
