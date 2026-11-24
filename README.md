# Multi-Sensor Indoor Localization and Navigation Framework

An end-to-end, sub-meter indoor localization and turn-by-turn navigation system for Android. This framework combines **Bluetooth Low Energy (BLE) RSSI distance modeling**, **Pedestrian Dead Reckoning (PDR) step detection**, **Monte Carlo Particle Filtering (MCL)** with deferred trilateration initialization, **Optical QR Code Absolute Positioning**, **Topological Pathfinding**, **Interactive 3D Spatial Route Graph Visualization**, and **Natural Language Route Instruction Generation**.

This document details the mathematical models, algorithmic architecture, signal processing pipeline, software engineering implementation, and dynamic configuration tools of the project to serve as a comprehensive reference for research white papers and technical documentation.

---

## Table of Contents

1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [System Architecture](#2-system-architecture)
3. [RF Signal Processing & Path Loss Calibration](#3-rf-signal-processing--path-loss-calibration)
   - [Log-Distance Path Loss Model](#log-distance-path-loss-model)
   - [Exponential Moving Average (EMA) Smoothing](#exponential-moving-average-ema-smoothing)
   - [Empirical Calibration Tool (`fit_path_loss.py`)](#empirical-calibration-tool-fit_path_loss-py)
4. [Localization Engine: Particle Filter & Sensor Fusion](#4-localization-engine-particle-filter--sensor-fusion)
   - [State Representation](#state-representation)
   - [Deferred Location Initialization](#deferred-location-initialization)
   - [Prediction Step (Pedestrian Dead Reckoning)](#prediction-step-pedestrian-dead-reckoning)
   - [Update Step (BLE Measurement Likelihood)](#update-step-ble-measurement-likelihood)
   - [Systematic Resampling & Jitter Injection](#systematic-resampling--jitter-injection)
   - [Spatial Anchor Interpolation](#spatial-anchor-interpolation)
5. [Optical Absolute Position Reset (QR Scanning)](#5-optical-absolute-position-reset-qr-scanning)
6. [Pathfinding & Topological Graph Search](#6-pathfinding--topological-graph-search)
   - [Topological Navigation Network](#topological-navigation-network)
   - [Dynamic Point Projection & Graph Splitting](#dynamic-point-projection--graph-splitting)
   - [Dijkstra / Uniform-Cost Route Optimization](#dijkstra--uniform-cost-route-optimization)
7. [Turn-by-Turn Instruction Engine](#7-turn-by-turn-instruction-engine)
   - [Turn Angle Calculation](#turn-angle-calculation)
   - [Collinear Step Merging](#collinear-step-merging)
   - [Dynamic Active Step Tracking](#dynamic-active-step-tracking)
8. [User Interface, 3D Visualizer & Dynamic Configuration](#8-user-interface-3d-visualizer--dynamic-configuration)
   - [Main Application Interface (`MainScreen.kt`)](#main-application-interface-mainscreenkt)
   - [Interactive 3D Spatial Visualizer (`RouteGraph3D.kt`)](#interactive-3d-spatial-visualizer-routegraph3dkt)
   - [Dynamic Runtime Configuration (`SettingsScreen.kt` & `ConfigurationParser.kt`)](#dynamic-runtime-configuration-settingsscreenkt--configurationparserkt)
   - [Debug Telemetry Panel (`DebugContainer.kt`)](#debug-telemetry-panel-debugcontainerkt)
9. [Simulation, Testing & Metrics Tools](#9-simulation-testing--metrics-tools)
10. [Repository Structure & Specifications](#10-repository-structure--specifications)

---

## 1. Executive Summary & Problem Statement

Indoor navigation remains a challenging problem due to the attenuation, multipath interference, and non-line-of-sight (NLOS) propagation that render Global Navigation Satellite Systems (GNSS / GPS) unusable inside modern buildings.

This project addresses indoor positioning by fusing three complementary modalities:
1. **Absolute Spatial Anchors**: Fixed Bluetooth Low Energy (BLE) beacons broadcasting RSSI signals.
2. **Relative Motion Sensing**: Inertial step detection via Android `Sensor.TYPE_STEP_DETECTOR` for Pedestrian Dead Reckoning (PDR).
3. **Ground-Truth Optical Benchmarks**: Quick Response (QR) code scans using Google ML Kit Barcode Scanning to eliminate cumulative drift.

A **Monte Carlo Particle Filter** fuses these asynchronous inputs into a continuous, probabilistic 3D position estimate $(x, y, z)$. The localized coordinate is projected onto a topological indoor graph, where Dijkstra search computes optimal paths, and a geometry engine transforms raw coordinates into step-by-step turn guidance.

---

## 2. System Architecture

The software architecture is modularized into four primary tiers: Data Acquisition, Signal & State Estimation, Routing Engine, and Presentation & Configuration Layer.

```
+-----------------------------------------------------------------------+
|                         SENSORS & INPUTS                              |
|  [ BLE Beacons Scanner ]    [ Step Detector Sensor ]   [ QR Scanner ] |
+----------------------------+--------------------------+---------------+
                             |                          |
                             v                          v
+----------------------------+--------------------------+---------------+
|                   SIGNAL PROCESSING & STATE FUSION                    |
|  - Log-Distance Path Loss Model + EMA Smoothing                       |
|  - Deferred Particle Seeding (Trilateration or QR Scan Initialization)|
|  - Monte Carlo Particle Filter (1,000 Particles)                      |
|    * Prediction: PDR Noise-Injected Motion Model (Omnidirectional)   |
|    * Update: Gaussian Likelihood over Top-2 Nearest Active Beacons    |
|    * Resampling: Systematic Resampling with Spatial Jitter            |
|  - Spatial Anchor Linear Interpolation                                |
+------------------------------------+----------------------------------+
                                     |
                                     v
+------------------------------------+----------------------------------+
|                     PATHFINDING & GEOMETRY ENGINE                     |
|  - Dynamic Point Projection onto Nearest Graph Edge Segment           |
|  - PriorityQueue Uniform-Cost Graph Search (Dijkstra)                |
|  - Turn Angle Vector Cross/Dot-Product Analysis                       |
|  - Instruction Generation & Continuous Walk Segment Merging           |
+------------------------------------+----------------------------------+
                                     |
                                     v
+------------------------------------+----------------------------------+
|               PRESENTATION & CONFIGURATION LAYER                      |
|  - Jetpack Compose & Material 3 UI                                    |
|  - Interactive 3D Spatial Route Graph Visualizer (Yaw/Pitch/Orbit)    |
|  - Dynamic Runtime Graph & BLE Configuration (SharedPreferences)      |
|  - Visual Debug Telemetry Panel & Scan Metrics                        |
+-----------------------------------------------------------------------+
```

---

## 3. RF Signal Processing & Path Loss Calibration

### Log-Distance Path Loss Model

Radio Frequency (RF) signals attenuate with distance according to the Log-Distance Path Loss model:

$$\text{RSSI}(d) = A - 10 \cdot n \cdot \log_{10}(d) + X_\sigma$$

Where:
- $\text{RSSI}(d)$: Received Signal Strength Indicator in dBm at distance $d$ (meters).
- $A$ (`txPower`): Reference RSSI at a distance of $1\,\text{meter}$ (typically $-68.0\,\text{dBm}$ to $-72.0\,\text{dBm}$).
- $n$ (`pathLossExponent`): Path Loss Exponent quantifying RF signal decay depending on building geometry (typically $2.0 \le n \le 4.0$).
- $X_\sigma$: Zero-mean Gaussian random variable modeling shadow fading ($\mathcal{N}(0, \sigma^2)$).

Inverting this equation yields the estimated distance $d$:

$$d = 10^{\frac{A - \text{RSSI}}{10 \cdot n}}$$

### Exponential Moving Average (EMA) Smoothing

To suppress high-frequency multipath noise in raw RSSI values, an Exponential Moving Average (EMA) filter is applied sequentially to each beacon stream:

$$\text{RSSI}_{\text{smoothed}}^{(t)} = \alpha \cdot \text{RSSI}_{\text{raw}}^{(t)} + (1 - \alpha) \cdot \text{RSSI}_{\text{smoothed}}^{(t-1)}$$

Where $\alpha = 0.2$ balances responsivity to user movement with signal smoothing.

### Empirical Calibration Tool (`fit_path_loss.py`)

The repository includes a Python regression utility (`fit_path_loss.py`) that analyzes empirical RSSI log files collected at known distances ($6\text{m}, 10\text{m}, 15\text{m}, 20\text{m}, 25\text{m}$).

1. **Linear Regression Fit**: Performs Ordinary Least Squares (OLS) regression of $\text{RSSI}$ against $\log_{10}(d)$:
   $$\text{slope} = -10 \cdot n, \quad \text{intercept} = A$$
2. **Variance Estimation**: Computes residual variance $\sigma_m^2$ in distance space ($\text{m}^2$) and RSSI space ($\text{dBm}^2$) to calibrate the variance parameter ($\sigma^2 = 5.0\,\text{m}^2$) used by the Particle Filter.
3. **Plot Generation**: Generates diagnostic regression curves and residual histograms (`path_loss_fit.png`).

---

## 4. Localization Engine: Particle Filter & Sensor Fusion

The core positioning engine is a non-linear, non-Gaussian Bayesian estimator implemented as a **Sequential Monte Carlo Particle Filter** (`ParticleFilter.kt`) using $N = 1,000$ particles.

### State Representation

Each particle $p_i$ represents a candidate 3D position hypothesis with an associated probability weight $w_i$:

$$p_i = \left( x_i, y_i, z_i, w_i \right), \quad i \in \{1, \dots, N\}, \quad \sum_{i=1}^N w_i = 1$$

### Deferred Location Initialization

To prevent false early convergence around an arbitrary origin, location estimation is **deferred** upon startup (`isInitialized = false`). Particle motion predictions and likelihood updates are bypassed until the filter is explicitly seeded through one of two mechanisms:

1. **BLE Trilateration Seeding**: When at least 2 active BLE anchor measurements are received within `SCAN_TIMEOUT_MS` ($10\,\text{seconds}$), an initial spatial estimate $(\hat{x}_0, \hat{y}_0, \hat{z}_0)$ is computed via distance-weighted trilateration. Particles are then seeded around this coordinate with Gaussian noise.
2. **Optical QR Code Scan**: Scanning a landmark QR code immediately calls `absUpdate(x, y, z)`, seeding all 1,000 particles in a concentrated Gaussian distribution ($\sigma = 1.0\,\text{m}$) around $(x_{\text{qr}}, y_{\text{qr}}, z_{\text{qr}})$.

While uninitialized, the UI displays an informative status banner: `"Finding location (move around or scan QR)"`.

### Prediction Step (Pedestrian Dead Reckoning)

When the device sensor triggers `TYPE_STEP_DETECTOR`, each particle's position is updated according to a noisy stride model:

$$x_i^{(t)} = x_i^{(t-1)} + \delta_{\text{step}} \cdot \cos(\theta_i)$$
$$y_i^{(t)} = y_i^{(t-1)} + \delta_{\text{step}} \cdot \sin(\theta_i)$$
$$z_i^{(t)} = z_i^{(t-1)} + \epsilon_z$$

Where:
- $\delta_{\text{step}} = d_{\text{nominal}} + \epsilon_s$, with $d_{\text{nominal}} = 0.7\,\text{m}$ and step noise $\epsilon_s \sim \mathcal{N}(0, 0.05\,\text{m}^2)$.
- $\theta_i \sim \mathcal{U}(0, 2\pi)$: Uniformly distributed random azimuth heading per particle.
  > **Key Design Choice**: Heading angle $\theta$ is sampled randomly across $360^\circ$ rather than relying on phone compass/gyroscope yaw. This eliminates magnetic distortion errors in steel-framed indoor environments, allowing position estimates to be updated robustly using BLE likelihood field constraints regardless of phone orientation.
- $\epsilon_z \sim \mathcal{N}(0, 0.05\,\text{m}^2)$: Vertical jitter.

### Update Step (BLE Measurement Likelihood)

When a BLE scan result arrives, the Euclidean distance $d_{i, k}$ between particle $p_i$ and active anchor beacon $k$ at $(X_k, Y_k, Z_k)$ is evaluated:

$$d_{i, k} = \sqrt{(x_i - X_k)^2 + (y_i - Y_k)^2 + (z_i - Z_k)^2}$$

Assuming Gaussian measurement error, the likelihood function for anchor distance observation $r_k$ with variance $\sigma_m^2 = 5.0\,\text{m}^2$ is:

$$p(r_k \mid p_i) = \frac{1}{\sqrt{2\pi \sigma_m^2}} \exp\left( -\frac{(d_{i, k} - r_k)^2}{2\sigma_m^2} \right)$$

To mitigate multipath corruption from distant beacons behind walls, **only the top-2 closest BLE measurements** are used in each update batch. The log-likelihood formulation ensures numerical stability:

$$\ln w_i^{(t)} = \ln w_i^{(t-1)} + \sum_{k \in \text{Top-2}} \ln p(r_k \mid p_i)$$

Weights are normalized such that $\sum w_i = 1$. Unrestricted particle movement without artificial bounding box clamping ensures smooth spatial propagation.

### Systematic Resampling & Jitter Injection

To combat particle degeneracy, systematic resampling is performed every update step. The effective sample size $N_{\text{eff}}$ is tracked:

$$N_{\text{eff}} = \frac{1}{\sum_{i=1}^N (w_i)^2}$$

During resampling, new particles are sampled proportionally to cumulative weights. To prevent particle collapse onto single points (sample impoverishment), Gaussian spatial jitter is added:

$$x_{\text{resampled}} = x_p + \mathcal{N}(0, 0.2\,\text{m})$$
$$y_{\text{resampled}} = y_p + \mathcal{N}(0, 0.2\,\text{m})$$
$$z_{\text{resampled}} = z_p + \mathcal{N}(0, 0.05\,\text{m})$$

### Spatial Anchor Interpolation

The expected user position $\hat{P} = (\hat{x}, \hat{y}, \hat{z})$ is calculated via the weighted mean:

$$\hat{P} = \sum_{i=1}^N w_i \cdot (x_i, y_i, z_i)$$

To ensure smooth spatial transitions along physical corridors, `interpolate()` further projects the estimate between the two nearest geometric anchors $A_1$ and $A_2$ based on distance ratios:

$$\text{ratio} = \frac{d(\hat{P}, A_1)}{d(\hat{P}, A_1) + d(\hat{P}, A_2)}$$
$$\hat{P}_{\text{interpolated}} = A_1 \cdot (1 - \text{ratio}) + A_2 \cdot \text{ratio}$$

---

## 5. Optical Absolute Position Reset (QR Scanning)

To handle initial cold starts and eliminate long-term dead-reckoning drift, the app integrates Google ML Kit Barcode Scanning (`GmsBarcodeScanning`).

When the user scans a QR code placed at a known location $(x_{\text{qr}}, y_{\text{qr}}, z_{\text{qr}})$:
1. `absUpdate(x, y, z)` is invoked.
2. All 1,000 particles are immediately re-initialized in a concentrated Gaussian cloud centered at $(x_{\text{qr}}, y_{\text{qr}}, z_{\text{qr}})$ with standard deviation $1.0\,\text{m}$.
3. The location estimate instantly updates with zero latency and sets `isInitialized = true`.

---

## 6. Pathfinding & Topological Graph Search

### Topological Navigation Network

Indoor space is represented as a directed graph $G = (V, E)$ where nodes $v_i \in V$ represent physical landmarks/corridors (`GeoLocation`), and edges $e = (u, v) \in E$ represent navigable pathways weighted by Euclidean distance $d(u, v)$.

### Dynamic Point Projection & Graph Splitting

When the user starts navigation from an arbitrary point $P_{\text{user}}$ off the fixed graph vertices:

1. **Shortest Distance to Edge**: Finds the nearest line segment $(U, V)$ in $G$ by projecting $P_{\text{user}}$ onto segment $UV$:
   $$t = \text{clamp}\left( \frac{(P_{\text{user}} - U) \cdot (V - U)}{\|V - U\|^2}, 0, 1 \right)$$
   $$P_{\text{projected}} = U + t \cdot (V - U)$$
2. **Graph Augmentation**: Temporarily splits edge $(U, V)$ by inserting $P_{\text{projected}}$ into a mutable copy of the adjacency graph, creating edges $(U, P_{\text{projected}})$ and $(P_{\text{projected}}, V)$.

### Dijkstra / Uniform-Cost Route Optimization

`PathFind.route(start, goal)` employs Dijkstra's shortest path search using a priority queue ordered by accumulated path distance:

$$\text{Cost}(P) = \sum_{i=1}^{k-1} d(v_i, v_{i+1})$$

The search guarantees finding the shortest Euclidean route along navigable corridors.

---

## 7. Turn-by-Turn Instruction Engine

Raw coordinate paths are transformed into natural language turn-by-turn guidance via `RouteInstructionGenerator.kt`.

### Turn Angle Calculation

For three consecutive path nodes $P_1, P_2, P_3$, vectors $\vec{v}_1 = P_2 - P_1$ and $\vec{v}_2 = P_3 - P_2$ are evaluated in the horizontal plane:

$$\text{cross} = v_{1,x} \cdot v_{2,y} - v_{1,y} \cdot v_{2,x}$$
$$\text{dot} = v_{1,x} \cdot v_{2,x} + v_{1,y} \cdot v_{2,y}$$
$$\theta_{\text{turn}} = \text{atan2}(\text{cross}, \text{dot}) \times \frac{180^\circ}{\pi}$$

Turn instructions are classified based on $\theta_{\text{turn}}$ and elevation change $\Delta z = z_3 - z_2$:

| Condition / Threshold | Identified Step Type | Instruction Template |
| :--- | :--- | :--- |
| $\Delta z > 0.5\,\text{m}$ | `STAIRS_UP` | "Take stairs up $D\,\text{m}$ towards Destination" |
| $\Delta z < -0.5\,\text{m}$ | `STAIRS_DOWN` | "Take stairs down $D\,\text{m}$ towards Destination" |
| $25^\circ \le \theta_{\text{turn}} \le 110^\circ$ | `TURN_RIGHT` | "Turn right and walk $D\,\text{m}$ towards Destination" |
| $-110^\circ \le \theta_{\text{turn}} \le -25^\circ$ | `TURN_LEFT` | "Turn left and walk $D\,\text{m}$ towards Destination" |
| $10^\circ \le \theta_{\text{turn}} < 25^\circ$ | `SLIGHT_RIGHT` | "Bear right and walk $D\,\text{m}$ towards Destination" |
| $-25^\circ < \theta_{\text{turn}} \le -10^\circ$ | `SLIGHT_LEFT` | "Bear left and walk $D\,\text{m}$ towards Destination" |
| Otherwise | `WALK` | "Walk $D\,\text{m}$ straight towards Destination" |

### Collinear Step Merging

`mergeContinuousWalkSteps()` combines consecutive `WALK` steps into unified instructions. Estimated steps are computed as $\text{steps} = \text{round}(D / 0.7\,\text{m})$.

### Dynamic Active Step Tracking

`calculateActiveStepIndex()` continuously tracks user movement during live navigation:
1. Projects user location $P_{\text{user}}$ onto each route segment.
2. Identifies the step segment with minimum orthogonal distance.
3. Automatically advances to the next step when the user approaches within $1.0\,\text{meter}$ of a segment end point.

---

## 8. User Interface, 3D Visualizer & Dynamic Configuration

Built with **Jetpack Compose** and **Material 3**, the application UI provides real-time navigation guidance, interactive spatial rendering, and dynamic configuration tools.

### Main Application Interface (`MainScreen.kt`)
- **Interactive SearchBar**: Auto-completes room names against configured navigation graph nodes.
- **Location Status Card**: Displays live $(x, y, z)$ coordinates when localized, or `"Finding location (move around or scan QR)"` during initial scanning.
- **Navigation Timeline (`RouteDisplay.kt`)**: Displays turn-by-turn guidance steps with dynamic step highlighting and checkmark completion.
- **Settings Gear Icon**: Provides direct access to runtime configuration management.

### Interactive 3D Spatial Visualizer (`RouteGraph3D.kt`)
Replaces legacy 2D drawing with an interactive **3D spatial canvas**:
- **3D Projection Pipeline**: Transforms 3D world coordinates $(x, y, z)$ onto a 2D screen surface using adjustable field-of-view, perspective scale factor, yaw, and pitch angles.
- **Gesture Control**: Supports drag gestures for custom Orbit mode (adjusting pitch $15^\circ..85^\circ$ and yaw $0^\circ..360^\circ$).
- **Smart View Controls**:
  - **Auto-Alignment**: Calculates the principal vector of the active navigation route and automatically aligns camera heading to match user travel direction.
  - **Auto-Rotation**: Smooth continuous rotation mode for spatial overview.
- **3D Rendered Elements**:
  - Translucent floor grid plane.
  - Depth-sorted nodes and connecting corridor edges (highlighting active navigation route in vibrant primary colors).
  - Animated pulsing user location indicator with vertical shadow projection to the floor plane.
  - Node label pill cards anchored to 3D projected coordinates.

### Dynamic Runtime Configuration (`SettingsScreen.kt` & `ConfigurationParser.kt`)
Eliminates hardcoded graph dependencies, allowing users to configure floor plans and beacon deployments directly at runtime without recompiling code:
- **Navigation Graph Text Format**:
  - Node definitions: `Name, X, Y, Z` (e.g. `Lab 1, 15.39, 27.6, 0.0`)
  - Edge definitions: `NodeA-NodeB` (e.g. `Lab 1-Corridor 2`)
- **BLE Beacon Anchors Text Format**:
  - Anchor definitions: `MAC Address, X, Y, Z[, Name]` (e.g. `2D:7E:1A:02:3D:21, 0.0, 0.0, 0.0, BLE1`)
- **Validation Engine (`ConfigurationParser`)**: Instant syntax checking highlighting line-by-line errors (duplicate node names, unknown edge endpoints, invalid numeric coordinates, duplicate MAC addresses).
- **Persistence**: Saved automatically to Android `SharedPreferences` (`indoor_nav_prefs`). Includes a `"Reset Defaults"` option to restore built-in fallback configurations (`DEFAULT_GRAPH_TEXT`, `DEFAULT_BLE_TEXT`).

### Debug Telemetry Panel (`DebugContainer.kt`)
Provides real-time diagnostic metrics for research and algorithm analysis:
- Embedded 3D spatial route graph visualizer.
- **Filter Telemetry**: Active particle count, effective sample size $N_{\text{eff}}$, spatial standard deviation $(\sigma_X, \sigma_Y, \sigma_Z)$, and maximum particle weight.
- **Sensor & BLE Telemetry**: Live step detector count, estimated step length, timestamp, BLE scan rate, and individual beacon RSSI predictor readings.

---

## 9. Simulation, Testing & Metrics Tools

### Python Step-by-Step Simulator (`particle_filter.py`)
A stand-alone Python application for off-device replay and visual debugging of recorded telemetry files (`BLE_Telemetry_*.json`):
- Visualizes particle cloud movement, anchor coordinates, and estimated trajectories using `matplotlib`.
- Supports step-by-step frame advancement via keyboard input.

### Empirical Calibration Script (`fit_path_loss.py`)
Runs Ordinary Least Squares (OLS) regression analysis on empirical BLE RSSI datasets:
```bash
python fit_path_loss.py --data-dir data --alpha 0.2 --plot-out path_loss_fit.png
```

### Automated Unit & Metric Test Suites
- `ParticleFilterMetric.kt`: Replays recorded JSON telemetry datasets (`BLE_Telemetry_*.json`) against ground-truth trajectories to calculate mean positioning error in meters.
- `ConfigurationParserTest.kt`: Unit test suite verifying graph node/edge parsing, anchor text parsing, duplicate MAC detection, and malformed edge error handling.
- `RouteInstructionGeneratorTest.kt`: Unit tests verifying turn angle calculation consistency (right turn, left turn, straight, stairs elevation changes) and continuous step merging.
- `PathFindTest.kt`: Unit tests verifying Dijkstra shortest path route optimization across topological networks.

---

## 10. Repository Structure & Specifications

```
IndoorNavigation/
├── app/src/main/
│   ├── java/me/chetan/indoornavigation/
│   │   ├── MainActivity.kt               # Entry Activity & BLE/Sensor Permissions
│   │   ├── MathUtils.kt                  # Vector Interpolation & Distance Math
│   │   ├── ParticleFilter.kt             # Sequential Monte Carlo Particle Filter (Deferred Seeding)
│   │   ├── PathFind.kt                   # Topological Graph & Dijkstra Pathfinding
│   │   ├── RSSIDistancePredictor.kt      # EMA Filter & Path Loss Distance Predictor
│   │   ├── RouteInstructionGenerator.kt  # Turn Angle Vector Engine & Step Merger
│   │   ├── data/
│   │   │   ├── ConfigurationParser.kt    # Text Parser for Graph Nodes/Edges & BLE Anchors
│   │   │   ├── DeviceScanInfo.kt         # Scanned BLE Beacon State Container
│   │   │   ├── FilterEstimate.kt         # Estimated Coordinate Data Class
│   │   │   ├── GeoLocation.kt            # 3D Coordinate & Hashable Node Data Class
│   │   │   ├── Measurement.kt            # Particle Filter Measurement Likelihood Data Class
│   │   │   ├── NavigationData.kt         # Fallback Maps & Default Configuration Presets
│   │   │   ├── Particle.kt               # State Hypothesis Data Class
│   │   │   └── RouteStep.kt              # Instruction Step Enums & Data Class
│   │   └── ui/
│   │       ├── MainScreen.kt             # Compose Main Layout, Search Bar & Status Card
│   │       ├── MainViewModel.kt          # ViewModel, BLE/Sensor Scanning & Prefs Management
│   │       ├── SettingsScreen.kt         # Runtime Graph & BLE Anchor Configuration UI
│   │       └── components/
│   │           ├── BLEContainer.kt       # Scanned BLE Devices UI List
│   │           ├── DebugContainer.kt     # Real-Time Telemetry Stats & Diagnostics
│   │           ├── RouteDisplay.kt       # Turn-by-Turn Timeline Guidance UI
│   │           └── RouteGraph3D.kt       # Interactive 3D Spatial Canvas Visualizer
│   └── assets/                           # Asset Telemetry Traces
├── app/src/test/java/me/chetan/indoornavigation/
│   ├── ConfigurationParserTest.kt        # Unit Tests for Configuration Parser
│   ├── ExampleUnitTest.kt                # Sample Unit Test
│   ├── ParticleFilterMetric.kt           # Ground-Truth Replay Telemetry Error Evaluation
│   ├── PathFindTest.kt                   # Unit Tests for Dijkstra Route Search
│   └── RouteInstructionGeneratorTest.kt  # Unit Tests for Turn Angle & Instruction Engine
├── data/                                 # Empirical Distance Test Datasets (.txt)
├── fit_path_loss.py                      # Path Loss Regression & Calibration Script
├── particle_filter.py                    # Python Telemetry Replay & Simulation Tool
└── README.md                             # White Paper & Technical Specification Document
```

### Technical Requirements
- **Platform**: Android SDK 26+ (Android 8.0 Oreo or higher).
- **Languages**: Kotlin 2.x, Python 3.10+.
- **Dependencies**: Jetpack Compose (Material 3), Kotlin Coroutines, Google ML Kit Barcode Scanning (`com.google.android.gms:play-services-mlkit-barcode-scanning`), Android Hardware Sensors (Step Detector, Bluetooth LE Scanner).
