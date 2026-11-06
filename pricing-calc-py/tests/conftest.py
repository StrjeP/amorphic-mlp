from pathlib import Path

import pytest

GOLDEN_VECTORS = Path(__file__).resolve().parents[2] / "amorphic-demo-env" / "golden" / "vectors.json"


@pytest.fixture(scope="session")
def golden_vectors_path() -> Path:
    return GOLDEN_VECTORS
