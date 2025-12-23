#!/usr/bin/env python3
import json
import sys


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    raw_payload = sys.stdin.read()
    print("Получены данные:")
    print(raw_payload)

    response = {
        "status": "ok",
        "message": "Код выполнен успешно"
    }
    print(json.dumps(response, ensure_ascii=False))


if __name__ == "__main__":
    main()
