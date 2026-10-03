# Eclipse Marketplace

The listing of EHttpEditor on the [Eclipse Marketplace](https://marketplace.eclipse.org/), made like the one of
[ERipGrep](https://marketplace.eclipse.org/content/eripgrep): it installs the feature of the update site published
by the build (branch `update-site`).

To create it: log in to the Marketplace with an Eclipse account, *Add Content* (Solution), and fill the fields
below.

| Field | Value |
| --- | --- |
| Name | EHttpEditor |
| Teaser | The HTTP client of IntelliJ, in Eclipse: write HTTP requests in `.http` files, run them from the editor and see the responses. |
| Organization | RoiSoleil |
| Website | https://github.com/RoiSoleil/ehttpeditor |
| Support URL | https://github.com/RoiSoleil/ehttpeditor/issues |
| License | EPL 2.0 |
| Status | Production/Stable |
| Categories | Editor, Tools, Web |
| Tags | http, rest, api, client, http client, intellij, request, editor, testing, json |
| Logo | [docs/logo.png](logo.png) (128 × 128) |
| Screenshot | [docs/screenshot.png](screenshot.png) |
| Update site URL | `https://github.com/RoiSoleil/ehttpeditor/raw/update-site/latest/` |
| Features | `org.eclipse.ehttpeditor.feature` (required) |
| Eclipse versions | The latest release (the update site is built against `releases/latest`), Java 21 |
| Platforms | Windows, Mac, Linux/GTK |

## Description

```text
Write HTTP requests in .http files, with the syntax of the HTTP client of IntelliJ, run them from the editor and see the responses: the same files work in both IDEs.

Above each request, "▶ Send request" runs it (or Ctrl+Enter in it, Ctrl+Shift+Enter for all the requests of the file). The response shows in the HTTP Client view: body (JSON indented and colored), headers, request sent, tests, console of the scripts, and the history of the session.

Features:
- Syntax of IntelliJ: requests separated by ###, headers, body, comments, names, coloring and completion of the methods, headers, content types, tags and variables.
- Variables {{name}}, with their value in a hover: environments in http-client.env.json and http-client.private.env.json, in-place variables (@host = localhost:8080), global variables of the scripts and dynamic variables ($uuid, $timestamp, $random.*...).
- Bodies inline, from a file, multipart and forms.
- Response handlers and pre-request scripts in JavaScript: client.test, client.assert, client.global, response.body, crypto, jsonPath...
- Responses saved to a file (>> out/response.json).
- Tags: @no-redirect, @no-log, @no-cookie-jar, @timeout...
- Authentication Basic, Digest and Bearer, cookies kept between the requests, gzip and deflate.
- Environment chosen above the first request, in the view or with Ctrl+Alt+E.
- File > New > Other > HTTP Client > HTTP Request File creates a file with examples.

EHttpEditor needs TM4E (TextMate support), in the Eclipse release update site and already in most packages of the Eclipse IDE.
```

## Once created

The Marketplace gives the listing a node id (the number of
`https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=<id>`). Add the install button to the README:

```markdown
[![Drag to your running Eclipse workspace to install EHttpEditor](https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.svg)](https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=<id> "Drag to your running Eclipse workspace to install EHttpEditor")
```
