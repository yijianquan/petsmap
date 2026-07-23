const { request, asList, ensureLogin } = require("../../utils/request");

Page({
  data: { city: "上海市", cityCode: "", keyword: "", groups: [], loading: true, userLatitude: null, userLongitude: null },
  onShow() {
    this.setData({
      city: wx.getStorageSync("selectedCity") || "上海市",
      cityCode: ""
    });
    this.refreshLocation().finally(() => this.loadGroups());
  },
  openCity() { wx.navigateTo({ url: `/pages/city/city?city=${encodeURIComponent(this.data.city)}` }); },
  onSearch(event) { this.setData({ keyword: event.detail.value || "" }); },
  search() { this.loadGroups(); },
  openFaceJoin() { if (ensureLogin()) wx.navigateTo({ url: "/pages/face-join/face-join" }); },
  async loadGroups() {
    this.setData({ loading: true });
    try {
      const url = `/miniapp/api/walk-groups?cityCode=${encodeURIComponent(this.data.cityCode)}&cityName=${encodeURIComponent(this.data.city)}&q=${encodeURIComponent(this.data.keyword.trim())}`;
      const groups = asList(await request({ url })).map(group => ({ ...group, distanceText: distanceText(this.data.userLatitude, this.data.userLongitude, group.latitude, group.longitude) }));
      this.setData({ groups });
    } finally { this.setData({ loading: false }); }
  },
  handleGroup(event) {
    const index = Number(event.currentTarget.dataset.index);
    const group = this.data.groups[index];
    if (!group) return;
    wx.navigateTo({ url: `/pages/walk-chat/walk-chat?id=${group.id}&name=${encodeURIComponent(group.name)}&distance=${encodeURIComponent(group.distanceText || '')}` });
  },
  refreshLocation() {
    return new Promise(resolve => wx.getLocation({ type: "gcj02", success: res => { this.setData({ userLatitude: res.latitude, userLongitude: res.longitude }); resolve(); }, fail: resolve }));
  }
});

function distanceText(lat1, lng1, lat2, lng2) {
  if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return "";
  const rad = value => value * Math.PI / 180;
  const a = rad(lat1), b = rad(lat2), deltaLat = rad(lat2 - lat1), deltaLng = rad(lng2 - lng1);
  const value = Math.sin(deltaLat / 2) ** 2 + Math.cos(a) * Math.cos(b) * Math.sin(deltaLng / 2) ** 2;
  const meters = 6371000 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
  return meters < 1000 ? `${Math.round(meters)}m` : `${(meters / 1000).toFixed(1)}km`;
}
