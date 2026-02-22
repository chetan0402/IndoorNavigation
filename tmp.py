import pandas as pd
import numpy as np
import pickle
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.metrics import mean_squared_error, mean_absolute_error
from collections import deque

# ====================== TRAINING PHASE ======================
print("=== TRAINING PHASE ===")
df = pd.read_csv('signal_strength_distance.csv')

X = df[['Signal Strength']].values
y = df['Distance'].values

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

param_grid = {
    'n_estimators': [100, 200],
    'max_depth': [5, 8, 12],
    'min_samples_split': [5, 10, 20],
    'min_samples_leaf': [5, 10, 15]
}

print("Starting Grid Search...")
grid_search = GridSearchCV(
    RandomForestRegressor(random_state=42, n_jobs=-1),
    param_grid,
    cv=5,
    scoring='neg_root_mean_squared_error',
    verbose=1
)

grid_search.fit(X_train, y_train)
print(f"Best parameters: {grid_search.best_params_}")

best_rf = grid_search.best_estimator_

train_preds = best_rf.predict(X_train)
train_rmse = np.sqrt(mean_squared_error(y_train, train_preds))
print(f"Train RMSE: {train_rmse:.2f} m")

preds = best_rf.predict(X_test)
rmse = np.sqrt(mean_squared_error(y_test, preds))
mae = mean_absolute_error(y_test, preds)
print(f"Test  RMSE: {rmse:.2f} m")
print(f"Test  MAE : {mae:.2f} m")

best_rf.fit(X, y)

with open("rf_distance_model.pkl", 'wb') as file:
    pickle.dump(best_rf, file)
print("--- Model saved successfully ---\n")


# ====================== FINALIZED PREDICTOR CLASS (with 2D Kalman) ======================
class RSSIDistancePredictor:
    def __init__(self, model_path="rf_distance_model.pkl", window_size=7):
        with open(model_path, 'rb') as f:
            self.model = pickle.load(f)
        
        self.window = deque(maxlen=window_size)  # For confidence std
        self.smoothed_distance = 15.0
        self.alpha_distance = 0.45  # Keep for distance smoothing
        self.boost_threshold = -62
        
        # 2D Kalman: state [rssi, rssi_velocity]; tuned for dynamics
        self.dt = 1.0  # Time step (adjust to your sample rate, e.g., 0.5 for 2Hz)
        self.F = np.array([[1, self.dt], [0, 1]])  # Transition: constant velocity
        self.H = np.array([[1, 0]])  # Measure RSSI only
        self.Q = np.diag([0.01, 0.5])  # Process noise: low on RSSI, higher on vel for adaptation
        self.R = 1.6927  # Meas noise var from paper (tune to your std^2 ≈1.69)
        self.x = np.array([[-80.0], [0.0]])  # Init: RSSI=-80, vel=0
        self.P = np.diag([100.0, 1.0])  # High init uncertainty
    
    def predict(self, new_rssi: float):
        # Kalman Predict
        x_pred = self.F @ self.x
        P_pred = self.F @ self.P @ self.F.T + self.Q
        
        # Kalman Update
        z = np.array([[new_rssi]])
        S = self.H @ P_pred @ self.H.T + self.R
        K = (P_pred @ self.H.T) @ np.linalg.inv(S)  # Gain (2x1)
        self.x = x_pred + K @ (z - self.H @ x_pred)
        self.P = (np.eye(2) - K @ self.H) @ P_pred
        
        smoothed_rssi = self.x[0, 0]
        
        # Append to window for confidence
        self.window.append(new_rssi)
        
        # Predict distance
        raw_pred = self.model.predict([[smoothed_rssi]])[0]
        raw_pred = max(4.0, min(28.0, raw_pred))
        
        if smoothed_rssi < self.boost_threshold:
            boost = 2.2 + (-62 - smoothed_rssi) * 0.18
            raw_pred = max(5.0, raw_pred - boost)
        
        self.smoothed_distance = (self.alpha_distance * raw_pred +
                                  (1 - self.alpha_distance) * self.smoothed_distance)
        
        # Confidence: combine std and Kalman uncert
        rssi_std = np.std(self.window) if len(self.window) > 1 else 0
        kalman_uncert = np.sqrt(self.P[0, 0])
        confidence = max(0.15, 1.0 - (rssi_std / 15.0) - (kalman_uncert / 20.0))
        
        return round(self.smoothed_distance, 2), round(confidence, 2)


# ====================== TEST ======================
print("=== FINALIZED SUDDEN MOVEMENT TEST (with 2D Kalman) ===")
predictor = RSSIDistancePredictor()

initial = [-79.0, -79.0, -78.0, -78.0, -77.0]
print("Initial (far):")
for r in initial:
    d, c = predictor.predict(r)
    print(f"RSSI {r:6.1f} → {d:5.2f}m (conf {c:.2f})")

sudden = [-61.0, -58.0, -54.0, -53.0, -51.0]
print("\nSudden close movement:")
for r in sudden:
    d, c = predictor.predict(r)
    print(f"RSSI {r:6.1f} → {d:5.2f}m (conf {c:.2f})")