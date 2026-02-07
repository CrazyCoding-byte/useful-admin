from fastapi import FastAPI
import uvicorn

app = FastAPI()


@app.get("/api/ml/ctr/predict")
def predict_ctr(ad_id: int):
    # 先模拟返回CTR分数，后续替换为真实模型
    return {"ad_id": ad_id, "ctr_score": 0.85, "code": 200}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8083)
