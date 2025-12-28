#!/usr/bin/env python3
# ddpm.py
import json
import sys
from io import StringIO
from typing import Optional

import pandas as pd

# cat payload.json | python ddpm.py --n 500 --target total
# Get-Content payload.json | python ddpm.py --n 500 --target total


def parse_payload_to_dataframe(raw_payload: str) -> tuple[pd.DataFrame, dict]:
    cleaned_payload = raw_payload.strip()
    if not cleaned_payload:
        return pd.DataFrame(), {}

    try:
        parsed = json.loads(cleaned_payload)
        if isinstance(parsed, dict) and {"columns", "rows"}.issubset(parsed.keys()):
            df = pd.DataFrame(parsed.get("rows", []), columns=parsed.get("columns", []))
            config = (
                parsed.get("config", {})
                if isinstance(parsed.get("config", {}), dict)
                else {}
            )
            return df, config
    except json.JSONDecodeError:
        pass

    if cleaned_payload.startswith("{") or cleaned_payload.startswith("["):
        return pd.read_json(StringIO(cleaned_payload)), {}

    return pd.read_csv(StringIO(cleaned_payload)), {}


def _select_target_column(
    df: pd.DataFrame, target_column: Optional[str]
) -> tuple[Optional[str], list[str]]:
    """Проверяет целевую колонку и возвращает (target_column|None, warnings)."""
    if not target_column:
        return None, []

    if target_column not in df.columns:
        raise ValueError(
            f"target_column '{target_column}' not found in columns: {list(df.columns)}"
        )

    return target_column, []


def _extract_plugin_args(config: dict, key: str) -> dict:
    """Поддерживаем config.plugin_args и config.<key> (например ddpm_args)."""
    raw = config.get("plugin_args", None)
    if raw is None:
        raw = config.get(key, None)

    if raw is None:
        return {}

    if not isinstance(raw, dict):
        raise ValueError(
            f"config['plugin_args'] / config['{key}'] must be a dict, got: {type(raw)}"
        )
    return raw


INSUFFICIENT_DATA_MESSAGE = "Недостаточно данных для выполнения синтеза"


def synthesize_with_ddpm(
    df: pd.DataFrame,
    n_rows: int = 0,
    target_column: Optional[str] = None,
    plugin_args: Optional[dict] = None,
) -> tuple[pd.DataFrame, list[str]]:
    """
    Генерирует синтетические данные с помощью SynthCity TabDDPM (plugin id: 'ddpm').

    Requirements:
      pip install "synthcity[all]" pandas

    Notes:
    - If n_rows <= 0, generates the same number of rows as input.
    - If target_column is provided, it will be passed to GenericDataLoader.
    - Additional plugin kwargs can be passed via config.plugin_args or config.ddpm_args.
    """
    if df is None or df.empty:
        return df.copy() if df is not None else pd.DataFrame(), [INSUFFICIENT_DATA_MESSAGE]

    n = int(n_rows) if int(n_rows) > 0 else int(len(df))
    warnings: list[str] = []

    selected_target, target_warnings = _select_target_column(df, target_column)
    warnings.extend(target_warnings)

    try:
        from synthcity.plugins import Plugins
        from synthcity.plugins.core.dataloader import GenericDataLoader
    except Exception as e:
        raise RuntimeError(
            "synthcity не установлен или не может быть импортирован. "
            "Установите: pip install \"synthcity[all]\""
        ) from e

    if selected_target:
        loader = GenericDataLoader(df, target_column=selected_target)
    else:
        loader = GenericDataLoader(df)

    kwargs = dict(plugin_args or {})
    plugin = Plugins().get("ddpm", **kwargs)

    try:
        plugin.fit(loader)
        syn_loader = plugin.generate(count=n)
        return syn_loader.dataframe(), warnings
    except (ValueError, RuntimeError):
        return df.copy(), [INSUFFICIENT_DATA_MESSAGE, *warnings]


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

    plugin_args = {}
    if config:
        n_rows = int(config.get("n", n_rows) or 0)
        target_col = config.get("target") or target_col
        plugin_args = _extract_plugin_args(config, "ddpm_args")

    try:
        syn_df, warnings = synthesize_with_ddpm(
            df, n_rows=n_rows, target_column=target_col, plugin_args=plugin_args
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
