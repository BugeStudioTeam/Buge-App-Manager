#!/usr/bin/env python3
import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

LANGS = {
    "en": ("English", "English", "Read this in another language"),
    "fr": ("French", "Français", "Lire dans une autre langue"),
    "de": ("German", "Deutsch", "In einer anderen Sprache lesen"),
    "ru": ("Russian", "Русский", "Читать на другом языке"),
    "uk": ("Ukrainian", "Українська", "Читати іншою мовою"),
    "tr": ("Turkish", "Türkçe", "Başka bir dilde okuyun"),
    "pt": ("Portuguese (Portugal)", "Português", "Ler noutro idioma"),
    "pt-rBR": ("Portuguese (Brazil)", "Português (Brasil)", "Ler em outro idioma"),
    "es": ("Spanish", "Español", "Leer en otro idioma"),
    "zh": ("Simplified Chinese", "中文 (简体)", "阅读其他语言版本"),
    "zh-rTW": ("Traditional Chinese (Taiwan)", "中文 (繁体)", "閱讀其他語言版本"),
    "ar": ("Arabic", "العربية", "اقرأ بلغة أخرى"),
    "ja": ("Japanese", "日本語", "他の言語で読む"),
    "ko": ("Korean", "한국어", "다른 언어로 읽기"),
}

SWITCHER_ORDER = [
    "en", "fr", "de", "ru", "uk", "tr", "pt", "pt-rBR", "es", "zh", "zh-rTW", "ar", "ja", "ko"
]

SYSTEM_PROMPT = (
    "You translate GitHub README files. You keep the markdown structure, HTML tags, "
    "image and badge URLs, tables, emoji, code blocks and horizontal rules exactly as they are. "
    "You never translate the product name 'Buge App Manager', the names 'Shizuku', 'Dhizuku', "
    "'ROOT', 'Android', 'Material Design 3 Expressive', 'F-Droid', 'GitHub', 'Telegram' or "
    "'GNU General Public License v3.0'. You output only the translated markdown, with no "
    "introduction, no notes and no code fences around it."
)

USER_PROMPT = """Translate this README into {language}. Keep every URL and every HTML attribute unchanged.

{body}
"""


def switcher(current: str) -> str:
    phrase = LANGS[current][2]
    return "🌐 [%s](../README.md)\n" % phrase


def relocate(body: str) -> str:
    body = body.replace('src="assets/', 'src="../../assets/')
    body = body.replace("src='assets/", "src='../../assets/")
    body = body.replace("](assets/", "](../../assets/")
    return body


def insert_switcher(body: str, current: str) -> str:
    marker = "**Buge App Manager**"
    lines = body.splitlines()
    for index, line in enumerate(lines):
        if line.startswith(marker):
            lines.insert(index, switcher(current))
            lines.insert(index + 1, "")
            break
    return "\n".join(lines).rstrip() + "\n"


def chat(base_url: str, api_key: str, model: str, body: str, language: str, timeout: int) -> str:
    payload = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": USER_PROMPT.format(language=language, body=body)},
        ],
    }
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + api_key,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:500]
        raise SystemExit("request failed for %s: HTTP %s %s" % (language, error.code, detail))
    except urllib.error.URLError as error:
        raise SystemExit("request failed for %s: %s" % (language, error.reason))

    try:
        content = data["choices"][0]["message"]["content"]
    except (KeyError, IndexError):
        raise SystemExit("unexpected response for %s: %s" % (language, json.dumps(data)[:500]))

    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[-1]
        if content.rstrip().endswith("```"):
            content = content.rstrip()[:-3]
        content = content.strip()
    return content + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate README.md into docs/<lang>/README.md")
    parser.add_argument("--source", default="README.md")
    parser.add_argument("--docs", default="docs")
    parser.add_argument("--langs", default=",".join(SWITCHER_ORDER))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    source = pathlib.Path(args.source)
    if not source.is_file():
        print("missing source: %s" % source, file=sys.stderr)
        return 1

    codes = [code.strip() for code in args.langs.split(",") if code.strip()]
    unknown = [code for code in codes if code not in LANGS]
    if unknown:
        print("unknown languages: %s" % ", ".join(unknown), file=sys.stderr)
        return 1

    docs = pathlib.Path(args.docs)

    if args.check:
        missing = []
        for code in codes:
            target = docs / code / "README.md"
            if not target.is_file() or target.stat().st_size == 0:
                missing.append(str(target))
        if missing:
            print("missing translations:\n  " + "\n  ".join(missing), file=sys.stderr)
            return 1
        print("all %d translations present" % len(codes))
        return 0

    body = relocate(source.read_text(encoding="utf-8"))

    api_key = os.environ.get("TRANSLATE_API_KEY", "")
    if not api_key and not args.dry_run:
        print("TRANSLATE_API_KEY is not set", file=sys.stderr)
        return 1

    base_url = os.environ.get("TRANSLATE_API_BASE") or "https://api.openai.com/v1"
    model = os.environ.get("TRANSLATE_MODEL") or "gpt-4o-mini"
    timeout = int(os.environ.get("TRANSLATE_TIMEOUT") or "180")

    for code in codes:
        language = LANGS[code][0]
        if args.dry_run or code == "en":
            text = with_switcher(body, code)
        else:
            print("translating %s (%s)" % (code, language))
            text = with_switcher(chat(base_url, api_key, model, body, language, timeout), code)
        target = docs / code / "README.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
        print("wrote %s (%d bytes)" % (target, len(text.encode("utf-8"))))

    return 0


def with_switcher(body: str, code: str) -> str:
    return insert_switcher(body, code)


if __name__ == "__main__":
    sys.exit(main())
