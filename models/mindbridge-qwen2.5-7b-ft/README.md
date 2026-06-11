# mindbridge-qwen2.5-7b-ft:latest

這是 MindBridge 項目使用的本地 Ollama 模型目錄。`Modelfile` 會加載本目錄下的 GGUF 權重文件：

```text
mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

創建本地模型：

```bash
/Applications/Ollama.app/Contents/Resources/ollama create mindbridge-qwen2.5-7b-ft:latest -f models/mindbridge-qwen2.5-7b-ft/Modelfile
```
