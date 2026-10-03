# EHttpEditor guide

EHttpEditor runs the HTTP requests written in `.http` files, with the
[syntax of the HTTP client of IntelliJ](https://www.jetbrains.com/help/idea/exploring-http-syntax.html): the same
files work in both. It is a plug-in of the Eclipse IDE, and a small standalone application.

- [The standalone application](#the-standalone-application)
- [Running a request](#running-a-request)
- [Writing requests](#writing-requests)
- [Variables](#variables)
- [Environments](#environments)
- [Bodies](#bodies)
- [Scripts](#scripts)
- [Tags](#tags)
- [Keys](#keys)
- [Preferences](#preferences)

## The standalone application

Download the archive of your platform from the
[latest build](https://github.com/RoiSoleil/ehttpeditor/releases/tag/latest), extract it and run `ehttpeditor`
(`ehttpeditor.exe` on Windows, `EHttpEditor.app` on macOS). It needs Java 21 or later.

The window has two parts: the editors of the `.http` files, and below them the *HTTP Client* view, with the
responses. At the first start, `requests.http` opens with a few commented examples: it is in the folder
`ehttpeditor-workspace` of your home folder, and opens again at each start.

- *File > New HTTP File...* creates a file with the same examples, where you want.
- *File > Open File...* (**Ctrl+O**) opens an `.http` file, or any text file (`http-client.env.json`...).
- A file given on the command line, `ehttpeditor path/to/requests.http`, opens in the window.
- *Help > Documentation* opens this guide.

## Running a request

Above each request, **▶ Send request** runs it; **▶▶ Run all requests**, above the first one, runs all the
requests of the file. With the keys: **Ctrl+Enter** runs the request of the cursor, **Ctrl+Shift+Enter** all of them.

The *HTTP Client* view shows the status, the time and the size of the response, and in its tabs:

- **Response**: the body (the JSON is indented and colored);
- **Headers**: the headers of the response;
- **Request**: the request as it was sent, with the variables replaced;
- **Tests**: the results of `client.test` in the scripts;
- **Console**: the output of `client.log` and `console.log`, and the variables which have no value.

The list on the left keeps the requests of the session: select one to see its response again, and run it again
with the button of the view.

## Writing requests

```http
### The name of the request (optional)
POST https://httpbin.org/post?debug=true
Content-Type: application/json
Accept: application/json

{ "name": "EHttpEditor" }
```

- `###` starts a request; the text after it is its name. `# @name Name` names it too.
- The request line: the method, the URL, and optionally the version (`HTTP/1.1`, `HTTP/2`). Without a method,
  the request is a GET: `https://httpbin.org/get` alone is a request.
- The URL can continue on the next lines, which start with `?` or `&` (indented or not); the parameters are encoded:

  ```http
  GET https://httpbin.org/get
      ?q=eclipse http
      &page=2
  ```

- The headers follow, one `Name: value` a line, then an empty line and the body.
- The lines starting with `#` or `//` are comments.

Typing **Ctrl+Space** completes the methods, the headers, the content types, the tags and the variables.

## Variables

`{{name}}` is replaced by the value of the variable when the request runs. Hover a variable to see its value and
where it comes from. A variable without a value stays as it is (encoded in the URL), and the *Console* tab lists it.

- **In the file**: `@host = https://httpbin.org`, for the requests below it. The value can use other variables.
- **Environments**: see below.
- **Scripts**: `client.global.set("token", value)` keeps a value for the next requests (until the end of the
  session), `request.variables.set("id", value)` in a pre-request script for the request only.
- **Objects**: `{{user.name}}`, `{{ids[0]}}` read the values of an environment which are objects or arrays.
- **Dynamic variables**, a new value each time:

  | Variable | Value |
  | --- | --- |
  | `{{$uuid}}` | a random UUID |
  | `{{$timestamp}}` | the current time, in seconds since 1970 |
  | `{{$isoTimestamp}}` | the current time, in ISO 8601 |
  | `{{$randomInt}}` | a random number between 0 and 1000 |
  | `{{$random.integer(1, 10)}}`, `{{$random.float(0, 1)}}` | a random number in the range |
  | `{{$random.alphabetic(8)}}`, `{{$random.alphanumeric(8)}}`, `{{$random.hexadecimal(8)}}` | random characters |
  | `{{$random.email}}`, `{{$random.name.fullName}}`... | random data |
  | `{{$env.HOME}}` | an environment variable of the system |
  | `{{$projectRoot}}`, `{{$historyFolder}}` | the folder of the project, the folder of the saved responses |

## Environments

The environments are in `http-client.env.json`, in the folder of the `.http` file or in a folder above it:

```json
{
  "$shared": { "host": "https://httpbin.org" },
  "dev":  { "host": "http://localhost:8080", "user": { "name": "dev" } },
  "prod": { "token": "..." }
}
```

`$shared` is in every environment. The secrets go in `http-client.private.env.json`, next to it, which is not
committed: its values are added to the ones of `http-client.env.json`.

The environment is chosen with **Environment: ...** above the first request, in the *HTTP Client* view, or with
**Ctrl+Alt+E**. `"SSLConfiguration": {"verifyHostCertificate": false}` in an environment accepts the certificates
which are not trusted (a local server).

## Bodies

- **Inline**: the lines after the empty line.
- **From a file**: `< ./input.json` sends the file as it is; `<@ ./template.json` replaces its variables first.
- **Forms**: with `Content-Type: application/x-www-form-urlencoded`, `key = value &` on several lines, encoded.
- **Multipart**: with `Content-Type: multipart/form-data; boundary=...`, the parts and `< ./file.txt` for the files.
- **Saved responses**: `>> out/response.json` after a request saves its response in a new file
  (`response-1.json`... when it exists), `>>! out/response.json` overwrites it.

## Scripts

The scripts are in JavaScript (ES6). A **response handler** runs after the response, a **pre-request script**
before the request:

```http
< {%
request.variables.set("id", "42");
%}
GET https://httpbin.org/get?id={{id}}

> {%
client.test("Status is 200", function () {
  client.assert(response.status === 200, "status " + response.status);
});
client.global.set("url", response.body.url);
%}
```

`> handler.js` and `< script.js` run a file instead.

| API | |
| --- | --- |
| `client.global.set(name, value)`, `client.global.get(name)` | the variables kept between the requests |
| `client.test(name, function)`, `client.assert(condition, message)` | the tests, in the *Tests* tab |
| `client.log(text)`, `console.log(text)` | the *Console* tab |
| `client.exit()` | stops the script |
| `response.status`, `response.body`, `response.contentType` | the response; `body` is an object for JSON |
| `response.headers.valueOf(name)`, `response.headers.valuesOf(name)` | the headers of the response |
| `response.cookies()` | the cookies of the response |
| `request.variables`, `request.environment`, `request.headers`, `request.url`, `request.body` | the request |
| `crypto` (MD5, SHA, HMAC), `jsonPath(object, "$.a[0].b")`, `btoa`, `atob`, `sleep`, `setTimeout` | helpers |

## Tags

Comments before a request change how it runs:

- `# @no-redirect`: does not follow the redirections;
- `# @no-log`: not in the history;
- `# @no-cookie-jar`: does not send or keep the cookies;
- `# @no-auto-encoding`: sends the URL as it is written;
- `# @timeout 30 s`, `# @connection-timeout 2 m`: the timeouts of the request.

`Authorization: Basic user password` is encoded for you, `Authorization: Digest user password` answers the
challenge of the server.

## Keys

| Key | Action |
| --- | --- |
| **Ctrl+Enter** | runs the request of the cursor |
| **Ctrl+Shift+Enter** | runs all the requests of the file |
| **Ctrl+Alt+E** | chooses the environment |
| **Ctrl+Space** | completion |
| **Ctrl+O** | opens a file (standalone application) |
| **Ctrl+S** | saves the file |

On macOS, **Cmd** instead of **Ctrl**.

## Preferences

*Window > Preferences > HTTP Client*: the timeouts, the User-Agent, the size of the history and the cookies kept
between the sessions.
