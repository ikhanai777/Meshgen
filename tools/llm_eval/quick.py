# One-shot check of a model on a single request, using the app's exported prompt and grammar.
# Usage: python tools/llm_eval/quick.py MODEL.gguf "request" [system.txt] [grammar.gbnf]
import sys, time
from llama_cpp import Llama, LlamaGrammar

model, req = sys.argv[1], sys.argv[2]
base = '/home/user/Meshgen/core/build/llm/'
system = open(sys.argv[3] if len(sys.argv) > 3 else base + 'system.txt').read()
grammar = LlamaGrammar.from_string(open(sys.argv[4] if len(sys.argv) > 4 else base + 'grammar.gbnf').read(), verbose=False)
llm = Llama(model_path=model, n_ctx=8192, n_threads=4, n_batch=512, verbose=False, seed=7)
prompt = (f"<|im_start|>system\n{system}<|im_end|>\n<|im_start|>user\nRequest: {req}<|im_end|>\n"
          f"<|im_start|>assistant\n<think>\n\n</think>\n\n")
t = time.time()
n = len(llm.tokenize(prompt.encode(), special=True))
out = llm.create_completion(prompt, grammar=grammar, max_tokens=1400, temperature=0.2, top_p=0.9, top_k=40, stop=["<|im_end|>"])
print(f"prompt_tokens={n} gen_tokens={out['usage']['completion_tokens']} secs={time.time() - t:.0f}")
print(out['choices'][0]['text'])
