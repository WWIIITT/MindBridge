# MindBridge 大模型 LoRA 微調全流程

本文檔記錄 MindBridge 項目中 Qwen2.5-7B 的 LoRA 微調、adapter 導出、模型合併、GGUF 轉換、量化以及本地 Ollama 接入流程。按照本文操作，可以從項目數據集訓練出 LoRA adapter，並最終得到本項目可直接使用的本地模型文件。

## 1. 最終目標

本流程最終會得到以下文件：

```text
mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

放到本地項目目錄：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

然後用 Ollama 註冊爲：

```text
mindbridge-qwen2.5-7b-ft:latest
```

項目啓動後，後端默認調用該模型。

## 2. 數據集說明

本項目用於 LoRA 微調的數據集爲：

```text
data/lora/psychqa_synthetic.jsonl
```

數據格式爲 JSONL，每行一條樣本：

```json
{"instruction":"分析用戶文本情緒，只能輸出：正常、焦慮、低落、高風險","input":"最近考試快到了，我總擔心自己複習不完，晚上也睡不踏實。","output":"焦慮"}
```

在本地可以先確認：

```bash
cd MindBridge
wc -l data/lora/psychqa_synthetic.jsonl
head -n 1 data/lora/psychqa_synthetic.jsonl
```

訓練時按 9:1 劃分訓練集和驗證集：

```text
訓練集：約 2160 條
驗證集：約 240 條
```

後續訓練命令使用 `--split_dataset_ratio 0.1` 自動劃分，不需要手動拆成兩個文件。

## 3. 雲 GPU 環境準備

推薦雲 GPU：

```text
GPU：A10G 24GB / RTX 4090 24GB / A5000 24GB / A100
系統盤：建議 80GB 以上，最低不要低於 60GB
```

實測 A10G 24GB 可以完成 LoRA 微調。

進入雲服務器後，先檢查 GPU：

```bash
nvidia-smi
```

能看到類似：

```text
NVIDIA A10G
Memory-Usage: 3MiB / 24564MiB
```

說明 GPU 可用。

## 4. 上傳數據集

如果 SSH 可以使用，可以從本地上傳整個項目：

```bash
scp -r MindBridge root@服務器IP:/root/MindBridge
```

如果 SSH 不方便，也可以使用 JupyterLab：

1. 打開雲平臺裏的 JupyterLab
2. 左側文件區上傳 `psychqa_synthetic.jsonl`
3. 打開 JupyterLab 的 Terminal
4. 將文件整理到固定目錄

常用整理命令：

```bash
mkdir -p /root/MindBridge/data/lora
cp /MindBridge/psychqa_synthetic.jsonl /root/MindBridge/data/lora/
```

如果不知道文件被上傳到哪裏，使用：

```bash
find /root /MindBridge -name "psychqa_synthetic.jsonl" 2>/dev/null
```

確認數據集：

```bash
cd /root/MindBridge
wc -l data/lora/psychqa_synthetic.jsonl
head -n 1 data/lora/psychqa_synthetic.jsonl
```

## 5. 安裝微調環境

在雲服務器 Terminal 中執行：

```bash
python3 -m venv /root/mindbridge-lora-env
source /root/mindbridge-lora-env/bin/activate

pip install -U pip
pip install -U ms-swift transformers accelerate peft datasets safetensors
```

安裝完成後，命令行前面會出現：

```text
(mindbridge-lora-env)
```

如果模型下載較慢，可以設置鏡像：

```bash
export HF_ENDPOINT=https://hf-mirror.com
```

實際訓練時，`ms-swift` 也可能從 ModelScope 下載模型。

## 6. 開始 LoRA 微調

進入項目目錄：

```bash
cd /root/MindBridge
source /root/mindbridge-lora-env/bin/activate
```

執行訓練命令：

```bash
CUDA_VISIBLE_DEVICES=0 swift sft \
  --model Qwen/Qwen2.5-7B-Instruct \
  --dataset /root/MindBridge/data/lora/psychqa_synthetic.jsonl \
  --tuner_type lora \
  --lora_rank 8 \
  --lora_alpha 32 \
  --target_modules all-linear \
  --torch_dtype bfloat16 \
  --num_train_epochs 3 \
  --per_device_train_batch_size 1 \
  --gradient_accumulation_steps 16 \
  --learning_rate 1e-4 \
  --max_length 512 \
  --split_dataset_ratio 0.1 \
  --data_seed 42 \
  --eval_strategy steps \
  --eval_steps 100 \
  --save_steps 100 \
  --save_total_limit 2 \
  --logging_steps 5 \
  --output_dir /root/MindBridge/output/qwen25-7b-mindbridge-lora \
  --system "你是 MindBridge 校園心理關懷智能體，需要識別學生情緒狀態，保持溫和、穩定、非評判表達，遇到高風險內容優先保護學生安全。"
