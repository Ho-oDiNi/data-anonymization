#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt

from synthcity.plugins import Plugins
from synthcity.plugins.core.dataloader import GenericDataLoader
from synthcity.metrics import Metrics


def run_experiment(csv_path: str, chunk_size: int = 192, num_chunks: int = 11):
    df = pd.read_csv(csv_path)

    sizes = []
    quality_percent = []

    for i in range(1, num_chunks + 1):
        current_size = i * chunk_size
        df_part = df.iloc[:current_size].reset_index(drop=True)

        plugin = Plugins().get("bayesian_network")
        plugin.fit(GenericDataLoader(df_part))

        df_synth = plugin.generate(count=len(df_part)).dataframe()

        metrics = Metrics.evaluate(
            df_part,
            df_synth,
            metrics={"sanity": ["data_mismatch"]},
        )

        # В твоей версии synthcity metrics — DataFrame-агрегация, берем mean первой строки
        data_mismatch = float(metrics["mean"].iloc[0])

        print("----------------TEST----------------------")
        print(metrics)
        print("----------------TEST----------------------")


        score = data_mismatch * 100.0

        sizes.append(current_size)
        quality_percent.append(score)

        print(f"Rows: {current_size:4d} | data_mismatch: {data_mismatch:.6f} | quality: {score:.2f}%")

    return sizes, quality_percent


def plot_results(sizes, quality_percent):
    plt.figure()
    plt.plot(sizes, quality_percent, marker="o")
    plt.xlabel("Количество строк данных")
    plt.ylabel("Качество по Data Mismatch")
    plt.title("Сравнение алгоритмов по Data Mismatch")
    plt.ylim(0, 100)
    plt.xticks(sizes)
    plt.grid(True)
    plt.show()


if __name__ == "__main__":
    sizes, quality_percent = run_experiment(
        csv_path="UCIObesityDataSet.csv",
        chunk_size=192,
        num_chunks=11,
    )
    plot_results(sizes, quality_percent)
