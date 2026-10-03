// The API of the scripts of EHttpEditor, compatible with the one of the HTTP client of IntelliJ:
// client, request, response, crypto, jsonPath, console, sleep, setTimeout.
// Built on __host (org.eclipse.ehttpeditor.core.ScriptHost). Runs on Rhino, in ES6 mode.
(function (global) {
	var host = global.__host;
	var EXIT = { __ehttpExit: true };

	function text(value) {
		if (value === undefined || value === null) {
			return value;
		}
		if (typeof value === 'object') {
			return JSON.stringify(value);
		}
		return String(value);
	}

	function format(args) {
		var parts = [];
		for (var i = 0; i < args.length; i++) {
			var value = args[i];
			if (typeof value === 'object' && value !== null && !(value instanceof Error)) {
				try {
					parts.push(JSON.stringify(value));
				} catch (e) {
					parts.push(String(value));
				}
			} else {
				parts.push(String(value));
			}
		}
		return parts.join(' ');
	}

	function message(error) {
		if (error && error.message !== undefined) {
			return String(error.message);
		}
		return String(error);
	}

	// client

	var currentTest = null;
	global.client = {
		global: {
			set: function (name, value) {
				host.globalSet(String(name), value === undefined || value === null ? null : text(value));
			},
			get: function (name) {
				var value = host.globalGet(String(name));
				return value === undefined ? null : value;
			},
			isEmpty: function () {
				return host.globalIsEmpty();
			},
			clear: function (name) {
				host.globalClear(String(name));
			},
			clearAll: function () {
				host.globalClearAll();
			},
			headers: {
				set: function (name, value) {
					host.globalHeaderSet(String(name), text(value));
				},
				clear: function (name) {
					host.globalHeaderClear(String(name));
				}
			}
		},
		log: function () {
			host.log(format(arguments));
		},
		test: function (name, func) {
			var previous = currentTest;
			currentTest = String(name);
			try {
				func();
				host.testPassed(String(name));
			} catch (e) {
				if (e === EXIT || host.cancelled()) {
					throw e;
				}
				host.testFailed(String(name), message(e));
			} finally {
				currentTest = previous;
			}
		},
		assert: function (condition, msg) {
			if (!condition) {
				var m = msg === undefined ? 'Assertion failed' : String(msg);
				if (currentTest !== null) {
					throw new Error(m);
				}
				host.testFailed(m, m);
			}
		},
		exit: function () {
			throw EXIT;
		}
	};

	global.console = {
		log: function () {
			host.log(format(arguments));
		},
		info: function () {
			host.log(format(arguments));
		},
		debug: function () {
			host.log(format(arguments));
		},
		warn: function () {
			host.log('WARN: ' + format(arguments));
		},
		error: function () {
			host.log('ERROR: ' + format(arguments));
		}
	};

	// request

	function requestHeaders() {
		return JSON.parse(host.requestHeadersJson()).map(function (header) {
			return {
				name: header.name,
				value: header.value,
				getRawValue: function () {
					return header.raw;
				},
				tryGetSubstitutedValue: function () {
					return header.value;
				}
			};
		});
	}

	global.request = {
		method: host.requestMethod(),
		variables: {
			set: function (name, value) {
				host.requestVariableSet(String(name), value === undefined || value === null ? '' : text(value));
			},
			get: function (name) {
				var value = host.requestVariableGet(String(name));
				return value === undefined ? null : value;
			}
		},
		environment: {
			get: function (name) {
				var value = host.environmentGet(String(name));
				return value === undefined ? null : value;
			}
		},
		headers: {
			all: function () {
				return requestHeaders();
			},
			findByName: function (name) {
				var lower = String(name).toLowerCase();
				var all = requestHeaders();
				for (var i = 0; i < all.length; i++) {
					if (all[i].name.toLowerCase() === lower) {
						return all[i];
					}
				}
				return null;
			}
		},
		body: {
			getRaw: function () {
				return host.requestBodyRaw();
			},
			tryGetSubstituted: function () {
				return host.requestBodySubstituted();
			}
		},
		url: {
			getRaw: function () {
				return host.requestUrlRaw();
			},
			tryGetSubstituted: function () {
				return host.requestUrlSubstituted();
			}
		},
		iteration: function () {
			return 0;
		},
		templateValue: function () {
			return null;
		}
	};

	// response

	if (global.__hasResponse) {
		var rawHeaders = JSON.parse(host.responseHeadersJson());
		var body = host.responseBody();
		if (host.responseIsJson()) {
			try {
				body = JSON.parse(body);
			} catch (e) {
				// Not valid JSON: the text.
			}
		}
		global.response = {
			status: host.responseStatus(),
			body: body,
			headers: {
				valueOf: function (name) {
					var lower = String(name).toLowerCase();
					for (var i = 0; i < rawHeaders.length; i++) {
						if (rawHeaders[i][0].toLowerCase() === lower) {
							return rawHeaders[i][1];
						}
					}
					return null;
				},
				valuesOf: function (name) {
					var lower = String(name).toLowerCase();
					var values = [];
					for (var i = 0; i < rawHeaders.length; i++) {
						if (rawHeaders[i][0].toLowerCase() === lower) {
							values.push(rawHeaders[i][1]);
						}
					}
					return values;
				}
			},
			contentType: {
				mimeType: host.responseMimeType(),
				charset: host.responseCharset()
			},
			cookies: function () {
				return JSON.parse(host.responseCookiesJson());
			},
			cookiesByName: function (name) {
				return JSON.parse(host.responseCookiesJson()).filter(function (cookie) {
					return cookie.name === String(name);
				});
			}
		};
	}

	// crypto

	function hasher(algorithm, keyHex) {
		var hex = '';
		var self = {
			updateWithText: function (value, encoding) {
				hex += host.textToHex(String(value), encoding ? String(encoding) : null);
				return self;
			},
			updateWithHex: function (value) {
				hex += String(value).toLowerCase();
				return self;
			},
			updateWithBase64: function (value, urlSafe) {
				hex += host.base64ToHex(String(value), !!urlSafe);
				return self;
			},
			digest: function () {
				var result = host.digest(algorithm, hex, keyHex);
				return {
					toHex: function () {
						return result;
					},
					toBase64: function (urlSafe) {
						return host.hexToBase64(result, !!urlSafe);
					}
				};
			}
		};
		return self;
	}

	function hmac(algorithm) {
		return function () {
			return {
				withTextSecret: function (secret, encoding) {
					return hasher(algorithm, host.textToHex(String(secret), encoding ? String(encoding) : null));
				},
				withHexSecret: function (secret) {
					return hasher(algorithm, String(secret).toLowerCase());
				},
				withBase64Secret: function (secret, urlSafe) {
					return hasher(algorithm, host.base64ToHex(String(secret), !!urlSafe));
				}
			};
		};
	}

	function hash(algorithm) {
		return function () {
			return hasher(algorithm, null);
		};
	}

	global.crypto = {
		md5: hash('MD5'),
		sha1: hash('SHA-1'),
		sha256: hash('SHA-256'),
		sha384: hash('SHA-384'),
		sha512: hash('SHA-512'),
		hmac: {
			md5: hmac('MD5'),
			sha1: hmac('SHA-1'),
			sha256: hmac('SHA-256'),
			sha384: hmac('SHA-384'),
			sha512: hmac('SHA-512')
		}
	};

	global.btoa = function (value) {
		return host.textToBase64(String(value), false);
	};
	global.atob = function (value) {
		return host.base64ToText(String(value), false);
	};

	// jsonPath(object, '$.users[0].name'), with [*] and .* for every element

	global.jsonPath = function (object, path) {
		var value = object;
		if (typeof value === 'string') {
			try {
				value = JSON.parse(value);
			} catch (e) {
				return null;
			}
		}
		var p = String(path).trim();
		if (p.charAt(0) === '$') {
			p = p.substring(1);
		} else if (p.charAt(0) !== '.' && p.charAt(0) !== '[') {
			p = '.' + p;
		}
		var pattern = /\.\*|\.([^.\[\]]+)|\[\s*(\d+|\*|'[^']*'|"[^"]*")\s*\]/g;
		var nodes = [value];
		var many = false;
		var match;
		while ((match = pattern.exec(p)) !== null) {
			var key = match[1] !== undefined ? match[1] : match[2];
			var wildcard = match[0] === '.*' || key === '*';
			if (wildcard) {
				many = true;
			} else if (/^['"]/.test(key)) {
				key = key.substring(1, key.length - 1);
			}
			var next = [];
			nodes.forEach(function (node) {
				if (node === null || typeof node !== 'object') {
					return;
				}
				if (wildcard) {
					Object.keys(node).forEach(function (k) {
						next.push(node[k]);
					});
				} else if (Object.prototype.hasOwnProperty.call(node, key)) {
					next.push(node[key]);
				}
			});
			nodes = next;
		}
		if (many) {
			return nodes;
		}
		return nodes.length > 0 && nodes[0] !== undefined ? nodes[0] : null;
	};

	// timers: sleep blocks; the callbacks of setTimeout run after the script, in their order

	var timers = [];
	var nextTimer = 1;
	global.sleep = function (millis) {
		host.sleep(Number(millis) || 0);
	};
	global.setTimeout = function (func, millis) {
		var args = Array.prototype.slice.call(arguments, 2);
		var id = nextTimer++;
		timers.push({ id: id, at: Date.now() + (Number(millis) || 0), func: func, args: args });
		return id;
	};
	global.clearTimeout = function (id) {
		timers = timers.filter(function (timer) {
			return timer.id !== id;
		});
	};

	// client.exit() throws EXIT, recognized by ScriptRunner. The errors are not caught here: rethrown, they would
	// lose the line of the script.
	global.__ehttpRun = function (script) {
		script();
		while (timers.length > 0) {
			timers.sort(function (a, b) {
				return a.at - b.at || a.id - b.id;
			});
			var timer = timers.shift();
			var wait = timer.at - Date.now();
			if (wait > 0) {
				host.sleep(wait);
			}
			timer.func.apply(null, timer.args);
		}
	};

	delete global.__host;
	delete global.__hasResponse;
})(this);
