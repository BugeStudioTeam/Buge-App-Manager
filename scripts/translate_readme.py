#!/usr/bin/env python3
import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

LANGS = {
    "en": "English",
    "fr": "French",
    "de": "German",
    "ru": "Russian",
    "uk": "Ukrainian",
    "tr": "Turkish",
    "pt": "Portuguese (Portugal)",
    "pt-rBR": "Portuguese (Brazil)",
    "es": "Spanish",
    "zh": "Simplified Chinese",
    "zh-rTW": "Traditional Chinese (Taiwan)",
    "ar": "Arabic",
    "ja": "Japanese",
    "ko": "Korean",
}

DEEPL_LANG = {
    "en": "EN",
    "fr": "FR",
    "de": "DE",
    "ru": "RU",
    "uk": "UK",
    "tr": "TR",
    "pt": "PT-PT",
    "pt-rBR": "PT-BR",
    "es": "ES",
    "zh": "ZH",
    "zh-rTW": "ZH-HANT",
    "ar": "AR",
    "ja": "JA",
    "ko": "KO",
}

SWITCHER_ORDER = [
    "en", "fr", "de", "ru", "uk", "tr", "pt", "pt-rBR", "es", "zh", "zh-rTW", "ar", "ja", "ko"
]

DEEPL_CONTEXT = (
    "This is a GitHub README for the Android app 'Buge App Manager'. "
    "Do not translate the product name 'Buge App Manager', nor 'Shizuku', 'Dhizuku', "
    "'ROOT', 'Android', 'Material Design 3 Expressive', 'F-Droid', 'GitHub', 'Telegram', "
    "'GNU General Public License v3.0'. Keep URLs, HTML tags, markdown structure, "
    "code blocks and emoji unchanged."
)


def relocate(body: str) -> str:
    body = body.replace('src="assets/', 'src="../../assets/')
    body = body.replace("src='assets/", "src='../../assets/")
    body = body.replace("](assets/", "](../../assets/")
    return body


def deepl_translate(api_key: str, text: str, target_lang: str, timeout: int) -> str:
    endpoint = "https://api-free.deepl.com/v2/translate"
    data = urllib.parse.urlencode({
        "text": text,
        "target_lang": target_lang,
        "context": DEEPL_CONTEXT,
        "preserve_formatting": "1",
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=data,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": "DeepL-Auth-Key " + api_key,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:500]
        raise SystemExit("DeepL request failed for %s: HTTP %s %s" % (target_lang, error.code, detail))
    except urllib.error.URLError as error:
        raise SystemExit("DeepL request failed for %s: %s" % (target_lang, error.reason))

    try:
        return payload["translations"][0]["text"].strip() + "\n"
    except (KeyError, IndexError):
        raise SystemExit("unexpected DeepL response for %s: %s" % (target_lang, json.dumps(payload)[:500]))


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

    timeout = int(os.environ.get("TRANSLATE_TIMEOUT") or "120")

    for code in codes:
        if code == "en":
            text = body
        elif args.dry_run:
            text = body
        else:
            print("translating %s (%s)" % (code, LANGS[code]))
            text = deepl_translate(api_key, body, DEEPL_LANG[code], timeout)
        target = docs / code / "README.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
        print("wrote %s (%d bytes)" % (target, len(text.encode("utf-8"))))

    return 0


if __name__ == "__main__":
    sys.exit(main())