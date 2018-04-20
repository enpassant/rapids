(function() {
    var clientId = "clientId-" + Math.floor(Math.random() * (1000000 - 1) + 1)

    var token = window.sessionStorage.getItem("X-Token");
    var baseUrl = undefined;
    var linkObjs = undefined;
    var websocket = undefined;
    var timeoutFn = undefined;

    function WebSocketTest() {
        if ("WebSocket" in window) {
            console.log("WebSocket is supported by your Browser!");

            var self = this;

            var open = function() {
                console.log("Connection is opened...");
                var wsScheme =
                     window.location.href.startsWith("https") ? "wss" : "ws";
                self.ws = new WebSocket(wsScheme + "://" + location.hostname
                    + (location.port ? ':' + location.port : '')
                    + location.pathname + "updates/" + clientId);

                /*
                self.ws.onopen = function()
                {
                    ws.send("Message to send");
                    console.log("Message is sent...");
                };
                */

                self.ws.onmessage = function (evt) {
                    var msg = JSON.parse(evt.data).value;
                    var br = document.createElement("br");
                    byId("messages").appendChild(br);
                    var textnode = document.createTextNode(msg);
                    byId("messages").appendChild(textnode);
                    window.history.back();
                    console.log(window.history);
                };

                self.ws.onclose = function() {
                    console.log("Connection is closed...");
                    if (token) open();
                };
            }
            open();
        } else {
            // The browser doesn't support WebSocket
            console.log("WebSocket NOT supported by your Browser!");
        }
        return self.ws;
    }

    window.ajaxGet = function(url, method) {
        return new Promise(function(resolve, reject) {
            let req = new XMLHttpRequest();
            req.open(method || "GET", url);
            req.setRequestHeader('Cache-Control', 'no-cache');
            req.onload = function() {
                if (req.status === 200) {
                    const links = req.getResponseHeader("Link");
                    if (links) {
                        linkObjs = links.split(",").map(parseLink);
                        baseUrl = linkObjs[0].url;
                    } else {
                        linkObjs = [];
                    }
                    resolve(req.response);
                } else {
                    reject(new Error(req.statusText));
                }
            };

            req.onerror = function() {
                reject(new Error("Network error"));
            };

            req.send();
        });
    }

    window.ajaxPost = function(url, data) {
        return new Promise(function(resolve, reject) {
            let req = new XMLHttpRequest();
            req.open("POST", fullUrl(url));
            req.setRequestHeader("Content-type", "application/json");
            const payload = extractPayload(token);
            if (typeof payload !== "undefined") {
                req.setRequestHeader("Authorization", "Bearer " + token);
            }
            req.onload = function() {
                if (req.status === 200) {
                    var t = req.getResponseHeader("X-Token");
                    if (typeof t !== "undefined") {
                        token = t;
                        window.sessionStorage.setItem("X-Token", token);
                    }
                    resolve(req.response);
                } else {
                    reject(new Error(req.statusText));
                }
            };

            req.onerror = function() {
                reject(new Error("Network error"));
            };

            req.send(JSON.stringify(data));
        });
    }

    function extractPayload() {
        if (token) {
            const parts = token.split('.');
            if (parts.length === 3) {
                const payload = JSON.parse(
                    decodeURIComponent(escape(atob(parts[1]))));
                if (typeof payload !== "undefined") {
                    const now = new Date().getTime() / 1000;
                    if (now <= payload.exp) {
                        const timeout = Math.ceil(payload.exp - now);
                        if (timeoutFn) clearTimeout(timeoutFn);
                        timeoutFn = setTimeout(
                            function() { extractPayload(); },
                            Math.min(timeout * 1000, 10000));
                        byId("login-user").innerHTML =
                            payload.name + " (" + timeout + "s)";
                        clientId = payload.sub;
                        if (!websocket) websocket = WebSocketTest();
                        return payload;
                    }
                }
            }
            token = undefined;
            window.sessionStorage.removeItem("X-Token");
            if (websocket) {
                byId("login-user").innerHTML = "";
                websocket.ws.close();
                websocket = undefined;
            }
        }
        return undefined;
    }

    function addScripts(el) {
        var scripts = el.getElementsByTagName("script");
        var form = el.firstElementChild;
        for (i = 0; i < scripts.length; i++) {
            var evalFn = function() {
                return eval(scripts[i].innerHTML);
            };
            evalFn.call(form);
        }
    }

    var el = null;
    function router () {
        el = el || byId('view');
        var url = fullUrl(location.hash.slice(2)) || baseUrl;
        if (!url || !linkObjs || linkObjs.length === 0) {
            ajaxGet(window.location.href, "HEAD").then(
                function(data) {
                    url = baseUrl;
                    router();
                }
            );
            return;
        }

        if (location.hash === "") {
            var menu = linkObjs
                .filter(link => !link.rel)
                .map(link =>
                    '<a href="#/' + link.url + '">' + link.title + '</a>');
            el.innerHTML = "<ul><li>" + menu.join("</li><li>") + "</li>";
        } else if (el && url) {
            ajaxGet(url)
                .then(function(data) {
                    el.innerHTML =
                        data.replace(/(\s+href=")\//g, "$1#/");
                    addScripts(el);
                    var pos = url.indexOf("#");
                    if (pos >= 0) {
                        var goal = url.substring(pos + 1);
                        byId(goal).scrollIntoView();
                    }
                })
                .catch(function(e) {
                    el.innerHTML = e;
                });
        }
    }

    const fullUrl = function(url) {
        const path = url[0] === '/' ? url.slice(1) : url;
        return location.origin + location.pathname + path;
    };

    const dequote = function(str) {
        return str.replace(/"/g, '');
    };

    const parseLink = function(link) {
        const linkObj = {};
        const re = /<([^>]+)>(.*)/g;
        const result = re.exec(link.trim());
        if (result && result.length >= 3) {
            linkObj.url = result[1];
            const linkArr = result[2].split(";");
            const linkObjArr = linkArr.map(function(linkItem) {
                const linkItemArr = linkItem.trim().split("=");
                if (linkItemArr.length >= 2) {
                    const value = dequote(linkItemArr[1]);
                    linkObj[linkItemArr[0]] = value;
                }
            });
        }
        return linkObj;
    };

    window.byId = function(id) {
        return document.getElementById(id);
    }

    window.isHidden = function(el) {
        var style = window.getComputedStyle(el);
        return (style.display === 'none')
    }

    function getCookie(cname) {
        var name = cname + "=";
        var decodedCookie = decodeURIComponent(document.cookie);
        var ca = decodedCookie.split(';');
        for(var i = 0; i <ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') {
                c = c.substring(1);
            }
            if (c.indexOf(name) == 0) {
                return c.substring(name.length, c.length);
            }
        }
        return "";
    }

    const login = function() {
        window.location.assign("https://accounts.google.com/o/oauth2/v2/auth?client_id=33475451407-kv7l7blhf3jiha12c95doqvunb0uajop.apps.googleusercontent.com&redirect_uri=https://feca.mooo.com/app/auth/callback&response_type=code&access_type=online&scope=email openid profile&nonce=feca&state=feca|" + encodeURIComponent(window.location.href));
    };

    if (token) extractPayload();

    window.addEventListener('hashchange', router);
    window.addEventListener('load', router);
    byId('btnLogin').addEventListener('click', login);
    <!--var webSocket = WebSocketTest();-->
})();
