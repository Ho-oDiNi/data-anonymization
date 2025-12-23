#!/usr/bin/env python3
import json
import sys
from io import StringIO

import pandas as pd


def parse_payload_to_dataframe(raw_payload: str) -> pd.DataFrame:
    cleaned_payload = raw_payload.strip()
    if not cleaned_payload:
        return pd.DataFrame()

    if cleaned_payload.startswith("{") or cleaned_payload.startswith("["):
        return pd.read_json(StringIO(cleaned_payload))

    return pd.read_csv(StringIO(cleaned_payload))


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    raw_payload = sys.stdin.read()
    print("Получены данные:")
    print(raw_payload)

    data_frame = parse_payload_to_dataframe(raw_payload)
    print("Данные в формате Pandas:")
    print(data_frame)

    response = {
        "status": "ok",
        "message": "Код выполнен успешно"
    }
    print(json.dumps(response, ensure_ascii=False))


if __name__ == "__main__":
    main()
