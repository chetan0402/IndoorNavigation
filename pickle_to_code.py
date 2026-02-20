import joblib
import m2cgen as m2c

model=joblib.load('rf_distance_model.pkl')

java_code=m2c.export_to_java(model)
with open("RandomForstModel.java","w") as f:
    f.write(java_code)
