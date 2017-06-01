(function() {
    var clientId = Math.floor(Math.random() * (10 - 1) + 1)
    console.log("WebSocket clientId: " + clientId);

    var token = undefined;
    var baseUrl = undefined;
    var linkObjs = undefined;

    function WebSocketTest()
    {
        if ("WebSocket" in window)
        {
            console.log("WebSocket is supported by your Browser!");

            var self = this;

            var open = function() {
                console.log("Connection is opened...");
                self.ws = new WebSocket("ws://" + location.hostname
                    + (location.port ? ':' + location.port : '')
                    + "/updates/" + clientId);

                /*
                self.ws.onopen = function()
                {
                    ws.send("Message to send");
                    console.log("Message is sent...");
                };
                */

                self.ws.onmessage = function (evt)
                {
                    var msg = JSON.parse(evt.data).value;
                    var br = document.createElement("br");
                    byId("messages").appendChild(br);
                    var textnode = document.createTextNode(msg);
                    byId("messages").appendChild(textnode);
                };

                self.ws.onclose = function()
                {
                    console.log("Connection is closed...");
                    open();
                };
            }

            open();
        }

        else
        {
            // The browser doesn't support WebSocket
            console.log("WebSocket NOT supported by your Browser!");
        }
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
                const now = new Date().getTime() / 1000;
                if (now <= payload.exp) {
                    req.setRequestHeader("Authorization",
                        "Bearer " + token);
                } else {
                    token = undefined;
                }
            }
            req.onload = function() {
                if (req.status === 200) {
                    var t = req.getResponseHeader("X-Token");
                    if (typeof t !== "undefined") {
                        token = t;
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
        if (typeof token !== "undefined") {
            const parts = token.split('.');
            if (parts.length === 3) {
                return JSON.parse(atob(parts[1]));
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
        if (!url && !linkObjs) {
            ajaxGet(window.location.href, "HEAD").then(
                function(data) {
                    url = baseUrl;
                    router();
                }
            );
            return;
        }

        if (el && url) {
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
        return location.origin + location.pathname + url;
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

    window.addEventListener('hashchange', router);
    window.addEventListener('load', router);
    <!--var webSocket = WebSocketTest();-->
})();
