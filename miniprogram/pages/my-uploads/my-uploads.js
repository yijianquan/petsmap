const { request, asList } = require("../../utils/request");

Page({
  data: { places: [], loading: true },

  onShow() {
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    try {
      const places = await request({ url: "/miniapp/api/places/mine" });
      this.setData({ places: asList(places) });
    } finally {
      this.setData({ loading: false });
    }
  },

  addPlace() {
    wx.setStorageSync("openUploadForm", true);
    wx.switchTab({ url: "/pages/map/map" });
  },

  openPlace(event) {
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${event.currentTarget.dataset.id}` });
  },

  editPlace(event) {
    const place = this.data.places[Number(event.currentTarget.dataset.index)];
    if (!place) return;
    wx.setStorageSync("editUploadPlace", place);
    wx.switchTab({ url: "/pages/map/map" });
  }
});
