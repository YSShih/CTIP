#!/usr/bin/env python3
"""OpenAPI 破壞性變更檢查(docs/spec/09-api.md §9.6)。

用法:openapi-breaking-check.py <base.json> <new.json>

檢查三類未預期的破壞性變更(任一命中即 exit 1):
  1. 移除端點(base 有的 path+method 在 new 消失)
  2. 移除必填欄位(同名 schema 的 required 項目消失)
  3. 變更型別(同名 schema 的同名 property 其 type/$ref/format 改變)

新增端點、新增欄位、放寬(required 移除欄位本身仍存在)以外的縮限皆不在此列——
語意層審查由 code review 負責,本腳本只擋機器可判定的破壞。
"""

import json
import sys


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def operations(doc):
    methods = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
    for path, item in (doc.get("paths") or {}).items():
        for method in item:
            if method in methods:
                yield f"{method.upper()} {path}"


def property_shape(prop):
    return {k: prop.get(k) for k in ("type", "$ref", "format", "items")}


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    base, new = load(sys.argv[1]), load(sys.argv[2])
    problems = []

    new_ops = set(operations(new))
    for op in operations(base):
        if op not in new_ops:
            problems.append(f"移除端點:{op}")

    base_schemas = (base.get("components") or {}).get("schemas") or {}
    new_schemas = (new.get("components") or {}).get("schemas") or {}
    for name, base_schema in base_schemas.items():
        new_schema = new_schemas.get(name)
        if new_schema is None:
            continue  # schema 移除若仍被引用會反映在端點差異;孤兒 schema 消失不算破壞
        removed_required = set(base_schema.get("required") or []) - set(new_schema.get("required") or [])
        for field in sorted(removed_required):
            problems.append(f"移除必填欄位:{name}.{field}")
        new_props = new_schema.get("properties") or {}
        for prop, base_prop in (base_schema.get("properties") or {}).items():
            if prop in new_props and property_shape(base_prop) != property_shape(new_props[prop]):
                problems.append(f"變更型別:{name}.{prop} {property_shape(base_prop)} -> {property_shape(new_props[prop])}")

    if problems:
        print("[FAIL] OpenAPI 破壞性變更:")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("[PASS] 無破壞性變更")
    return 0


if __name__ == "__main__":
    sys.exit(main())
