const { request, asList, baseUrl, isLoggedIn } = require("../../utils/request");

Page({
  data: { id: null, name: "", distanceText: "", invited: false, group: {}, messages: [], content: "", scrollIntoView: "", showManage: false, renameValue: "", selectedMember: null, baseUrl: "" },
  onLoad(options) {
    const invited = options.invite === "1";
    const invitePath = `/pages/walk-chat/walk-chat?id=${Number(options.id)}&name=${encodeURIComponent(decode(options.name) || "一起遛")}&invite=1`;
    if (!isLoggedIn()) {
      wx.setStorageSync("pendingInvitePath", invitePath);
      wx.reLaunch({ url: "/pages/login/login" });
      return;
    }
    this.setData({ id: Number(options.id), name: decode(options.name), distanceText: decode(options.distance), invited, baseUrl: baseUrl() });
    this.updateTitle();
  },
  async onShow() {
    if (!this.data.id || this.initializing) return;
    this.initializing = true;
    try {
      if (this.data.invited) await request({ url: `/miniapp/api/walk-groups/${this.data.id}/join`, method: "POST" });
      await Promise.all([this.loadGroup(), this.loadMessages(false)]);
      this.timer = setInterval(() => this.loadMessages(true), 3000);
    } finally { this.initializing = false; }
  },
  onHide() { this.stopTimer(); }, onUnload() { this.stopTimer(); }, stopTimer() { if (this.timer) clearInterval(this.timer); this.timer = null; },
  async loadGroup() {
    const group = await request({ url: `/miniapp/api/walk-groups/${this.data.id}` });
    this.setData({ group, name: group.name });
    if (!this.data.distanceText) this.resolveDistance(group); else this.updateTitle();
  },
  async loadMessages(incremental) {
    const messages = this.data.messages || [];
    const afterId = incremental && messages.length ? messages[messages.length - 1].id : 0;
    try {
      const incoming = asList(await request({ url: `/miniapp/api/walk-groups/${this.data.id}/messages?afterId=${afterId}` })).map(item => ({ ...item, avatarSrc: avatar(item.avatarUrl, this.data.baseUrl) }));
      if (!incoming.length && incremental) return;
      const merged = incremental ? messages.concat(incoming) : incoming;
      this.setData({ messages: merged, scrollIntoView: merged.length ? `message-${merged[merged.length - 1].id}` : "" });
    } catch (error) { this.stopTimer(); }
  },
  onContent(event) { this.setData({ content: event.detail.value || "" }); },
  async send() {
    const content = this.data.content.trim(); if (!content) return;
    const message = await request({ url: `/miniapp/api/walk-groups/${this.data.id}/messages`, method: "POST", data: { content } });
    const messages = (this.data.messages || []).concat(message); this.setData({ content: "", messages, scrollIntoView: `message-${message.id}` });
  },
  openMembers() { wx.navigateTo({ url: `/pages/walk-members/walk-members?id=${this.data.id}&name=${encodeURIComponent(this.data.name)}` }); },
  showSender(event) {
    const message = this.data.messages[Number(event.currentTarget.dataset.index)];
    if (!message) return;
    this.setData({ selectedMember: { id: message.senderId, nickname: message.senderName, avatarSrc: message.avatarSrc, owner: Number(message.senderId) === Number(this.data.group.ownerId) } });
  },
  closeMember() { this.setData({ selectedMember: null }); },
  openManage() { this.setData({ showManage: true, renameValue: this.data.group.name || this.data.name }); },
  closeManage() { this.setData({ showManage: false }); }, noop() {},
  onRename(event) { this.setData({ renameValue: event.detail.value || "" }); },
  async rename() {
    const group = await request({ url: `/miniapp/api/walk-groups/${this.data.id}`, method: "PUT", data: { name: this.data.renameValue.trim() } });
    this.setData({ name: group.name, group, showManage: false }); this.updateTitle();
  },
  updateTitle() { wx.setNavigationBarTitle({ title: [this.data.name || "群聊", this.data.distanceText].filter(Boolean).join(" · ") }); },
  resolveDistance(group) { wx.getLocation({ type: "gcj02", success: res => { const distanceText = formatDistance(res.latitude, res.longitude, group.latitude, group.longitude); this.setData({ distanceText }); this.updateTitle(); } }); },
  leave() { wx.showModal({ title: "退出群聊", content: "退出后需要重新加入才能查看消息。", success: async res => { if (!res.confirm) return; await request({ url: `/miniapp/api/walk-groups/${this.data.id}/leave`, method: "DELETE" }); wx.navigateBack({ delta: 1 }); } }); },
  dissolve() { wx.showModal({ title: "解散群聊", content: "群聊和全部历史消息将被删除，且无法恢复。", confirmColor: "#d64545", success: async res => { if (!res.confirm) return; await request({ url: `/miniapp/api/walk-groups/${this.data.id}`, method: "DELETE" }); wx.navigateBack({ delta: 2 }); } }); }
});
function decode(value) { try { return decodeURIComponent(value || ""); } catch (e) { return value || ""; } }
function avatar(value, root) { if (!value) return ""; return value.startsWith("/miniapp/") ? `${root}${value}` : value; }
function formatDistance(lat1,lng1,lat2,lng2){if([lat1,lng1,lat2,lng2].some(v=>v==null))return "";const r=v=>v*Math.PI/180,a=r(lat1),b=r(lat2),x=r(lat2-lat1),y=r(lng2-lng1),q=Math.sin(x/2)**2+Math.cos(a)*Math.cos(b)*Math.sin(y/2)**2,m=6371000*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));return m<1000?`${Math.round(m)}m`:`${(m/1000).toFixed(1)}km`;}
