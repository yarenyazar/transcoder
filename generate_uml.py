import os
import re

def process_project():
    uml_lines = []
    uml_lines.append("@startuml")
    uml_lines.append("!theme plain")
    uml_lines.append("scale max 4000 width")
    uml_lines.append("skinparam linetype ortho")
    uml_lines.append("skinparam nodesep 50")
    uml_lines.append("skinparam ranksep 50")
    uml_lines.append("skinparam classAttributeIconSize 0")
    uml_lines.append("set namespaceSeparator ::")
    
    relations = set()
    known_classes = set()
    
    # regexes
    java_class_rx = re.compile(r'(?:public\s+|abstract\s+|final\s+)*(class|interface|enum)\s+(\w+)(?:<.*?>)?(?:\s+extends\s+([A-Za-z0-9_]+))?(?:\s+implements\s+([A-Za-z0-9_,\s]+))?')
    java_field_rx = re.compile(r'^\s*(private|protected|public)\s+(?:final\s+|static\s+|volatile\s+)*([\w<>,\[\]\?]+)\s+([a-zA-Z_$][\w$]*)\s*[;=]')
    java_method_rx = re.compile(r'^\s*(public|protected|private)\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+)*([\w<>,\[\]\?]+)\s+([a-zA-Z_$][\w$]*)\s*\(')
    
    ts_class_rx = re.compile(r'(?:export\s+|abstract\s+|default\s+)*(class|interface|enum)\s+(\w+)(?:<.*?>)?(?:\s+extends\s+([A-Za-z0-9_]+))?(?:\s+implements\s+([A-Za-z0-9_,\s]+))?')
    ts_prop_rx = re.compile(r'^\s*(?:private|protected|public|readonly)?\s*([a-zA-Z_$][\w$]*)\??\s*:\s*([^;=]+)(?:;|$)')
    ts_method_rx = re.compile(r'^\s*(?:public|private|protected)?\s*(?:static\s+)?(?:async\s+)?([a-zA-Z_$][\w$]*)\s*\(')
    
    py_class_rx = re.compile(r'^class\s+(\w+)(?:\((.*?)\))?:')
    py_method_rx = re.compile(r'^\s*def\s+([a-zA-Z_]\w*)\s*\(')

    def to_visibility(modifier):
        if 'private' in modifier: return '-'
        if 'protected' in modifier: return '#'
        return '+'

    # Phase 1: gather class names
    for root_dir in ['backend/src/main/java', 'frontend/src/app', 'subtitle-service']:
        if not os.path.exists(root_dir): continue
        for root, _, files in os.walk(root_dir):
            for file in files:
                if file.endswith('.java'):
                    with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                        for line in f:
                            m = java_class_rx.search(line)
                            if m: known_classes.add(m.group(2))
                elif file.endswith('.ts'):
                    with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                        for line in f:
                            m = ts_class_rx.search(line)
                            if m: known_classes.add(m.group(2))
                elif file.endswith('.py'):
                    with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                        for line in f:
                            m = py_class_rx.search(line)
                            if m: known_classes.add(m.group(1))

    # Phase 2: process files
    for root_dir in ['backend/src/main/java', 'frontend/src/app', 'subtitle-service']:
        if not os.path.exists(root_dir): continue
        
        system_name = "Backend" if "backend" in root_dir else "Frontend" if "frontend" in root_dir else "SubtitleService"
        uml_lines.append(f'package "{system_name}" {{')
        
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                filepath = os.path.join(root, file)
                
                if file.endswith('.java'):
                    with open(filepath, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                    
                    in_class = False
                    current_class = ""
                    for line in lines:
                        line_stripped = line.strip()
                        m = java_class_rx.search(line_stripped)
                        if m and not line_stripped.startswith('//') and not line_stripped.startswith('*'):
                            typ = "enum" if "enum" in m.group(1) else "interface" if "interface" in m.group(1) else "class"
                            current_class = m.group(2)
                            uml_lines.append(f"  {typ} {current_class} <<Java>> {{")
                            in_class = True
                            
                            extends = m.group(3)
                            if extends and extends in known_classes:
                                relations.add(f"{extends} <|-- {current_class}")
                            
                            implements = m.group(4)
                            if implements:
                                for imp in implements.split(','):
                                    imp = imp.strip()
                                    if imp in known_classes:
                                        relations.add(f"{imp} <|.. {current_class}")
                            continue
                            
                        if in_class:
                            if line_stripped == '}':
                                uml_lines.append("  }")
                                in_class = False
                                continue
                            
                            fm = java_field_rx.match(line)
                            if fm:
                                vis = to_visibility(fm.group(1))
                                f_type = fm.group(2)
                                f_name = fm.group(3)
                                uml_lines.append(f"    {vis}{f_name}: {f_type}")
                                
                                # relation
                                t_clean = re.sub(r'[^a-zA-Z0-9_]', '', f_type)
                                if t_clean in known_classes and t_clean != current_class:
                                    relations.add(f"{current_class} --> {t_clean} : {f_name}")
                                
                                # Check List collections etc
                                m_col = re.search(r'<(.*?)>', f_type)
                                if m_col:
                                    t2 = m_col.group(1)
                                    if t2 in known_classes and t2 != current_class:
                                        relations.add(f"{current_class} o-- {t2}")

                            else:
                                mm = java_method_rx.match(line)
                                if mm:
                                    vis = to_visibility(mm.group(1))
                                    m_type = mm.group(2)
                                    m_name = mm.group(3)
                                    uml_lines.append(f"    {vis}{m_name}(): {m_type}")
                
                elif file.endswith('.ts') and not file.endswith('.spec.ts'):
                    with open(filepath, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                        
                    in_class = False
                    current_class = ""
                    for line in lines:
                        line_stripped = line.strip()
                        m = ts_class_rx.search(line_stripped)
                        if m and not line_stripped.startswith('//') and not line_stripped.startswith('*'):
                            typ = "enum" if "enum" in m.group(1) else "interface" if "interface" in m.group(1) else "class"
                            current_class = m.group(2)
                            uml_lines.append(f"  {typ} {current_class} <<TS>> {{")
                            in_class = True
                            
                            extends = m.group(3)
                            if extends and extends in known_classes:
                                relations.add(f"{extends} <|-- {current_class}")
                            
                            implements = m.group(4)
                            if implements:
                                for imp in implements.split(','):
                                    imp = imp.strip()
                                    if imp in known_classes:
                                        relations.add(f"{imp} <|.. {current_class}")
                            continue
                            
                        if in_class:
                            if line_stripped == '}':
                                uml_lines.append("  }")
                                in_class = False
                                continue
                            
                            fm = ts_prop_rx.match(line)
                            if fm:
                                f_name = fm.group(1)
                                f_type = fm.group(2).strip()
                                if '(' not in f_type and '{' not in f_type and '=>' not in f_type:
                                    uml_lines.append(f"    +{f_name}: {f_type}")
                                    
                                    t_clean = re.sub(r'[^a-zA-Z0-9_]', '', f_type)
                                    if t_clean in known_classes and t_clean != current_class:
                                        relations.add(f"{current_class} --> {t_clean} : {f_name}")
                                    
                                    m_col = re.search(r'<(.*?)>', f_type)
                                    if m_col:
                                        t2 = m_col.group(1)
                                        if t2 in known_classes and t2 != current_class:
                                            relations.add(f"{current_class} o-- {t2}")
                                    m_arr = re.search(r'([A-Za-z0-9_]+)\[\]', f_type)
                                    if m_arr:
                                        t2 = m_arr.group(1)
                                        if t2 in known_classes and t2 != current_class:
                                            relations.add(f"{current_class} o-- {t2}")

                            else:
                                mm = ts_method_rx.match(line)
                                if mm and "constructor" not in mm.group(1):
                                    m_name = mm.group(1)
                                    uml_lines.append(f"    +{m_name}()")
                
                elif file.endswith('.py'):
                    with open(filepath, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                    
                    current_class = ""
                    for line in lines:
                        m = py_class_rx.search(line)
                        if m:
                            if current_class:
                                uml_lines.append("  }")
                            current_class = m.group(1)
                            uml_lines.append(f"  class {current_class} <<Python>> {{")
                            parents = m.group(2)
                            if parents:
                                for p in parents.split(','):
                                    p = p.strip()
                                    if p in known_classes:
                                        relations.add(f"{p} <|-- {current_class}")
                            continue
                        
                        if current_class:
                            mm = py_method_rx.match(line)
                            if mm:
                                m_name = mm.group(1)
                                uml_lines.append(f"    +{m_name}()")
                    if current_class:
                        uml_lines.append("  }")
                        
        uml_lines.append("}")

    for r in relations:
        uml_lines.append(r)
        
    uml_lines.append("@enduml")
    
    with open('/Users/yarenyazar/Desktop/ott/yaren-transcoder/.gemini/scratch/plantuml.txt', 'w', encoding='utf-8') as f:
        f.write("\n".join(uml_lines))
        print(f"Generated {len(uml_lines)} lines of PlantUML.")

if __name__ == '__main__':
    process_project()
