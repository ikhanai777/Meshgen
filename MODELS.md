# Models

Every AI model must have its license checked for **commercial use** before it is integrated.
Models are never bundled in the APK; they are downloaded on first use from the in-app **Models** screen,
pinned to an exact Hugging Face commit and verified by SHA-256 after download.

| Model | Used by | Source (pinned) | License | Commercial use | Size | Quantization | Status |
|---|---|---|---|---|---|---|---|
| **Qwen3 1.7B** (instruct) | Engine 1, text → shape (default) | [unsloth/Qwen3-1.7B-GGUF](https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/tree/d7f544eead698dbd1f15126ef60b45a1e1933222) `Qwen3-1.7B-Q4_K_M.gguf` @ `d7f544e`, quantization of [Qwen/Qwen3-1.7B](https://huggingface.co/Qwen/Qwen3-1.7B) | Apache 2.0 ([LICENSE](https://huggingface.co/Qwen/Qwen3-1.7B/blob/main/LICENSE)) | Yes | 1.11 GB | GGUF Q4_K_M | Integrated (Phase 3) |
| **Qwen3 4B** (instruct) | Engine 1, optional "smart" model | [Qwen/Qwen3-4B-GGUF](https://huggingface.co/Qwen/Qwen3-4B-GGUF/tree/bc640142c66e1fdd12af0bd68f40445458f3869b) `Qwen3-4B-Q4_K_M.gguf` @ `bc64014` (official) | Apache 2.0 ([LICENSE](https://huggingface.co/Qwen/Qwen3-4B/blob/main/LICENSE)) | Yes | 2.50 GB | GGUF Q4_K_M | Integrated (Phase 3) |

SHA-256:
- `Qwen3-1.7B-Q4_K_M.gguf` b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897
- `Qwen3-4B-Q4_K_M.gguf` 7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5

Runtime: [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT), source taken from the
`llama-cpp-python` 0.3.36 sdist on PyPI (SHA-256 `832db069…c1a2e`, see `tools/fetch_llama_cpp.sh`).

Considered and not used (Phase 3):
- **Gemma 4 E2B** (Apache 2.0): ~3 GB at 4-bit, too heavy for 6 GB phones.
- **MediaPipe LLM Inference API**: in maintenance mode per Google; no grammar-constrained decoding.

_Not yet integrated, license to be verified before use:_ TripoSR (Engine 2), a background-removal model (Engine 2),
an on-device text-to-image model (Engine 2, optional).
