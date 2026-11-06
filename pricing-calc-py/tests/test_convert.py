import json
from decimal import Decimal

import pytest

from pricing_calc import UnsupportedPair, convert


def load_cases(path):
    return json.loads(path.read_text())["cases"]


def test_golden_vectors(golden_vectors_path):
    cases = load_cases(golden_vectors_path)
    assert cases, "golden vectors file has no cases"
    for case in cases:
        got = convert(Decimal(case["amount"]), case["from"], case["to"])
        assert str(got) == case["expected"], case


# The incumbent's own unit tests cover banker's rounding directly, including
# half-penny cases with an even digit before the 5, where HALF_UP would differ.
@pytest.mark.parametrize(
    ("amount", "from_", "to", "expected"),
    [
        ("10.02", "GBP", "USD", "12.52"),  # 12.525 -> even 2 stays
        ("10.06", "GBP", "USD", "12.58"),  # 12.575 -> odd 7 rounds up
        ("0.02", "GBP", "USD", "0.02"),    # 0.025
        ("0.10", "EUR", "USD", "0.10"),    # 0.105
        ("10.10", "EUR", "USD", "10.60"),  # 10.605
        ("1.50", "GBP", "EUR", "1.78"),    # 1.785
    ],
)
def test_half_penny_rounds_half_even(amount, from_, to, expected):
    assert str(convert(Decimal(amount), from_, to)) == expected


def test_unsupported_pair():
    with pytest.raises(UnsupportedPair):
        convert(Decimal("1.00"), "USD", "GBP")


def test_negative_amount():
    with pytest.raises(ValueError):
        convert(Decimal("-1.00"), "GBP", "USD")
