#!/usr/bin/env python3
"""Runner di prova: foto prescrizione -> JSON piano terapeutico (OpenAI vision).
Uso: python3 runner.py <img_path> [model]
La chiave si legge da OPENAI_API_KEY. Nessuna chiave hardcoded.
"""
import base64, json, os, sys, urllib.request, urllib.error, pathlib

HERE = pathlib.Path(__file__).parent
MODEL = sys.argv[2] if len(sys.argv) > 2 else "gpt-5.4"

def system_prompt():
    txt = (HERE / "prompt.md").read_text(encoding="utf-8")
    start = txt.index("## System / istruzioni") + len("## System / istruzioni")
    end = txt.index("## User")
    return txt[start:end].strip()

def main():
    img_path = sys.argv[1]
    key = os.environ["OPENAI_API_KEY"]
    schema = json.loads((HERE / "schema.json").read_text(encoding="utf-8"))
    b64 = base64.b64encode(pathlib.Path(img_path).read_bytes()).decode()
    data_uri = f"data:image/jpeg;base64,{b64}"

    body = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": system_prompt()},
            {"role": "user", "content": [
                {"type": "text", "text": "Allegata: la foto della prescrizione. Estrai il piano terapeutico in JSON secondo lo schema."},
                {"type": "image_url", "image_url": {"url": data_uri, "detail": "high"}},
            ]},
        ],
        "response_format": {"type": "json_schema", "json_schema": schema},
        "max_completion_tokens": 8000,
        "reasoning_effort": "high",
    }
    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions",
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            resp = json.load(r)
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:2000], file=sys.stderr)
        sys.exit(1)
    msg = resp["choices"][0]["message"]
    print(json.dumps(json.loads(msg["content"]), ensure_ascii=False, indent=2))
    usage = resp.get("usage", {})
    print(f"\n# model={resp.get('model')} usage={usage}", file=sys.stderr)

if __name__ == "__main__":
    main()
