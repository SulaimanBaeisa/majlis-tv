#!/usr/bin/env python3
"""Extracts the English text passed to Ui.tr(pt, english[, spanish]) and the keys of Spanish.java.
Usage: extract_keys.py [--json]  (run from the repo root)"""
import glob, json, re, sys

def lit(tokens):
    return "".join(tokens)

def split_args(src, i):
    """src[i] is just after '('. Returns (list of arg source strings, index after ')')."""
    args, depth, cur, j, n = [], 0, [], i, len(src)
    while j < n:
        c = src[j]
        if c == '"':
            k = j + 1
            while src[k] != '"':
                k += 2 if src[k] == '\\' else 1
            cur.append(src[j:k + 1]); j = k + 1; continue
        if c == "'":
            k = j + 1
            while src[k] != "'":
                k += 2 if src[k] == '\\' else 1
            cur.append(src[j:k + 1]); j = k + 1; continue
        if c in "([{": depth += 1
        elif c in ")]}":
            if depth == 0:
                args.append("".join(cur).strip()); return args, j + 1
            depth -= 1
        elif c == "," and depth == 0:
            args.append("".join(cur).strip()); cur = []; j += 1; continue
        cur.append(c); j += 1
    raise SystemExit("unterminated call")

def unescape(p):
    out, i = [], 0
    while i < len(p):
        c = p[i]
        if c == "\\" and i + 1 < len(p):
            n = p[i + 1]
            if n == "u":
                out.append(chr(int(p[i + 2:i + 6], 16))); i += 6; continue
            out.append({"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\"}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return "".join(out)

def string_value(arg):
    """Value of a (concatenation of) Java string literal(s); None if not purely literals."""
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
    rest = re.sub(r'"(?:[^"\\]|\\.)*"', "", arg)
    if not parts or re.sub(r'[\s+]', "", rest):
        return None
    return "".join(unescape(x) for x in parts)

def main():
    keys, dynamic = {}, []
    for f in sorted(glob.glob("app/src/io/freemytv/*.java")):
        if f.endswith(("Spanish.java", "Arabic.java")):
            continue
        src = open(f, encoding="utf-8").read()
        for m in re.finditer(r'(?<![\w.])(?:Ui\.)?tr\(', src):
            if src[max(0, m.start() - 12):m.start()].endswith("static String "):
                continue
            args, _ = split_args(src, m.end())
            if len(args) < 2:
                continue
            v = string_value(args[1])
            line = src.count("\n", 0, m.start()) + 1
            if v is None:
                dynamic.append((f.split("/")[-1], line, args[1][:60]))
            else:
                keys.setdefault(v, []).append((f.split("/")[-1], line))
    sp = open("app/src/io/freemytv/Spanish.java", encoding="utf-8").read()
    for k in re.findall(r'case "((?:[^"\\]|\\.)*)":', sp):
        keys.setdefault(unescape(k), []).append(("Spanish.java", 0))
    if "--json" in sys.argv:
        json.dump({"keys": sorted(keys), "dynamic": dynamic}, sys.stdout, ensure_ascii=False, indent=1)
    else:
        print(len(keys), "English keys;", len(dynamic), "dynamic second args (not checked)")
        for d in dynamic: print("  dynamic:", d)

main()
