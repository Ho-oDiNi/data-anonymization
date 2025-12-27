#!/usr/bin/env python3
import json
import sys
from io import StringIO
from typing import Optional, Tuple

import pandas as pd

# cat payload.json | python bayesian_network.py --n 500 --target total
# Get-Content payload.json | python bayesian_network.py --n 500 --target total


def parse_payload_to_dataframe(raw_payload: str) -> Tuple[pd.DataFrame, dict]:
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


def synthesize_with_bayesian_network(
    df: pd.DataFrame,
    n_rows: int = 0,
    target_column: Optional[str] = None,
) -> pd.DataFrame:
    """
    Generates synthetic data using SynthCity Bayesian Network plugin.

    Requirements:
      pip install "synthcity[all]" pandas scikit-learn

    Notes:
    - If n_rows <= 0, generates the same number of rows as input.
    - If target_column is provided, it will be passed to GenericDataLoader.
    """
    if df is None or df.empty:
        return pd.DataFrame()

    n = n_rows if n_rows and n_rows > 0 else len(df)

    # Lazy import to keep script usable even without synthcity installed
    from synthcity.plugins import Plugins
    from synthcity.plugins.core.dataloader import GenericDataLoader

    if target_column:
        if target_column not in df.columns:
            raise ValueError(f"target_column '{target_column}' not found in columns: {list(df.columns)}")
        loader = GenericDataLoader(df, target_column=target_column)
    else:
        loader = GenericDataLoader(df)

    plugin = Plugins().get("bayesian_network")
    plugin.fit(loader)

    syn_loader = plugin.generate(count=n)
    syn_df = syn_loader.dataframe()

    return syn_df


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    # Optional config via CLI args:
    #   --n 5000
    #   --target label
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

    # --- Synthesis ---
    try:
        syn_df = synthesize_with_bayesian_network(df, n_rows=n_rows, target_column=target_col)

        # Return synthetic data in JSON to stdout (machine-friendly)
        response = {
            "status": "ok",
            "message": "Синтез выполнен успешно",
            "rows_real": int(len(df)),
            "rows_synth": int(len(syn_df)),
            "data_synth": syn_df.to_dict(orient="records"),
        }
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
