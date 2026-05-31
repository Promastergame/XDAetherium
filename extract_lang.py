import json

with open('C:/Users/79129/AppData/Roaming/.minecraft/assets/objects/64/6438510b019473a6509b8abb22530ae4d9da2251', 'r', encoding='utf-8') as f:
    data = json.load(f)

result = {k: v['name'] + ' (' + v['region'] + ')' for k, v in data['language'].items()}

with open('C:/Users/79129/Desktop/XDAetherium-main/mc_languages.json', 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)
