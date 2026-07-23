const { request, asList } = require("../../utils/request");

Page({
  data: { created: [], joined: [], loading: true },
  onShow() { this.load(); },
  async load() {
    this.setData({ loading: true });
    try {
      const result = await request({ url: "/miniapp/api/walk-groups/mine" });
      this.setData({ created: asList(result.created), joined: asList(result.joined) });
    } finally { this.setData({ loading: false }); }
  },
  openGroup(event) {
    const group = event.currentTarget.dataset.group;
    if (!group) return;
    wx.navigateTo({ url: `/pages/walk-chat/walk-chat?id=${group.id}&name=${encodeURIComponent(group.name || "")}` });
  }
});
