title: 使用 Earth2Studio 和 AI 模型进行全球天气预测：太阳辐照
date: 2025-07-11 09:17:08
category: [ai]
tags: [ai, 天气预测, earth2studio]

---

## 使用 Earth2Studio 和 AI 模型进行全球太阳辐照预测

太阳能作为一种关键的可再生能源，其稳定性和效率在很大程度上取决于对太阳辐照度的精确预测。本文将指导您如何利用 NVIDIA 的 `Earth2Studio` 框架，结合强大的 `FourCastNet (SFNO)` 天气预报模型和 `SolarRadiationAFNO` 诊断模型，轻松实现全球范围内的太阳辐照度（GHI）预测。

我们将通过一个完整的 Python 示例，涵盖从数据加载、模型执行、结果可视化到精度验证的全过程。

### 核心概念：预报 (Prognostic) + 诊断 (Diagnostic) 的强大组合

Earth2Studio 的核心思想之一是将复杂的地球系统预测任务分解为两个步骤：

1. **预报模型 (Prognostic Model)：** 这是预测的主力。它负责预测未来的基本大气状态，如风场、温度、湿度等。在我们的示例中，我们使用 `SFNO` (球面傅里叶神经算子，Spherical Fourier Neural Operator)，即 FourCastNet V2，它是一个先进的全球天气预报 AI 模型。
2. **诊断模型 (Diagnostic Model)：** 该模型接收预报模型的输出，并从中“诊断”或计算出我们真正关心的特定物理量。这里，我们使用 `SolarRadiationAFNO` 模型，它专门用于根据大气状态计算地表接收到的太阳短波辐射（`ssrd`），这在物理上等同于全球水平辐照度（GHI）。

这种 **预报+诊断** 的架构极具灵活性，允许我们为同一基础天气预报附加不同的诊断模型，以获取如太阳能、风能、极端天气指数等多种衍生产品。

### 实战演练：一步步生成您的第一个太阳辐照度预测

让我们深入代码，了解预测流程的每个环节。

#### 第 1 步：准备环境与数据源

首先，您需要安装 `earth2studio`。整个流程依赖以下几个关键组件：

- **数据源 (Data Source)：** 为 AI 模型提供初始气象条件。
  - **GFS (Global Forecast System)：** 美国国家气象局的全球预报系统，提供近乎实时的分析数据（通常可回溯 30 天），免费且无需注册。是快速上手的理想选择。
  - **IFS (Integrated Forecasting System)：** 欧洲中期天气预报中心（ECMWF）的高分辨率数据，精度更高，但需要注册哥白尼气候数据存储（CDS）并配置 API 密钥。

在代码中，我们可以轻松加载 GFS 数据：

```python
from earth2studio.data import GFS

# 加载 GFS 数据，它将按需从云端下载
data = GFS()
```

> **注意**：GFS 数据有可用性限制。为确保成功，建议将预测起始时间 (`start_time`) 设置为距今 3-5 天前的 UTC 00:00, 06:00, 12:00 或 18:00。

#### 第 2 步：加载 AI 模型

接下来，我们加载预报模型和诊断模型。Earth2Studio 会自动处理模型的下载和缓存。

```python
from earth2studio.models.px import SFNO
from earth2studio.models.dx import SolarRadiationAFNO1H, SolarRadiationAFNO6H

# 1. 加载 FourCastNet 预报模型
px_model = SFNO.load_model(SFNO.load_default_package())

# 2. 加载太阳辐照诊断模型 (以 1 小时为例)
# 可根据需求选择 '1h' 或 '6h' 分辨率
dx_model = SolarRadiationAFNO1H.load_model(SolarRadiationAFNO1H.load_default_package())
```

**1 小时 vs 6 小时模型**：

| 维度           | 1 小时模型 (`SolarRadiationAFNO1H`) | 6 小时模型 (`SolarRadiationAFNO6H`) |
| :------------- | :---------------------------------- | :---------------------------------- |
| **时间分辨率** | 1 小时，细节更丰富                  | 6 小时，趋势更平滑                  |
| **典型场景**   | 分时电价调度、短时功率波动预测      | 中长期发电量预估、电站容量规划      |
| **计算成本**   | 相对更高                            | 相对更低，速度更快                  |

选择哪个模型取决于您的具体业务需求。若关注日内功率曲线，选 **1H**；若仅需长期趋势；**6H** 更具效率，推理速度更快。

#### 第 3 步：执行预测并保存结果

