function resolveBaseUrl() {
  const configured = wx.getStorageSync("apiBaseUrl");
  if (configured) return configured;
  try {
    const envVersion = wx.getAccountInfoSync().miniProgram.envVersion;
    if (envVersion === "develop") return "http://127.0.0.1:8080";
  } catch (error) {
    // Use the online API when environment information is unavailable.
  }
  return "https://www.trsttax.cn";
}

App({
  globalData: {
    baseUrl: resolveBaseUrl()
  },

  onLaunch() {
    const token = wx.getStorageSync("token");
    this.globalData.token = token || "";
  }
});
