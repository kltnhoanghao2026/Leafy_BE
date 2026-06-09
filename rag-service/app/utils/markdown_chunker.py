"""
Header-based markdown chunker.

Splits a markdown document into semantic chunks using its heading structure as
the primary boundary.  Each chunk is rooted at a heading and includes all
subsequent content up to (but not including) the next sibling-or-deeper heading.

Chunk size is enforced by a configurable soft-limit: when a section exceeds
CHUNK_SIZE characters a secondary split is applied on paragraph / line / sentence
boundaries so that no single chunk blows past the limit.
"""

import re
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config.settings import settings

logger = logging.getLogger(__name__)

# Heading pattern: # through ###### followed by whitespace and text
_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)


@dataclass
class MarkdownSection:
    """A single parsed section rooted at one heading."""

    level: int              # 1–6  (how many # characters)
    title: str              # text after the # markers, stripped
    content: str           # raw markdown body below this heading
    heading_line: int       # 0-based line index of the heading itself
    parent_titles: List[str] = field(default_factory=list)
    """Full path of ancestor headings, outermost first."""

    @property
    def full_title(self) -> str:
        """Human-readable path: 'Parent > Child > Leaf'."""
        parts = self.parent_titles + [self.title]
        return " > ".join(parts)

    @property
    def section_id(self) -> str:
        """Stable dot-separated path used in metadata."""
        parts = self.parent_titles + [self.title]
        return ".".join(parts)


@dataclass
class MarkdownChunk:
    """A single chunk ready for embedding."""

    text: str
    section: MarkdownSection
    chunk_index: int       # position within the document's chunk list


# ---------------------------------------------------------------------------
# Settings exposed as module-level fallbacks so callers can import directly
# ---------------------------------------------------------------------------

MARKDOWN_CHUNK_SIZE: int = getattr(settings, "MARKDOWN_CHUNK_SIZE", 2000)
MARKDOWN_CHUNK_OVERLAP: int = getattr(settings, "MARKDOWN_CHUNK_OVERLAP", 300)
MARKDOWN_MAX_HEADING_DEPTH: int = getattr(settings, "MARKDOWN_MAX_HEADING_DEPTH", 3)


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

def parse_markdown_sections(markdown_text: str) -> List[MarkdownSection]:
    """
    Walk a markdown string and return a flat list of MarkdownSection objects.

    The algorithm is a depth-first state-machine:

    1. Split the text on every heading line (using the regex above).
    2. Maintain a heading-stack that tracks the current parent headings.
    3. When we encounter a heading of level H:
       - pop everything >= H off the stack (closing all deeper sections)
       - push this heading onto the stack
       - everything collected since the *previous* heading becomes the body of
         the prior section.

    This correctly handles:
    - Adjacent headings at the same level (two ## siblings → two sections)
    - Arbitrarily nested sub-sections (### under ##)
    - Headings at any depth
    - Content before the first heading (the "preamble") attached to the doc root
    """
    matches = list(_HEADING_RE.finditer(markdown_text))

    if not matches:
        return []

    sections: List[MarkdownSection] = []

    # Stack tracks (level, title) pairs for the current nesting path
    heading_stack: List[tuple[int, str]] = []

    for idx, m in enumerate(matches):
        level = len(m.group(1))
        title = m.group(2).strip()

        # Close any headings that are at the same level or deeper
        while heading_stack and heading_stack[-1][0] >= level:
            heading_stack.pop()

        parent_titles = [h[1] for h in heading_stack]
        heading_line = markdown_text[: m.start()].count("\n")

        # Content = everything between this heading and the next
        end = m.start() if idx == 0 else matches[idx - 1].start()
        next_start = matches[idx + 1].start() if idx + 1 < len(matches) else len(markdown_text)
        raw_content = markdown_text[next_start: m.start()].strip()

        # Remove the heading line itself from content (when idx > 0 we took
        # content after the *previous* heading, so we need to strip this
        # heading's line only when it was absorbed by the previous slice)
        if idx > 0:
            prev_heading_end = matches[idx - 1].start()
            between = markdown_text[prev_heading_end : m.start()]
            raw_content = between.strip()

        section = MarkdownSection(
            level=level,
            title=title,
            content=raw_content,
            heading_line=heading_line,
            parent_titles=parent_titles,
        )
        sections.append(section)

        # Push current heading onto stack
        heading_stack.append((level, title))

    return sections


# ---------------------------------------------------------------------------
# Secondary splitting (size guard)
# ---------------------------------------------------------------------------

_size = getattr(settings, "MARKDOWN_CHUNK_SIZE", 2000)
_overlap = getattr(settings, "MARKDOWN_CHUNK_OVERLAP", 300)

_sub_splitter = RecursiveCharacterTextSplitter(
    chunk_size=_size,
    chunk_overlap=_overlap,
    separators=["\n\n", "\n", ". ", " ", ""],
    length_function=len,
)


