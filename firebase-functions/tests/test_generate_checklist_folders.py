"""Unit tests for sanitize_generated_items (generated_items.py).

Run from firebase-functions/:
    python -m pytest tests/test_generate_checklist_folders.py -q
or without pytest:
    python tests/test_generate_checklist_folders.py

No Firebase credentials needed — generated_items.py is dependency-free (like cors.py).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from generated_items import sanitize_generated_items, MAX_FOLDER_DEPTH


def test_flat_list_passes_through_as_leaves():
    # Back-compat: a flat legacy list stays flat; checked is normalized to False (template).
    items = [{"text": "a", "checked": False}, {"text": "b", "checked": True}]
    out = sanitize_generated_items(items)
    assert out == [{"text": "a", "checked": False}, {"text": "b", "checked": False}]


def test_nested_folder_preserved_within_depth():
    items = [
        {"text": "Groceries", "type": "folder", "children": [
            {"text": "Milk", "checked": False},
            {"text": "Dairy", "type": "folder", "children": [
                {"text": "Cheese", "checked": False},
            ]},
        ]},
        {"text": "Standalone", "checked": False},
    ]
    out = sanitize_generated_items(items)
    assert out[0]["type"] == "folder"
    assert out[0]["text"] == "Groceries"
    assert out[0]["children"][0] == {"text": "Milk", "checked": False}
    assert out[0]["children"][1]["type"] == "folder"
    assert out[0]["children"][1]["children"][0] == {"text": "Cheese", "checked": False}
    assert out[1] == {"text": "Standalone", "checked": False}


def test_depth_beyond_cap_is_flattened_to_leaf():
    node = {"text": "leaf", "checked": False}
    for i in range(MAX_FOLDER_DEPTH + 2):
        node = {"text": "f%d" % i, "type": "folder", "children": [node]}
    out = sanitize_generated_items([node])

    def measured_depth(nodes, d=0):
        m = d
        for n in nodes:
            if n.get("type") == "folder":
                m = max(m, measured_depth(n["children"], d + 1))
        return m

    assert measured_depth(out) <= MAX_FOLDER_DEPTH


def test_total_count_capped():
    items = [{"text": "i%d" % n, "checked": False} for n in range(500)]
    out = sanitize_generated_items(items, max_total=10)
    assert len(out) == 10


def test_malformed_entries_skipped():
    items = [
        "not a dict",
        {"no_text": 1},
        {"text": ""},
        {"text": None},
        {"text": "valid", "checked": False},
    ]
    out = sanitize_generated_items(items)
    assert out == [{"text": "valid", "checked": False}]


def test_folder_without_children_key_is_leaf():
    # type=folder but no children list -> treated as a leaf (defensive against malformed AI output).
    items = [{"text": "weird", "type": "folder"}]
    out = sanitize_generated_items(items)
    assert out == [{"text": "weird", "checked": False}]


def test_empty_folder_children_preserved():
    items = [{"text": "Empty", "type": "folder", "children": []}]
    out = sanitize_generated_items(items)
    assert out == [{"text": "Empty", "type": "folder", "children": []}]


def test_non_list_input_is_safe():
    assert sanitize_generated_items(None) == []
    assert sanitize_generated_items("nope") == []


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for fn in fns:
        try:
            fn()
            print("PASS " + fn.__name__)
        except AssertionError as e:
            failed += 1
            print("FAIL %s: %s" % (fn.__name__, e))
    print("\n%d/%d passed" % (len(fns) - failed, len(fns)))
    sys.exit(1 if failed else 0)
