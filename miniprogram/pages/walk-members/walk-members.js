const { request, asList, baseUrl, isLoggedIn } = require("../../utils/request");
Page({
  data: { id: null, name: "", group: {}, members: [], baseUrl: "", selectedMember: null },
  onLoad(options) { this.setData({ id: Number(options.id), name: decode(options.name), baseUrl: baseUrl() }); wx.setNavigationBarTitle({ title: "群信息" }); this.load(); },
  async load() { const root = this.data.baseUrl; const [group, rawMembers] = await Promise.all([request({ url: `/miniapp/api/walk-groups/${this.data.id}` }), request({ url: `/miniapp/api/walk-groups/${this.data.id}/members` })]); const members = asList(rawMembers).map(item => ({ ...item, avatarSrc: item.avatarUrl && item.avatarUrl.startsWith("/miniapp/") ? `${root}${item.avatarUrl}` : item.avatarUrl, pets:(item.pets||[]).map(p=>({...p,avatarSrc:p.avatarUrl?`${root}${p.avatarUrl}`:""})) })); this.setData({ group, members }); },
  showMember(event) { this.setData({ selectedMember: this.data.members[Number(event.currentTarget.dataset.index)] || null }); },
  closeMember() { this.setData({ selectedMember: null }); },
  leave() { if (!isLoggedIn() || !this.data.group.joined || this.data.group.owner) return; wx.showModal({ title: "退出群聊", content: "退出后仍可浏览群聊，重新加入后才能发消息。", success: async res => { if (!res.confirm) return; await request({ url: `/miniapp/api/walk-groups/${this.data.id}/leave`, method: "DELETE" }); this.setData({ "group.joined": false }); wx.showToast({ title: "已退出群聊", icon: "success" }); } }); },
  onShareAppMessage() {
    return {
      title: `邀请你加入「${this.data.name || "一起遛"}」`,
      path: `/pages/walk-chat/walk-chat?id=${this.data.id}&name=${encodeURIComponent(this.data.name || "一起遛")}&invite=1`
    };
  },
  noop() {}
});
function decode(value) { try { return decodeURIComponent(value || ""); } catch (e) { return value || ""; } }
