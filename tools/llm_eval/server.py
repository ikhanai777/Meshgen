# Minimal llama.cpp server for evaluating the shape agent on a desktop.
# Implements the same contract as the app's TextGenerator: prefix (cached) + suffix, GBNF grammar.
# Usage: python tools/llm_eval/server.py MODEL.gguf [port]
import sys, json, time
from http.server import BaseHTTPRequestHandler, HTTPServer
from llama_cpp import Llama, LlamaGrammar

model = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 8765
llm = Llama(model_path=model, n_ctx=8192, n_threads=4, n_batch=512, verbose=False, seed=1234)
grammars = {}

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_POST(self):
        req = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        g = req.get('grammar')
        if g and g not in grammars: grammars[g] = LlamaGrammar.from_string(g, verbose=False)
        t0 = time.time()
        prompt = req['prefix'] + req['suffix']
        n_prompt = len(llm.tokenize(prompt.encode(), special=True))
        out = llm.create_completion(prompt, grammar=grammars.get(g), max_tokens=req['max_tokens'],
                                    temperature=req['temperature'], top_p=0.9, top_k=40, repeat_penalty=1.0,
                                    stop=["<|im_end|>"])
        text = out['choices'][0]['text']
        body = json.dumps({'text': text, 'tokens': out['usage']['completion_tokens'], 'prompt_tokens': n_prompt,
                           'seconds': time.time() - t0}).encode()
        self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers(); self.wfile.write(body)

print(f"serving {model} on {port}", flush=True)
HTTPServer(('127.0.0.1', port), H).serve_forever()
