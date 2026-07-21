const { request, asList, baseUrl } = require("../../utils/request");
Page({
  data: { id: null, name: "", members: [], baseUrl: "", selectedMember: null },
  onLoad(options) { this.setData({ id: Number(options.id), name: decode(options.name), baseUrl: baseUrl() }); this.load(); },
  async load() { const root = this.data.baseUrl; const members = asList(await request({ url: `/miniapp/api/walk-groups/${this.data.id}/members` })).map(item => ({ ...item, avatarSrc: item.avatarUrl && item.avatarUrl.startsWith("/miniapp/") ? `${root}${item.avatarUrl}` : item.avatarUrl })); this.setData({ members }); },
  showMember(event) { this.setData({ selectedMember: this.data.members[Number(event.currentTarget.dataset.index)] || null }); },
  closeMember() { this.setData({ selectedMember: null }); },
  onShareAppMessage() {
    return {
      title: `邀请你加入「${this.data.name || "一起遛"}」`,
      path: `/pages/walk-chat/walk-chat?id=${this.data.id}&name=${encodeURIComponent(this.data.name || "一起遛")}&invite=1`
    };
  },
  noop() {}
});
function decode(value) { try { return decodeURIComponent(value || ""); } catch (e) { return value || ""; } }
