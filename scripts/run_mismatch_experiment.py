#!/usr/bin/env python3
import argparse
import uuid
from datetime import datetime, timezone

import pandas as pd

from synthcity.plugins import Plugins
from synthcity.plugins.core.dataloader import GenericDataLoader
from synthcity.metrics import Metrics


def compute_data_mismatch(real_df: pd.DataFrame, synth_df: pd.DataFrame) -> float:
    """
    Возвращает data_mismatch (float).
    В вашей версии synthcity Metrics.evaluate возвращает DataFrame summary,
    где mean первой строки соответствует метрике, если запрошена одна метрика.
    """
    metrics = Metrics.evaluate(
        real_df,
        synth_df,
        metrics={"sanity": ["data_mismatch"]},
    )

    # summary DataFrame: берем mean первой строки
    return float(metrics["mean"].iloc[0])


def run_experiment(
    csv_path: str,
    algorithm: str,
    out_path: str,
    chunk_size: int = 192,
    num_chunks: int = 11,
) -> None:
    df = pd.read_csv(csv_path)
    run_id = str(uuid.uuid4())
    ts = datetime.now(timezone.utc).isoformat()

    results = []
    plugin = Plugins().get(algorithm)

    for i in range(1, num_chunks + 1):
        current_size = i * chunk_size
        df_part = df.iloc[:current_size].reset_index(drop=True)

        # fit -> generate
        plugin.fit(GenericDataLoader(df_part))
        df_synth = plugin.generate(count=len(df_part)).dataframe()

        data_mismatch = compute_data_mismatch(df_part, df_synth)

        # 0..100%, где 100% = 1 - mismatch
        score_percent = (1.0 - data_mismatch) * 100.0

        results.append(
            {
                "timestamp_utc": ts,
                "run_id": run_id,
                "algorithm": algorithm,
                "rows": int(current_size),
                "data_mismatch": float(data_mismatch),
                "score_percent": float(score_percent),
                "chunk_size": int(chunk_size),
                "num_chunks": int(num_chunks),
                "source_csv": csv_path,
            }
        )

        print(
            f"{algorithm} | rows={current_size:4d} | "
            f"data_mismatch={data_mismatch:.6f} | score={score_percent:.2f}%"
        )

    out_df = pd.DataFrame(results)

    # дописываем в общий файл (накапливаем результаты разных алгоритмов)
    try:
        existing = pd.read_csv(out_path)
        combined = pd.concat([existing, out_df], ignore_index=True)
        combined.to_csv(out_path, index=False)
    except FileNotFoundError:
        out_df.to_csv(out_path, index=False)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default="UCIObesityDataSet.csv", help="Путь к исходному CSV")
    parser.add_argument(
        "--algorithm",
        default="bayesian_network",
        help="Имя плагина synthcity (например: bayesian_network, ctgan, tvae, tabddpm)",
    )
    parser.add_argument("--out", default="mismatch_results.csv", help="Файл для записи результатов")
    parser.add_argument("--chunk-size", type=int, default=192)
    parser.add_argument("--num-chunks", type=int, default=11)

    args = parser.parse_args()

    run_experiment(
        csv_path=args.csv,
        algorithm=args.algorithm,
        out_path=args.out,
        chunk_size=args.chunk_size,
        num_chunks=args.num_chunks,
    )


if __name__ == "__main__":
    main()
