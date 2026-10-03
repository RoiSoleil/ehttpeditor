# <img src="docs/logo.png" width="40" align="top" alt=""> EHttpEditor : the HTTP client of IntelliJ, in Eclipse.

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/RoiSoleil/ehttpeditor/build.yml)](https://github.com/RoiSoleil/ehttpeditor/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/RoiSoleil/ehttpeditor/branch/main/graph/badge.svg)](https://codecov.io/gh/RoiSoleil/ehttpeditor)
[![GitHub](https://img.shields.io/github/license/RoiSoleil/ehttpeditor)](LICENSE)

Write HTTP requests in `.http` files, with the
[syntax of the HTTP client of IntelliJ](https://www.jetbrains.com/help/idea/exploring-http-syntax.html), run them
from the editor and see the responses: the same files work in both IDEs.

```http
@host = https://httpbin.org

### Login
POST {{host}}/post
Content-Type: application/json

{ "user": "{{user}}", "password": "{{password}}" }

> {%
client.test("Logged in", function () {
  client.assert(response.status === 200, "status " + response.status);
});
client.global.set("token", response.body.json.user);
%}

### Uses the token
GET {{host}}/bearer
Authorization: Bearer {{token}}
```

Above each request, **▶ Send request** runs it (or **Ctrl+Enter** in it, **Ctrl+Shift+Enter** for all the requests
of the file). The response shows in the *HTTP Client* view: body (JSON indented and colored), headers, request sent,
tests, console of the scripts, and the history of the session.

# Features

- **Syntax** of IntelliJ: requests separated by `###`, the method optional (GET), the URL on several lines,
  `HTTP/1.1` or `HTTP/2`, headers, body, comments `#` and `//`, names (`### Name`, `# @name Name`), coloring
  (TextMate grammar) and completion of the methods, headers, content types, tags and variables.
- **Variables** `{{name}}`, with their value in a hover:
  - environments in `http-client.env.json` and `http-client.private.env.json` (the secrets, not committed), in the
    folder of the file or above it, with `$shared`, objects (`{{user.name}}`, `{{ids[0]}}`) and
    `"SSLConfiguration": {"verifyHostCertificate": false}`;
  - in-place variables: `@host = localhost:8080`;
  - global variables of the scripts (`client.global.set`) and variables of the request (`request.variables.set`);
  - dynamic variables: `$uuid`, `$timestamp`, `$isoTimestamp`, `$randomInt`, `$random.integer(1, 10)`,
    `$random.float(0, 1)`, `$random.alphabetic(8)`, `$random.alphanumeric(8)`, `$random.hexadecimal(8)`,
    `$random.email`, `$random.name.fullName`..., `$env.HOME`, `$projectRoot`, `$historyFolder`.
- **Bodies**: inline, from a file (`< ./input.json`, `<@ ./template.json` replaces its variables too), multipart
  (`multipart/form-data` with files), forms (`key = value &` on several lines, encoded).
- **Response handlers** `> {% ... %}` or `> handler.js` and **pre-request scripts** `< {% ... %}`, in JavaScript (Rhino,
  ES6): `client.global`, `client.test`, `client.assert`, `client.log`, `client.exit`, `response.body` (an object for
  JSON), `response.headers.valueOf/valuesOf`, `response.status`, `response.contentType`, `response.cookies()`,
  `request.variables`, `request.environment`, `request.headers`, `request.url`, `request.body`, `crypto` (MD5, SHA,
  HMAC), `jsonPath(object, "$.a[0].b")`, `console.log`, `sleep`, `setTimeout`, `btoa`/`atob`.
- **Responses saved** to a file: `>> out/response.json` (a new file, `response-1.json`...), `>>! file` (overwritten).
- **Tags**: `# @no-redirect`, `# @no-log`, `# @no-cookie-jar`, `# @no-auto-encoding`, `# @timeout 30 s`,
  `# @connection-timeout 2 m`.
- **Authentication**: `Authorization: Basic user password` (encoded), `Digest user password` (the challenge is
  answered), `Bearer`.
- **Cookies** kept between the requests (and between the sessions, see the preferences), redirections followed with
  their cookies, gzip and deflate responses decompressed.
- **Environment**: chosen above the first request, in the view or with **Ctrl+Alt+E**.
- *File > New > Other > HTTP Client > HTTP Request File* creates a file with examples.

The timeouts, the User-Agent, the size of the history and the cookies kept are in
*Window > Preferences > HTTP Client*.

Not supported yet: GraphQL, WebSocket and gRPC requests, `run` of other requests, the iterations of a request over
an array, the OAuth 2.0 configuration of the environments, the client certificates, the XML body as a DOM in the
handlers (it is a string).

# Update Site

You can find the latest build of EHttpEditor here:

https://github.com/RoiSoleil/ehttpeditor/raw/update-site/latest/

EHttpEditor needs TM4E (TextMate support, in the Eclipse release update site, already in most packages of the Eclipse IDE).

# Build

Requires JDK 21 and Maven 3.9 (the Apache distribution: the Maven packaged by some Linux distributions does not work
with Tycho).

```bash
mvn clean install   # update site in update-site/org.eclipse.ehttpeditor/target/repository
```

The tests drive the parser, the variables, the HTTP client (against a local server) and the scripts, without a
workbench. JaCoCo measures their coverage; `tests/org.eclipse.ehttpeditor.coverage` writes the report in
`target/site/jacoco-aggregate` and GitHub Actions sends it to [Codecov](https://codecov.io/gh/RoiSoleil/ehttpeditor).

The icons are drawn by `tools/MakeIcon.java` (the view icon is the same drawing as `icons/ehttpeditor.svg`):

```bash
java tools/MakeIcon.java ehttpeditor 16 bundles/org.eclipse.ehttpeditor/icons/ehttpeditor.png
```

# License

[Eclipse Public License, v2.0](http://www.eclipse.org/legal/epl-v20.html)