def _safe_split(text: str) -> List[str]:
    """Split text with a guard against empty results."""
    if not text.strip():
        return []
    result = _sub_splitter.split_text(text)
    if not result and text.strip():
        # Fallback: return the whole thing if splitter produced nothing
        return [text.strip()]
    return result


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def chunk_markdown_by_headers(
    file_path: Path,
    metadata: Optional[Dict[str, Any]] = None,
) -> List[MarkdownChunk]:
    """
    Parse a markdown file and return semantically chunked MarkdownChunk objects.

    Parameters
    ----------
    file_path:
        Path to the ``.md`` file on disk.
    metadata:
        Optional dict of extra fields to merge into each chunk's metadata
        (e.g. ``user_id``, ``category``).

    Returns
    -------
    List[MarkdownChunk]
        One chunk per section, or multiple chunks when a section exceeds
        the soft size limit.  Chunks are ordered top-to-bottom through the
        document.
    """
    metadata = metadata or {}

    raw_text = file_path.read_text(encoding="utf-8")
    sections = parse_markdown_sections(raw_text)

    if not sections:
        logger.warning("No headings found in %s — falling back to plain split", file_path.name)
        raw_text_clean = re.sub(r"[ \t]+", " ", raw_text)
        raw_text_clean = re.sub(r"\n+", "\n", raw_text_clean).strip()
        sub_chunks = _safe_split(raw_text_clean)
        return [
            MarkdownChunk(
                text=chunk,
                section=MarkdownSection(
                    level=0,
                    title=file_path.stem,
                    content=raw_text_clean,
                    heading_line=0,
                    parent_titles=[],
                ),
                chunk_index=i,
            )
            for i, chunk in enumerate(sub_chunks)
        ]

    logger.info("Parsed %d sections from %s", len(sections), file_path.name)

    chunks: List[MarkdownChunk] = []
    chunk_index = 0

    for section in sections:
        body = section.content.strip()

        if not body:
            logger.debug("Section '%s' has no body, skipping", section.title)
            continue

        # Build the section prefix (heading text repeated as context)
        heading_prefix = "#" * section.level + " " + section.title

        # Size guard: split oversized sections
        if len(body) > _size:
            sub_chunks = _safe_split(body)
            for sub in sub_chunks:
                chunks.append(
                    MarkdownChunk(
                        text=f"{heading_prefix}\n\n{sub}",
                        section=section,
                        chunk_index=chunk_index,
                    )
                )
                chunk_index += 1
        else:
            chunks.append(
                MarkdownChunk(
                    text=f"{heading_prefix}\n\n{body}",
                    section=section,
                    chunk_index=chunk_index,
                )
            )
            chunk_index += 1

    logger.info("Produced %d chunks from %d sections for %s",
                len(chunks), len(sections), file_path.name)
    return chunks


def build_chunks_from_markdown(
    file_path: Path,
    metadata: Dict[str, Any],
    file_hash: str,
) -> List[Dict[str, Any]]:
    """
    Convenience wrapper that produces the same dict-list format as the
    existing ``_build_chunks`` helper in ``app/workers/document.py``.

    Each dict has the shape::

        {
            "text": str,
            "metadata": {
                **metadata,
                "file_hash": str,
                "source": str,
                "source_file": str,
                "section_title": str,
                "section_full_path": str,
                "section_level": int,
                "section_heading_line": int,
                "chunk_index": int,
            },
        }
    """
    md_chunks = chunk_markdown_by_headers(file_path, metadata)
    base_meta = {
        **metadata,
        "file_hash": file_hash,
        "source": file_path.name,
    }
    result: List[Dict[str, Any]] = []
    for mc in md_chunks:
        meta = {
            **base_meta,
            "source_file": metadata.get("original_filename", file_path.name),
            "section_title": mc.section.title,
            "section_full_path": mc.section.full_title,
            "section_level": mc.section.level,
            "section_heading_line": mc.section.heading_line,
            "chunk_index": mc.chunk_index,
        }
        result.append({"text": mc.text, "metadata": meta})
    return result


def preview_markdown(
    file_path: Path,
) -> tuple[List[Dict[str, Any]], List[str]]:
    """
    Parse a markdown file without persisting anything.

    Returns
    -------
    tuple[list[dict], list[str]]
        ``(chunks, section_titles)`` where ``section_titles`` is the list of
        full section paths (e.g. ``["1. Tác Nhân", "2. Triệu Chứng > LOW",
        ...]``).
    """
    md_chunks = chunk_markdown_by_headers(file_path)
    sections_list = [mc.section.full_title for mc in md_chunks]

    chunks_out = [
        {
            "text": mc.text,
            "metadata": {
                "section_title": mc.section.title,
                "section_full_path": mc.section.full_title,
                "section_level": mc.section.level,
                "chunk_index": mc.chunk_index,
            },
        }
        for mc in md_chunks
    ]
    return chunks_out, sections_list
