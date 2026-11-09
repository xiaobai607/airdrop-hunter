from datetime import timezone

from app.services.rss_discovery import _configured_feeds, parse_feed


RSS = b"""<?xml version="1.0"?>
<rss version="2.0"><channel><title>Example</title>
<item><title>New Testnet Campaign</title><link>https://project.example/airdrop</link>
<description>Join the testnet.</description><pubDate>Mon, 09 Nov 2026 08:00:00 GMT</pubDate></item>
</channel></rss>"""

ATOM = b"""<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
<entry><title>Atom Project</title><link href="https://atom.example/start"/>
<summary>Atom summary</summary><updated>2026-11-09T08:00:00Z</updated></entry>
</feed>"""


def test_parse_rss_feed_keeps_source_item_as_candidate():
    rows = parse_feed(RSS, "https://feeds.example/rss.xml")
    assert len(rows) == 1
    assert rows[0]["title"] == "New Testnet Campaign"
    assert rows[0]["item_url"] == "https://project.example/airdrop"
    assert rows[0]["summary"] == "Join the testnet."
    assert rows[0]["published_at"].tzinfo is not None


def test_parse_atom_feed():
    rows = parse_feed(ATOM, "https://feeds.example/atom.xml")
    assert len(rows) == 1
    assert rows[0]["title"] == "Atom Project"
    assert rows[0]["item_url"] == "https://atom.example/start"


def test_parse_feed_rejects_non_https_or_missing_links():
    xml = b"<rss><channel><item><title>Bad</title><link>http://bad.example</link></item></channel></rss>"
    assert parse_feed(xml, "https://feeds.example/rss") == []


def test_feed_configuration_requires_exact_allowlisted_https_host(monkeypatch):
    monkeypatch.setenv("AIRDROP_RSS_FEEDS", "good=https://feeds.example/rss;bad=https://evil.example/rss;plain=http://feeds.example/rss")
    monkeypatch.setenv("AIRDROP_RSS_ALLOWED_HOSTS", "feeds.example")
    assert _configured_feeds() == [("good", "https://feeds.example/rss")]
