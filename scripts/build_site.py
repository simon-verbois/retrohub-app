#!/usr/bin/env python3
"""Generate and build the Western European language editions of RetroHub's GitHub Pages site."""
import argparse
import html
import json
from html.parser import HTMLParser
from pathlib import Path
import re
import shutil
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
BASE_URL = 'https://simon-verbois.github.io/retrohub-app/'
LANGUAGES = {
    'en': ('en', 'English'), 'fr': ('fr', 'Français'),
    'de': ('de', 'Deutsch'), 'es': ('es', 'Español'),
    'it': ('it', 'Italiano'), 'nl': ('nl', 'Nederlands'), 'pt': ('pt-PT', 'Português'),
}
SCREENSHOTS = ('home', 'conflict', 'settings', 'library')
ASSETS = ('styles.css', 'site.js', 'favicon.svg', 'fonts/manrope-regular.ttf',
          'fonts/manrope-extrabold.ttf', 'fonts/Manrope-OFL.txt') + tuple(f'screenshots/{name}.webp' for name in SCREENSHOTS)
PLACEHOLDER = re.compile(r'{{([a-z0-9_]+)}}')


def page_path(language: str) -> str:
    return 'index.html' if language == 'en' else f'{language}/index.html'


def route(language: str) -> str:
    return '' if language == 'en' else f'{language}/'


def read_copy(path: Path) -> dict[str, str]:
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'Duplicate translation key: {path}: {key}')
            result[key] = value
        return result
    copy = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)
    if not isinstance(copy, dict) or not copy or any(
        not isinstance(value, str) or not value.strip() or '{{' in value
        for value in copy.values()
    ):
        raise ValueError(f'Invalid or empty translation: {path}')
    return copy


def render_pages(source: Path) -> dict[str, str]:
    template = (source / 'site-template.html').read_text(encoding='utf-8')
    reference = read_copy(source / 'locales/en.json')
    special = {'html_language', 'canonical', 'alternates', 'asset_prefix',
               'language_name', 'language_links'}
    if set(PLACEHOLDER.findall(template)) != set(reference) | special:
        raise ValueError('Template and English translation keys differ.')
    alternates = '\n  '.join(
        f'<link rel="alternate" hreflang="{tag}" href="{BASE_URL}{route(language)}">'
        for language, (tag, _) in LANGUAGES.items()
    ) + f'\n  <link rel="alternate" hreflang="x-default" href="{BASE_URL}">'
    pages = {}
    for language, (tag, native_name) in LANGUAGES.items():
        copy = read_copy(source / f'locales/{language}.json')
        if copy.keys() != reference.keys():
            raise ValueError(f'Translation keys differ: {language}')
        prefix = './' if language == 'en' else '../'
        links = []
        for other, (other_tag, name) in LANGUAGES.items():
            current = ' aria-current="page"' if other == language else ''
            links.append(f'<a href="{prefix}{route(other)}" lang="{other_tag}" '
                         f'hreflang="{other_tag}"{current}>{name}</a>')
        values = {key: html.escape(value, quote=True) for key, value in copy.items()}
        values.update(html_language=tag, canonical=BASE_URL + route(language),
                      alternates=alternates, asset_prefix=prefix,
                      language_name=native_name, language_links='\n'.join(links))
        pages[page_path(language)] = PLACEHOLDER.sub(lambda match: values[match[1]], template)
    return pages


class Links(HTMLParser):
    """Collect local references and detect duplicate IDs without dependencies."""

    def __init__(self):
        super().__init__()
        self.targets = []
        self.ids = set()

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            if attrs['id'] in self.ids:
                raise ValueError(f'Duplicate HTML ID: {attrs["id"]}')
            self.ids.add(attrs['id'])
        self.targets.extend(attrs[key] for key in ('src', 'href') if key in attrs)


def validate_site(destination: Path) -> None:
    destination = destination.resolve()
    documents = {}
    for language in LANGUAGES:
        path = destination / page_path(language)
        links = Links()
        links.feed(path.read_text(encoding='utf-8'))
        documents[path] = links
    for path, links in documents.items():
        for target in links.targets:
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc:
                continue
            if parsed.path.startswith('/'):
                raise ValueError(f'Root-relative URL breaks project hosting: {target}')
            resolved = (path.parent / unquote(parsed.path)).resolve()
            if not resolved.is_relative_to(destination):
                raise ValueError(f'Site reference escapes destination: {target}')
            if resolved.is_dir():
                resolved /= 'index.html'
            if not resolved.is_file():
                raise ValueError(f'Missing site asset: {path.name}: {target}')
            if parsed.fragment and (
                resolved not in documents or parsed.fragment not in documents[resolved].ids
            ):
                raise ValueError(f'Missing page section: {target}')
    for asset in ASSETS:
        if not (destination / asset).is_file():
            raise ValueError(f'Missing runtime asset: {asset}')


def generate(source: Path) -> None:
    for name, content in render_pages(source).items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    print(f'Generated {len(LANGUAGES)} static locale pages in docs/.')


def check_generated(source: Path) -> None:
    for name, content in render_pages(source).items():
        path = source / name
        if not path.is_file() or path.read_text(encoding='utf-8') != content:
            raise ValueError(f'Stale localized page: {name}; run scripts/build_site.py --generate.')


def build(destination: Path, source: Path | None = None) -> None:
    source = (source or ROOT / 'docs').resolve()
    destination = destination.resolve()
    if destination == source or destination in source.parents or destination.is_relative_to(source):
        raise ValueError('Destination must be outside the source directory.')
    if destination == ROOT or destination in ROOT.parents:
        raise ValueError('Destination cannot contain the source repository.')
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Destination must be empty; choose a fresh build directory.')
    check_generated(source)
    destination.mkdir(parents=True, exist_ok=True)
    for name in (*ASSETS, *(page_path(language) for language in LANGUAGES)):
        path = destination / name
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, path)
    (destination / '.nojekyll').touch()
    validate_site(destination)
    print(f'Built and checked {len(LANGUAGES)} languages / {len(list(destination.rglob("*")))} entries in {destination}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--generate', action='store_true', help='Update committed docs locale pages')
    mode.add_argument('--check', action='store_true', help='Check committed locale pages and runtime links')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/site')
    args = parser.parse_args()
    try:
        if args.generate:
            generate(ROOT / 'docs')
        elif args.check:
            check_generated(ROOT / 'docs')
            validate_site(ROOT / 'docs')
            print(f'{len(LANGUAGES)} static locale pages and local references are current.')
        else:
            build(args.output)
    except (OSError, ValueError) as error:
        parser.exit(1, f'Website: {error}\n')


if __name__ == '__main__':
    main()