```

如果 `bfloat16` 不支持，改成：

```bash
--torch_dtype float16
```

如果顯存不夠，可以降低上下文長度：

```bash
--max_length 256
```

## 7. 訓練日誌怎麼看

訓練前會下載 Qwen2.5-7B-Instruct 原始權重。它通常會分成 4 個 `.safetensors` 文件：

```text
model-00001-of-00004.safetensors
model-00002-of-00004.safetensors
model-00003-of-00004.safetensors
model-00004-of-00004.safetensors
```

總大小十幾 GB 是正常的，因爲這是 FP16/BF16 原始權重。Qwen2.5-7B 約 7.6B 參數，每個參數按 2 bytes 計算，約 15GB。

訓練開始後，會看到類似：

```text
PeftModelForCausalLM: 7635.8016M Params (20.1851M Trainable)
```

含義：

```text
總參數量：約 76 億
LoRA 可訓練參數：約 2018 萬
可訓練比例：約 0.26%
```

訓練進度類似：

```text
global_step/max_steps: 102/405
Train: 25%
```

405 步的來源：

```text
數據量 2400 條
按 9:1 劃分後，訓練集約 2160 條，驗證集約 240 條
batch_size = 1
gradient_accumulation_steps = 16
每輪約 2160 / 16 = 135 步
num_train_epochs = 3
總步數約 135 * 3 = 405 步
```

訓練完成時會看到：

```text
Train: 100% 405/405
last_model_checkpoint: /root/MindBridge/output/qwen25-7b-mindbridge-lora/.../checkpoint-405
```

## 8. 找到 LoRA Adapter

訓練完成後執行：

```bash
find /root/MindBridge/output/qwen25-7b-mindbridge-lora -name "adapter_model.safetensors"
```

一般會看到：

```text
/root/MindBridge/output/qwen25-7b-mindbridge-lora/v1-20260530-163249/checkpoint-405/adapter_model.safetensors
```

最終使用整個 `checkpoint-405` 文件夾，而不是隻使用單個 `.safetensors` 文件。該目錄通常包含：

```text
adapter_config.json
adapter_model.safetensors
additional_config.json
args.json
README.md
optimizer.pt
rng_state.pth
scheduler.pt
trainer_state.json
training_args.bin
```

真正用於推理/合併的核心文件是：

```text
adapter_config.json
adapter_model.safetensors
```

## 9. 合併 LoRA 到 Qwen2.5-7B

在雲 GPU 上合併，因爲基礎模型權重已經在雲端緩存裏。

執行：

```bash
cd /root/MindBridge
source /root/mindbridge-lora-env/bin/activate

swift export \
  --adapters /root/MindBridge/output/qwen25-7b-mindbridge-lora/v1-20260530-163249/checkpoint-405 \
  --merge_lora true \
  --output_dir /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-hf
```

注意：把路徑裏的 `v1-20260530-163249/checkpoint-405` 換成你實際生成的最終 checkpoint 路徑。

成功時會看到：

```text
Successfully merged LoRA and saved in `/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-hf`.
```

合併後的目錄是 Hugging Face 格式完整模型，通常包含：

```text
config.json
generation_config.json
model-00001-of-00004.safetensors
model-00002-of-00004.safetensors
model-00003-of-00004.safetensors
model-00004-of-00004.safetensors
model.safetensors.index.json
tokenizer.json
tokenizer_config.json
vocab.json
merges.txt
```

## 10. 轉換爲 GGUF

Ollama 更適合使用 GGUF 文件，因此需要用 `llama.cpp` 轉換。

### 10.1 下載 llama.cpp

如果 GitHub 網絡正常：

```bash
cd /root
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
pip install -r requirements.txt
```

如果 `git clone` 很慢，可以下載 zip：

```bash
cd /root
wget -O llama.cpp.zip https://gh-proxy.com/https://github.com/ggml-org/llama.cpp/archive/refs/heads/master.zip
```

如果服務器沒有 `unzip`，用 Python 解壓：

```bash
python3 - <<'PY'
import zipfile
from pathlib import Path