所有组件准备就绪后，调用 `earth2studio.run.diagnostic` 即可启动预测。我们将结果保存为 [Zarr](https://zarr.readthedocs.io/en/stable/) 格式，它支持高效的并行 I/O 和懒加载，特别适合处理大规模气象数据。

```python
import torch
from datetime import datetime
from earth2studio.run import diagnostic
from earth2studio.io import ZarrBackend

# 设置预测参数
start_time = datetime(2025, 2, 1)  # 预测起始时间
ndays = 7  # 预测 7 天
nsteps = ndays * 24  # 1 小时模型共需 168 步
file_path = f'runs/ssrd_{start_time.strftime("%Y%m%d")}_1h_forecast.zarr'

# 设置输出后端，'overwrite'：True 表示如果文件已存在则覆盖
io_backend = ZarrBackend(file_name=file_path, backend_kwargs={'overwrite': True})

# 在 GPU 上执行预测
diagnostic(
    time=[start_time.strftime('%Y-%m-%dT%H:%M:%SZ')],
    nsteps=nsteps,
    prognostic=px_model,
    diagnostic=dx_model,
    data=data,
    io=io_backend,
    device=torch.device('cuda'),
)
```

执行完毕后，一个包含 7 天全球太阳辐照度预测的 `zarr` 文件就生成了。

### 结果解析与可视化

有了预测数据，下一步就是理解和展示它。我们将使用 `xarray` 和 `matplotlib` 进行数据处理和可视化。

#### 1. 读取单点时间序列

我们可以轻松提取任意经纬度上的预测结果，并绘制其时间序列图。

```python
import xarray as xr
import pandas as pd
import matplotlib.pyplot as plt

# 读取 Zarr 数据
ds = xr.open_zarr(file_path)

# 选择特定经纬度 (以哈萨克斯坦某地为例)
lon, lat = 77.07, 43.885
ssrd_point = ds['ssrd'].sel(lat=lat, lon=lon, method='nearest')

# 转换为 Pandas DataFrame 以便绘图
df = ssrd_point.to_dataframe().reset_index()
df['time'] = pd.to_datetime(df['time'])
df['lead_time'] = pd.to_timedelta(df['lead_time'].values.astype('int64'), unit='ns')
df['forecast_time'] = df['time'] + df['lead_time']

# 绘制时间序列图
plt.figure(figsize=(14, 6))
plt.plot(df['forecast_time'], df['ssrd'], 'b-', label='预测辐照 (SSRD)')
plt.xlabel('预测时间')
plt.ylabel('SSRD / W·m⁻²')
plt.title(f'太阳辐照预测时间序列\n位置: {lat:.2f}°N, {lon:.2f}°E')
plt.legend()
plt.grid(True)
plt.show()
```

这将生成一张清晰的日内辐照度变化曲线图。

#### 2. 绘制全球分布图

我们还可以一窥预测起始时刻的全球太阳辐照度分布。

```python
# 选择第一个时间步的数据
ssrd_step0 = ds['ssrd'].isel(lead_time=0)

# 创建全球分布图
plt.figure(figsize=(15, 8))
ssrd_step0.plot(cmap='turbo', vmin=0, vmax=1400, cbar_kwargs={'label': 'SSRD / W·m⁻²'})
plt.scatter(lon, lat, c='red', s=100, marker='*', label=f'选定点 ({lat:.2f}°N, {lon:.2f}°E)')
plt.title('太阳辐照全球分布 (预测起始时刻)')
plt.legend()
plt.grid(True)
plt.show()
```

这幅图直观地展示了晨昏线和全球太阳能资源的分布情况。

### 模型精度验证：与 NASA POWER 数据对比

任何模型的预测结果都应经过验证。示例脚本提供了一个非常实用的函数 `accuracy_comparision`，它能自动从 NASA POWER 数据库获取权威的卫星观测数据，并与我们的 AFNO 模型预测进行比对，计算均方根误差（RMSE）、平均绝对误差（MAE）等关键指标。

```python
# 示例：执行精度对比
start_ts = pd.Timestamp(start_time)
end_ts = pd.Timestamp(start_time + timedelta(days=ndays))

result = accuracy_comparision(file_path, lon, lat, start_ts, end_ts)
print(result)

# 输出示例:
# === 准确率指标 ===
# RMSE: 155550.266
# MAE: 101790.925
# MBE: 11401.244
# R2: 0.960
```

指标解释（单位均为 `J/m⁻²`，`1 W/m⁻²·h ≈ 3600 J/m⁻²`）

1. `RMSE = 1.56 × 10⁵ J/m⁻²`
     → 折算为功率约 43 `W/m⁻²`（= 155 550 ÷ 3 600）。
      表示预测与观测的标准误差 ~ 43 `W/m⁻²`。
2. `MAE = 1.02 × 10⁵ J/m⁻²`
     → `≈ 28 W/m⁻²` 的平均绝对误差。
      相较典型晴天峰值 1 000 `W/m⁻²`，误差 `≈ 2.8%`。
3. `MBE = +1.14 × 10⁴ J/m⁻²`
     → 约 `3 W/m⁻²` 的平均偏差，说明整体 **轻微高估**，幅度仅 `0.3%`。
4. `R² = 0.96`
     `98 ` 的观测方差可由模型解释，拟合度非常高；散点几乎围绕 `1∶1` 直线分布。

综合评价

- 误差水平（`28–43 W/m⁻²`）已属于高质量小时级辐照预报；
- 正偏差极小，可忽略或通过简易线性校正消除；
- 高 `R²` 表明时序变化捕捉准确，仅剩幅值微调空间。

实际上，这样的模型已满足日内功率调度与能量预测需求；后续可针对特定天气情形（多云、积雪等）做分段校正，进一步提升极端条件下的可靠度。
这为我们评估和信任模型预测提供了量化依据。

## 结论

通过本文，读者可以了解如何使用 `Earth2Studio` 框架，通过组合 `FourCastNet (SFNO)` 和 `SolarRadiationAFNO` 两个强大的 AI 模型，实现了一个端到端的太阳辐照度预测工作流。这个流程不仅概念清晰、代码简洁，而且功能强大，涵盖了从数据准备、模型推理到结果分析和验证的全部环节。

现在，您可以开始尝试调整参数，比如更换地点、延长预测天数，或在 `1` 小时和 `6` 小时模型之间切换，进一步探索 AI 在地球科学预测领域的巨大潜力。
