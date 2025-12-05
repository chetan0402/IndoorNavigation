import re
import glob
import numpy as np
from scipy.optimize import curve_fit

# -----------------------------------------------------------
# Model: distance = 10^((C - rssi) / (2n))
# -----------------------------------------------------------
def model(rssi, C, n):
    return 10 ** ((C - rssi) / (10 * n))

distances = []
rssis = []

# -----------------------------------------------------------
# Read all data*.txt files
# extract distance from filename e.g. data1.5m → 1.5
# -----------------------------------------------------------
for file in glob.glob("data*.txt"):
    match = re.search(r"data([0-9.]+)m", file)
    if not match:
        continue

    distance = float(match.group(1))

    with open(file, "r") as f:
        for line in f:
            m = re.search(r"RSSI:\s*([-0-9.]+)", line)
            if m:
                rssi = float(m.group(1))
                distances.append(distance)
                rssis.append(rssi)

distances = np.array(distances)
rssis = np.array(rssis)

# -----------------------------------------------------------
# Fit the model using nonlinear regression
# -----------------------------------------------------------
p0 = [-50, 2]  # initial guess: C=-50, n=2
params, covariance = curve_fit(model, rssis, distances, p0=p0)

C_fit, n_fit = params

# -----------------------------------------------------------
# Print results
# -----------------------------------------------------------
print("Fitted Parameters:")
print(f"Constant C = {C_fit:.4f}")
print(f"n = {n_fit:.4f}")

# Example: Predict distance for an RSSI
# print(model(-60, C_fit, n_fit))

