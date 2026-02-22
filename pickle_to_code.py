import joblib
import m2cgen as m2c

model=joblib.load('RF.pkl')

java_code=m2c.export_to_java(model)
with open("./app/src/main/java/me/chetan/indoornavigation/Model.java","w") as f:
    f.write(java_code)
