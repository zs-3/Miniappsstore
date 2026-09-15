#!/usr/bin/env python3
import os
import sys
import json
import zipfile
import argparse

def main():
    parser = argparse.ArgumentParser(description="MistFox Mini App Package Builder (.pkg)")
    parser.add_argument("src_dir", help="Path to mini-app source directory containing manifest.json")
    parser.add_argument("-o", "--output", help="Output .pkg file path", default=None)

    args = parser.parse_args()
    src_dir = os.path.abspath(args.src_dir)

    if not os.path.isdir(src_dir):
        print(f"Error: Source directory '{src_dir}' does not exist.", file=sys.stderr)
        sys.exit(1)

    manifest_path = os.path.join(src_dir, "manifest.json")
    if not os.path.isfile(manifest_path):
        print(f"Error: manifest.json not found in '{src_dir}'.", file=sys.stderr)
        sys.exit(1)

    with open(manifest_path, "r", encoding="utf-8") as f:
        try:
            manifest = json.load(f)
        except Exception as e:
            print(f"Error: Failed to parse manifest.json: {e}", file=sys.stderr)
            sys.exit(1)

    app_id = manifest.get("id")
    if not app_id:
        print("Error: manifest.json must contain an 'id' field.", file=sys.stderr)
        sys.exit(1)

    output_path = args.output
    if not output_path:
        output_path = os.path.join(os.path.dirname(src_dir), f"{app_id}.pkg")

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zip_out:
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_file = os.path.join(root, file)
                rel_path = os.path.relpath(abs_file, src_dir)
                zip_out.write(abs_file, rel_path)

    print(f"Successfully packaged '{app_id}' -> '{output_path}'")

if __name__ == "__main__":
    main()
