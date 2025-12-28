#!/usr/bin/env python3
import json
import sys
from io import StringIO
from typing import Optional

import pandas as pd

# cat payload.json | python tgan.py --n 500 --target total
# Get-Content payload.json | python tgan.py --n 500 --target total


def parse_payload_to_dataframe(raw_payload: str) -> tuple[pd.DataFrame, dict]:
    cleaned_payload = raw_payload.strip()
    if not cleaned_payload:
        return pd.DataFrame(), {}

    try:
        parsed = json.loads(cleaned_payload)
        if isinstance(parsed, dict) and {"columns", "rows"}.issubset(parsed.keys()):
            df = pd.DataFrame(parsed.get("rows", []), columns=parsed.get("columns", []))
            config = parsed.get("config", {}) if isinstance(parsed.get("config", {}), dict) else {}
            return df, config
    except json.JSONDecodeError:
        pass

    if cleaned_payload.startswith("{") or cleaned_payload.startswith("["):
        return pd.read_json(StringIO(cleaned_payload)), {}

    return pd.read_csv(StringIO(cleaned_payload)), {}


def _select_target_column(df: pd.DataFrame, target_column: Optional[str]) -> tuple[Optional[str], list[str]]:
    """Проверяет целевую колонку и, при необходимости, отключает её для стабильной работы.

    Возвращает фактически используемую колонку и список предупреждений.
    Отключение выполняется, если хотя бы один класс содержит меньше трёх записей,
    поскольку SynthCity применяет кросс-валидацию с тремя фолдами и падает при
    недостатке объектов в классах.
    """

    if not target_column:
        return None, []

    if target_column not in df.columns:
        raise ValueError(
            f"target_column '{target_column}' not found in columns: {list(df.columns)}"
        )

    class_distribution = df[target_column].value_counts(dropna=False)
    min_class_size = int(class_distribution.min()) if not class_distribution.empty else 0

    if min_class_size < 3:
        warning = (
            "Целевая колонка отключена: обнаружен класс с количеством наблюдений меньше 3, "
            "что несовместимо с кросс-валидацией TGAN. Синтез выполнен без учёта целевой переменной."
        )
        return None, [warning]

    return target_column, []


INSUFFICIENT_DATA_MESSAGE = "Недостаточно данных для выполнения синтеза"


def synthesize_with_adsgan(
    df: pd.DataFrame,
    n_rows: int = 0,
    target_column: Optional[str] = None,
) -> tuple[pd.DataFrame, list[str]]:
    """
    Генерирует синтетические данные с помощью SynthCity ADSGAN.

    Requirements:
      pip install "synthcity[all]" pandas scikit-learn

    Notes:
    - If n_rows <= 0, generates the same number of rows as input.
    - If target_column is provided, it will be passed to GenericDataLoader.
    """
    if df is None:
        return pd.DataFrame(), []

    if len(df) < 3:
        return df.copy(), [INSUFFICIENT_DATA_MESSAGE]

    n = n_rows if n_rows and n_rows > 0 else len(df)

    from synthcity.plugins import Plugins
    from synthcity.plugins.core.dataloader import GenericDataLoader

    selected_target, warnings = _select_target_column(df, target_column)

    if selected_target:
        loader = GenericDataLoader(df, target_column=selected_target)
    else:
        loader = GenericDataLoader(df)

    plugin = Plugins().get("adsgan")

    try:
        plugin.fit(loader)
        syn_loader = plugin.generate(count=n)
        return syn_loader.dataframe(), warnings
    except ValueError:
        fallback_warning = INSUFFICIENT_DATA_MESSAGE
        return df.copy(), [fallback_warning, *warnings]


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    n_rows = 0
    target_col: Optional[str] = None

    args = sys.argv[1:]
    if "--n" in args:
        i = args.index("--n")
        if i + 1 >= len(args):
            raise ValueError("Missing value after --n")
        n_rows = int(args[i + 1])

    if "--target" in args:
        i = args.index("--target")
        if i + 1 >= len(args):
            raise ValueError("Missing value after --target")
        target_col = args[i + 1]

    raw_payload = sys.stdin.read()
    df, config = parse_payload_to_dataframe(raw_payload)

    if config:
        n_rows = int(config.get("n", n_rows) or 0)
        target_col = config.get("target") or target_col

    try:
        syn_df, warnings = synthesize_with_adsgan(
            df, n_rows=n_rows, target_column=target_col
        )

        message = "Синтез выполнен успешно"
        if INSUFFICIENT_DATA_MESSAGE in warnings:
            message = INSUFFICIENT_DATA_MESSAGE

        response = {
            "status": "ok",
            "message": message,
            "rows_real": int(len(df)),
            "rows_synth": int(len(syn_df)),
            "data_synth": syn_df.to_dict(orient="records"),
        }
        if warnings:
            response["warnings"] = warnings
        print(json.dumps(response, ensure_ascii=False))

    except Exception as e:
        response = {
            "status": "error",
            "message": "Ошибка синтеза данных",
            "error": str(e),
        }
        print(json.dumps(response, ensure_ascii=False))
        raise


if __name__ == "__main__":
    main()
