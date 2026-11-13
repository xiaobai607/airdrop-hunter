package com.airdrophunter.app

/**
 * Wraps the original Binance CMS article HTML in a constrained document for WebView.
 *
 * Keep the source markup intact: the app must not invent or rewrite announcement terms.
 * Script execution and active network capabilities remain restricted by the CSP.
 */
internal fun announcementHtmlDocument(content: String): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">\n<meta name="color-scheme" content="light dark">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline' https:; font-src https: data:; script-src 'none'; connect-src 'none'; frame-src 'none';">
<style>
body { color: #20242b; background: #ffffff; font-family: sans-serif; font-size: 15px; line-height: 1.65; overflow-wrap: anywhere; }
img, video { max-width: 100%; height: auto; }
table { max-width: 100%; border-collapse: collapse; display: block; overflow-x: auto; }
a { color: #1677ff; }
@media (prefers-color-scheme: dark) {
  body { color: #f2f2f7; background: #121214; }
  a { color: #65b8ff; }
  blockquote { border-left-color: #3a3a3c; color: #c7c7cc; }
  th, td { border-color: #48484a; }
}
</style>
</head>
<body>$content</body>
</html>
""".trimIndent()
