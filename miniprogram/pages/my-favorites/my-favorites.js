const { request, asList } = require("../../utils/request");

Page({
  data: { places: [], loading: true },

  onShow() {
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    try {
      const places = await request({ url: "/miniapp/api/favorites" });
      this.setData({ places: asList(places) });
    } finally {
      this.setData({ loading: false });
    }
  },

  openPlace(event) {
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${event.currentTarget.dataset.id}` });
  }
});
