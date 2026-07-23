const { baseUrl } = require("./request");

let socket = null;
let opened = false;
let heartbeat = null;
let connectedToken = null;
const listeners = new Set();
const pending = [];
const watchedGroups = new Set();
const watchedFaces = new Set();

function connect() {
  const token = wx.getStorageSync("token") || "";
  if (socket && opened && connectedToken !== token) { try { socket.close({}); } catch (error) {} reset(); }
  if (socket && opened) return;
  const wsBase = baseUrl().replace(/^https:/, "wss:").replace(/^http:/, "ws:");
  socket = wx.connectSocket({ url: `${wsBase}/miniapp/ws?token=${encodeURIComponent(token)}` });
  socket.onOpen(() => {
    opened = true;
    connectedToken = token;
    while (pending.length) send(pending.shift());
    watchedGroups.forEach(groupId => send({ type: "watchGroup", groupId }));
    watchedFaces.forEach(code => send({ type: "watchFace", code }));
    heartbeat = setInterval(() => send({ type: "ping" }), 25000);
  });
  socket.onMessage(event => {
    try { const value = JSON.parse(event.data || "{}"); listeners.forEach(listener => listener(value)); } catch (error) {}
  });
  socket.onClose(() => { reset(); if (listeners.size) setTimeout(connect, 2000); });
  socket.onError(() => reset());
}

function reset() { opened = false; socket = null; connectedToken = null; if (heartbeat) clearInterval(heartbeat); heartbeat = null; }
function send(value) { if (!opened || !socket) { pending.push(value); connect(); return; } socket.send({ data: JSON.stringify(value) }); }
function watchGroup(groupId) { watchedGroups.add(groupId); send({ type: "watchGroup", groupId }); }
function watchFace(code) { watchedFaces.add(code); send({ type: "watchFace", code }); }
function on(listener) { listeners.add(listener); return () => listeners.delete(listener); }

module.exports = { connect, send, watchGroup, watchFace, on };
