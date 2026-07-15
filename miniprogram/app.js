App({
  globalData: {
    baseUrl: "http://localhost:8080"
  },

  onLaunch() {
    const token = wx.getStorageSync("token");
    this.globalData.token = token || "";
  }
});
