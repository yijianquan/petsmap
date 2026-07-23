const { request, asList, baseUrl, isLoggedIn } = require("../../utils/request");
const realtime = require("../../utils/realtime");
Page({
  data: { id: null, name: "", group: {}, members: [], requests: [], baseUrl: "", selectedMember: null, showInviteMenu: false },
  onLoad(options) { const id=Number(options.id);this.setData({ id, name: decode(options.name), baseUrl: baseUrl() }); wx.setNavigationBarTitle({ title: "群信息" });this.offRealtime=realtime.on(event=>{if(event.type==="join.request"||event.type==="group.joinRequestHandled"||event.type==="group.memberJoined")this.load();});realtime.connect();realtime.watchGroup(id);this.load(); },
  onUnload(){if(this.offRealtime)this.offRealtime();},
  async load() { const root = this.data.baseUrl; const [group, rawMembers] = await Promise.all([request({ url: `/miniapp/api/walk-groups/${this.data.id}` }), request({ url: `/miniapp/api/walk-groups/${this.data.id}/members` })]); const members = asList(rawMembers).map(item => ({ ...item, avatarSrc: item.avatarUrl && item.avatarUrl.startsWith("/miniapp/") ? `${root}${item.avatarUrl}` : item.avatarUrl, pets:(item.pets||[]).map(p=>({...p,avatarSrc:p.avatarUrl?`${root}${p.avatarUrl}`:""})) })); this.setData({ group, members }); if(group.manager)this.loadRequests(); },
  async loadRequests() { const root=this.data.baseUrl;const requests=asList(await request({url:`/miniapp/api/walk-groups/${this.data.id}/join-requests`})).map(item=>({...item,avatarSrc:item.avatarUrl&&item.avatarUrl.startsWith("/miniapp/")?`${root}${item.avatarUrl}`:item.avatarUrl}));this.setData({requests}); },
  showMember(event) { this.setData({ selectedMember: this.data.members[Number(event.currentTarget.dataset.index)] || null }); },
  closeMember() { this.setData({ selectedMember: null }); },
  toggleInviteMenu() { this.setData({ showInviteMenu: !this.data.showInviteMenu }); },
  closeInviteMenu() { if (this.data.showInviteMenu) this.setData({ showInviteMenu: false }); },
  openFaceInvite() { if (!isLoggedIn()) return; this.setData({ showInviteMenu: false }); wx.navigateTo({ url: `/pages/face-join/face-join?groupId=${this.data.id}&groupName=${encodeURIComponent(this.data.name)}` }); },
  async handleRequest(event) { const { id, action }=event.currentTarget.dataset;await request({url:`/miniapp/api/walk-groups/${this.data.id}/join-requests/${id}/${action}`,method:"POST"});wx.showToast({title:action==="approve"?"已同意":"已拒绝",icon:"success"});this.load(); },
  async toggleAdmin(event) { const index=Number(event.currentTarget.dataset.index),member=this.data.members[index];if(!member)return;const role=member.role==="ADMIN"?"MEMBER":"ADMIN";await request({url:`/miniapp/api/walk-groups/${this.data.id}/members/${member.id}/role`,method:"PUT",data:{role}});this.setData({[`members[${index}].role`]:role}); },
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