zip_path = Path("llama.cpp.zip")
out_dir = Path("/root")

with zipfile.ZipFile(zip_path, "r") as z:
    z.extractall(out_dir)

print("done")
PY
```

整理目錄並安裝依賴：

```bash
mv /root/llama.cpp-master /root/llama.cpp
cd /root/llama.cpp
pip install -r requirements.txt
```

如果 pip 下載慢，可以換源：

```bash
pip install -r requirements.txt \
  -i https://mirrors.aliyun.com/pypi/simple \
  --trusted-host mirrors.aliyun.com
```

### 10.2 轉成 F16 GGUF

```bash
cd /root/llama.cpp

python3 convert_hf_to_gguf.py \
  /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-hf \
  --outfile /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf \
  --outtype f16
```

轉換時會看到：

```text
Writing the following files:
/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf
n_tensors = 339, total_size = 15.2G
```

進度條有時會長時間不刷新，可以另開一個 Terminal 看文件大小是否增長：

```bash
watch -n 10 'date; ls -lh /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf; ps aux | grep convert_hf_to_gguf | grep -v grep'
```

完成後會看到：

```text
Model successfully exported to /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf
```

## 11. 量化爲 Q4_K_M

F16 GGUF 大約 15GB，不適合普通本地機器使用。需要量化成 4-bit。

如果缺少 `cmake`：

```bash
apt update
apt install -y cmake build-essential
```

如果不能用 apt，也可以：

```bash
pip install cmake
```

編譯量化工具：

```bash
cd /root/llama.cpp
cmake -B build
cmake --build build --config Release -j
```

執行量化：

```bash
./build/bin/llama-quantize \
  /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf \
  /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf \
  Q4_K_M
```

成功時會看到類似：

```text
model size = 14526.27 MiB
quant size = 4460.45 MiB
```

確認文件：

```bash
ls -lh /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

正常大小約：

```text
4.4G - 4.7G
```

量化成功後，可以刪除 F16 文件節省雲服務器空間：

```bash
rm -f /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf
```

## 12. 下載最終模型到本地

最終需要下載：

```text
/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

如果使用 JupyterLab，可以在左側文件瀏覽器找到該文件，右鍵下載。

如果 SSH 可用：

```bash
scp root@服務器IP:/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf \
  ./models/mindbridge-qwen2.5-7b-ft/
```

下載到本地項目後，確認：

```bash
ls -lh models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

## 13. 本地 Ollama 接入

確保本地 `models/mindbridge-qwen2.5-7b-ft/Modelfile` 內容類似：

```text
FROM ./mindbridge-qwen2.5-7b-ft-q4_k_m.gguf

SYSTEM """
你是 MindBridge 校園心理關懷智能體，由 Qwen2.5-7B 面向校園心理陪伴場景適配而來。
你需要保持溫和、穩定、非評判的表達風格，優先保護學生安全。
當學生表達普通問題時，正常回答，不強行心理測評。
當學生表達情緒困擾時，先共情，再給出具體、可執行的小步驟。
當學生表達自傷、自殺、傷人等高風險信號時，不提供危險細節，鼓勵其立即聯繫身邊可信任的人、學校輔導員/心理中心或當地緊急救助。
不要向學生輸出後颱風險等級、心理報告、評分或診斷結論。
"""

PARAMETER temperature 0.65
```

創建本地 Ollama 模型：

```bash
cd MindBridge
./scripts/create-finetuned-model.sh
```

檢查：

```bash
/Applications/Ollama.app/Contents/Resources/ollama list
```

應看到：

```text
mindbridge-qwen2.5-7b-ft:latest
```

啓動項目：

```bash
./scripts/run-dev.sh
```

瀏覽器打開：

```text
http://localhost:8080
```

## 14. 項目中如何確認調用的是微調模型

默認模型配置：

```yaml
mindbridge:
  ai:
    provider: ${AI_PROVIDER:ollama}
    ollama:
      model: ${OLLAMA_MODEL:mindbridge-qwen2.5-7b-ft:latest}
```

啓動腳本默認模型：

