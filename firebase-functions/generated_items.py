"""Sanitizer for AI-generated checklist items (dependency-free, unit-testable without firebase_admin).

Used by generate_checklist in main.py to clamp the AI's (possibly nested folder) output
before returning it to the client. Kept in its own module — like cors.py — so it can be
unit-tested without importing main.py (which initializes Firebase Admin at import time).
"""

# Limits for AI-generated nested folder structures (defend the 1 MiB Firestore document
# limit and keep the client tree usable). Folders deeper than MAX_FOLDER_DEPTH are flattened;
# emission stops past MAX_GENERATED_NODES total nodes.
MAX_FOLDER_DEPTH = 5
MAX_GENERATED_NODES = 300


def sanitize_generated_items(items, max_depth=MAX_FOLDER_DEPTH, max_total=MAX_GENERATED_NODES):
    """Clamp an AI-generated (possibly nested) item list before returning it to the client.

    - Caps nesting depth at max_depth: a folder deeper than the cap is flattened (its
      contents are promoted to leaf items at the deepest allowed level).
    - Caps the total number of emitted nodes at max_total (defends the 1 MiB Firestore
      document limit, since the whole tree is serialized into one checklist document).
    - Defensive against malformed AI output: non-dict entries and entries without a
      non-empty string text are skipped; a folder is recognized only when type=="folder"
      and children is a list.

    Back-compat: a flat list of {"text","checked"} items passes through as leaves
    (no "type"/"children" keys -> every node is treated as a leaf).
    """
    counter = {"n": 0}

    def walk(nodes, depth):
        result = []
        if not isinstance(nodes, list):
            return result
        for node in nodes:
            if counter["n"] >= max_total:
                break
            if not isinstance(node, dict):
                continue
            text = node.get("text")
            if not text or not isinstance(text, str):
                continue
            is_folder = node.get("type") == "folder" and isinstance(node.get("children"), list)
            if is_folder and depth < max_depth:
                counter["n"] += 1
                children = walk(node.get("children"), depth + 1)
                result.append({"text": text, "type": "folder", "children": children})
            elif is_folder:
                # Folder past the depth cap: flatten instead of dropping its contents. Emit the
                # folder's own label as a leaf, then promote every descendant to a leaf at this
                # (deepest allowed) level — walking with the SAME depth so nested sub-folders also
                # collapse to leaves here rather than vanishing. Matches the docstring contract
                # ("contents are promoted to leaf items at the deepest allowed level").
                counter["n"] += 1
                result.append({"text": text, "checked": False})
                result.extend(walk(node.get("children"), depth))
            else:
                # Plain leaf item.
                counter["n"] += 1
                result.append({"text": text, "checked": False})
        return result

    return walk(items, 0)
