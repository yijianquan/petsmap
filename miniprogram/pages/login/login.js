const { request } = require("../../utils/request");

Page({
  data: {
    loading: false
  },

  async wechatLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const loginResult = await wxLogin();
      const data = await request({
        url: "/miniapp/api/auth/wechat-dev",
        method: "POST",
        data: {
          code: loginResult.code,
          nickname: getFallbackNickname(),
          avatarUrl: ""
        }
      });
      wx.setStorageSync("token", data.token);
      wx.setStorageSync("user", data.user);
      getApp().globalData.token = data.token;
      wx.showToast({ title: "已登录", icon: "success" });
      const pendingInvitePath = wx.getStorageSync("pendingInvitePath");
      if (pendingInvitePath) {
        wx.removeStorageSync("pendingInvitePath");
        wx.reLaunch({ url: pendingInvitePath });
        return;
      }
      const redirectTab = wx.getStorageSync("loginRedirectTab") || "/pages/map/map";
      wx.removeStorageSync("loginRedirectTab");
      wx.switchTab({ url: redirectTab });
    } catch (error) {
      wx.showToast({ title: "登录失败，请稍后再试", icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  }
});

function getFallbackNickname() {
  const cached = wx.getStorageSync("fallbackNickname");
  if (cached) return cached;
  const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  let nickname = "";
  for (let i = 0; i < 10; i += 1) {
    nickname += letters[Math.floor(Math.random() * letters.length)];
  }
  wx.setStorageSync("fallbackNickname", nickname);
  return nickname;
}

function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({ success: resolve, fail: reject });
  });
}