```bash
OLLAMA_MODEL="${OLLAMA_MODEL:-mindbridge-qwen2.5-7b-ft:latest}"
```

Ollama 客戶端請求體會把該模型名傳給 `/api/chat`：

```java
"model", model
```

整體鏈路：

```text
application.yml / run-dev.sh
        ↓
mindbridge-qwen2.5-7b-ft:latest
        ↓
OllamaAiClient
        ↓
Ollama
        ↓
Modelfile
        ↓
mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

## 15. 常見問題

### 15.1 `mv psychqa_synthetic.jsonl ...` 提示沒有文件

說明當前目錄沒有該文件。先查找：

```bash
find /root /MindBridge -name "psychqa_synthetic.jsonl" 2>/dev/null
```

找到後再複製到：

```text
/root/MindBridge/data/lora/
```

### 15.2 `ValueError: remaining_argv: ['--train_type', 'lora']`

新版 `ms-swift` 使用：

```bash
--tuner_type lora
```

不要使用：

```bash
--train_type lora
```

### 15.3 爲什麼下載 4 個權重文件，總共十幾 GB

這是正常現象。LoRA 微調雖然只訓練 adapter，但基礎模型仍然要參與前向計算，所以必須加載完整 Qwen2.5-7B 權重。FP16/BF16 權重大約 15GB。

### 15.4 爲什麼訓練命令要劃分驗證集

本項目文檔統一按 9:1 劃分訓練集和驗證集，這樣訓練時可以看到驗證集上的評估情況，也更適合寫實驗報告或項目說明。

如果不加 `--split_dataset_ratio 0.1`，2400 條會全部進入訓練集，總步數會變成約 450 步；本文檔按 9:1 劃分後，總步數約爲 405 步。

### 15.5 GGUF 轉換一直停在 0% 或 7%

先檢查文件大小是否增長：

```bash
ls -lh /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf
ps aux | grep convert_hf_to_gguf | grep -v grep
```

如果文件在增長，說明還在寫。雲服務器系統盤 IO 慢時，進度條可能長時間不刷新。

### 15.6 `llama_model_quantize: failed ... iostream error`

大概率是磁盤空間不足。檢查：

```bash
df -h
```

清理殘缺文件和不再需要的目錄：

```bash
rm -f /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
rm -rf /root/MindBridge/output/mindbridge-qwen2.5-7b-ft-hf
rm -rf /root/.cache/modelscope/hub/models/Qwen/Qwen2___5-7B-Instruct
rm -rf /root/.cache/modelscope/hub/models/Qwen/Qwen2.5-7B-Instruct
df -h
```

然後重新量化。

### 15.7 `bash: cmake: 未找到命令`

安裝：

```bash
apt update
apt install -y cmake build-essential
```

或：

```bash
pip install cmake
```

### 15.8 `bash: unzip: 未找到命令`

用 Python 解壓 zip：

```bash
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile(Path("llama.cpp.zip"), "r") as z:
    z.extractall(Path("/root"))
print("done")
PY
```

### 15.9 pip 下載很慢

換源：

```bash
pip install -r requirements.txt \
  -i https://mirrors.aliyun.com/pypi/simple \
  --trusted-host mirrors.aliyun.com
```

或單獨安裝慢的包：

```bash
pip install numpy==1.26.4 \
  -i https://mirrors.aliyun.com/pypi/simple \
  --trusted-host mirrors.aliyun.com
```

## 16. 雲服務器釋放

確認以下文件已經下載到本地：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

並確認本地 Ollama 已經創建：

```bash
/Applications/Ollama.app/Contents/Resources/ollama list
```

看到：

```text
mindbridge-qwen2.5-7b-ft:latest
```

即可在雲平臺釋放實例。注意：如果雲平臺有“關機”和“釋放”，通常應選擇“釋放”，避免繼續計費。

## 17. 產物清單

雲端訓練產物：

```text
/root/MindBridge/output/qwen25-7b-mindbridge-lora/.../checkpoint-405
/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-hf
/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-f16.gguf
/root/MindBridge/output/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

最終交付/運行只需要：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
models/mindbridge-qwen2.5-7b-ft/Modelfile
scripts/create-finetuned-model.sh
scripts/run-dev.sh
```

如果需要證明訓練過程，可以額外保留：

```text
checkpoint-405/
logging.jsonl
args.json
```
