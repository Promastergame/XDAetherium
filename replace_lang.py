import re
import json

with open('mc_languages.json', 'r', encoding='utf-8') as f:
    lang_map = json.load(f)

# Manually add some custom/missing ones just in case
lang_map['default'] = 'Don\'t Change (Default)'
lang_map['en_pt'] = 'Anglish'
lang_map['es_an'] = 'Andalûh (Andalucía)'
lang_map['en_ws'] = 'Pirate Speak'
lang_map['lol_us'] = 'LOLCAT (Kingdom of Cats)'

java_file = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/LanguageDatabase.java'
with open(java_file, 'r', encoding='utf-8') as f:
    content = f.read()

def replace_match(match):
    name = match.group(1)
    code = match.group(2)
    
    native_name = lang_map.get(code)
    if native_name:
        return f'LANGUAGES.add(new MCLanguage("{native_name}", "{code}"));'
    return match.group(0)

# The pattern looks for: LANGUAGES.add(new MCLanguage("Name", "code"));
new_content = re.sub(r'LANGUAGES\.add\(new MCLanguage\("([^"]+)", "([^"]+)"\)\);', replace_match, content)

with open(java_file, 'w', encoding='utf-8') as f:
    f.write(new_content)
