"""Convert official K-FIND XLSX exports to operator-only catalog JSONL.

pip install -r scripts/food-catalog/requirements.txt
python scripts/food-catalog/convert_mfds.py --output /private/catalog.jsonl /private/foods.xlsx
Start server with ROUTINLOG_FOOD_CATALOG_IMPORT_FILE=/private/catalog.jsonl.
Retain the original XLSX, manifest and rejected rows outside Git; never use inferred nutrient values.
"""
import argparse
from collections import Counter
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
import openpyxl

URL = 'https://various.foodsafetykorea.go.kr/nutrient/general/down/historyList.do'
NUTRIENTS = {'kcal': '에너지(kcal)', 'carbsG': '탄수화물(g)', 'proteinG': '단백질(g)', 'fatG': '지방(g)', 'fiberG': '식이섬유(g)'}

def text(value):
    if value is None:
        return ''
    if isinstance(value, (date, datetime)):
        return value.isoformat()[:10]
    return str(value).strip()

def convert(row):
    def cell(key): return text(row.get(key))
    code, name = cell('식품코드'), cell('식품명')
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', code) or not name:
        raise ValueError('Invalid food identity')
    basis = cell('영양성분함량기준량')
    match = re.fullmatch(r'(\d+(?:\.\d+)?)\s*(g|ml)', basis, re.I)
    amount, unit = (match[1], match[2].lower()) if match else (None, 'UNKNOWN')
    if amount is not None and Decimal(amount) <= 0:
        raise ValueError('Invalid basis')
    nutrition, notes = {}, {}
    for key, header in NUTRIENTS.items():
        raw = cell(header)
        if raw == '':
            nutrition[key] = None
            continue
        try:
            number = Decimal(raw.replace(',', ''))
        except InvalidOperation:
            nutrition[key] = None
            notes[key] = raw
            continue
        if not number.is_finite() or not 0 <= number <= 1000000 or number.as_tuple().exponent < -10:
            raise ValueError('Invalid nutrient: ' + header)
        nutrition[key] = format(number, 'f')
    updated = cell('데이터기준일자')
    date.fromisoformat(updated)
    category = cell('데이터구분명')
    if category not in ('음식', '가공식품', '원재료성식품'):
        raise ValueError('Unsupported category: ' + category)
    brand = cell('업체명')
    if brand in ('', '해당없음', '-'):
        brand = None
    return dict(id=code, name=name, brand=brand, category=category, basisLabel=basis,
                basisAmount=amount, basisUnit=unit, nutrition=nutrition, nutrientNotes=notes,
                sourceName=' / '.join(filter(None, [cell('출처명'), cell('식품기원명')])),
                sourceUpdatedAt=updated, sourceUrl=URL)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('inputs', type=Path, nargs='+')
    args = parser.parse_args()
    output = args.output.resolve()
    if output in [p.resolve() for p in args.inputs]:
        parser.error('Output cannot overwrite a source workbook')
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_suffix('.jsonl.partial')
    counts, seen, files = Counter(), set(), []
    with temp.open('w', encoding='utf-8') as target, output.with_suffix('.rejected.jsonl').open('w', encoding='utf-8') as rejected:
        for path in args.inputs:
            with path.open('rb') as raw:
                checksum = hashlib.file_digest(raw, 'sha256').hexdigest()
            files.append(dict(name=path.name, sha256=checksum))
            book = openpyxl.load_workbook(path, read_only=True, data_only=True)
            try:
                for sheet in book:
                    rows = sheet.values
                    headers = [text(v) for v in next(rows)]
                    required = {'식품코드','식품명','영양성분함량기준량','데이터구분명','데이터기준일자',*NUTRIENTS.values()}
                    if not required.issubset(headers):
                        raise ValueError('Workbook headers changed: ' + sheet.title)
                    for number, values in enumerate(rows, 2):
                        row = dict(zip(headers, values))
                        try:
                            item = convert(row)
                            if item['id'] in seen:
                                raise ValueError('Duplicate food code')
                            seen.add(item['id'])
                        except (ValueError, InvalidOperation) as error:
                            counts['rejected'] += 1
                            rejected.write(json.dumps(dict(file=path.name, sheet=sheet.title, row=number, reason=str(error), original={k:text(v) for k,v in row.items()}), ensure_ascii=False)+'\n')
                            continue
                        target.write(json.dumps(item, ensure_ascii=False, separators=(',', ':'))+'\n')
                        counts['accepted'] += 1
                        counts['unit_'+item['basisUnit']] += 1
                        counts['category_'+item['category']] += 1
            finally:
                book.close()
            print(json.dumps(dict(file=path.name, counts=counts), ensure_ascii=False), flush=True)
    if not counts['accepted']:
        raise ValueError('No valid source rows; existing catalog file kept')
    temp.replace(output)
    manifest = dict(source=URL, files=files, counts=counts, formatVersion=1)
    output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False), flush=True)

if __name__ == '__main__':
    main()
