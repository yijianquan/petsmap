const { request, asList, ensureLogin } = require("../../utils/request");

Page({
  data: { placeId: null, placeName: "", groups: [], showCreate: false, groupName: "" },
  onLoad(options) { this.setData({ placeId: Number(options.placeId), placeName: decode(options.placeName) }); },
  onShow() { this.load(); },
  async load() { this.setData({ groups: asList(await request({ url: `/miniapp/api/places/${this.data.placeId}/walk-groups` })) }); },
  handleGroup(event) {
    const index = Number(event.currentTarget.dataset.index);
    const group = this.data.groups[index];
    if (!group) return;
    this.openChat(group);
  },
  openCreate() { if (ensureLogin()) this.setData({ showCreate: true, groupName: "" }); },
  closeCreate() { this.setData({ showCreate: false }); },
  noop() {},
  onName(event) { this.setData({ groupName: event.detail.value || "" }); },
  async createGroup() {
    const name = this.data.groupName.trim();
    if (name.length < 2) { wx.showToast({ title: "群名至少 2 个字", icon: "none" }); return; }
    const group = await request({
      url: `/miniapp/api/places/${this.data.placeId}/walk-groups`, method: "POST",
      data: { name, cityCode: wx.getStorageSync("selectedCityCode") || "" }
    });
    this.setData({ showCreate: false, groups: [group].concat(this.data.groups || []) });
    this.openChat(group, true);
  },
  openChat(group, replaceCurrent) {
    const url = `/pages/walk-chat/walk-chat?id=${group.id}&name=${encodeURIComponent(group.name)}`;
    const navigate = replaceCurrent ? wx.redirectTo : wx.navigateTo;
    navigate({ url });
  }
});
function decode(value) { try { return decodeURIComponent(value || ""); } catch (e) { return value || ""; } }
