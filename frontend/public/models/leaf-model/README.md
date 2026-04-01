Leaf detection model (TensorFlow.js GraphModel)

要求：
1. 放置 `model.json` 及其权重文件（例如 shard 的 `.bin`）。
2. `frontend/src/App.vue` 默认会加载路径 `/models/leaf-model/model.json`
3. 确保模型离线可用（前端静态资源不依赖后端）

注意：
- 本项目前端的“叶片检测”与“颜色异常分析”逻辑已经接好。
- 你需要把实际的轻量叶片检测模型文件放到此目录后，检测流程才会真正生效。

