const { request, upload, isLoggedIn, baseUrl, asList } = require("../../utils/request");

Page({
  data: {
    user: {},
    userAvatarSrc: "",
    loggedIn: false,
    baseUrl: "",
    uploadedPlaces: [],
    showProfileEditor: false,
    profileAvatarSrc: "",
    profileForm: {
      nickname: "",
      avatarUrl: ""
    }
  },

  onShow() {
    const loggedIn = isLoggedIn();
    const user = wx.getStorageSync("user") || {};
    this.setData({
      user,
      userAvatarSrc: avatarSrc(user.avatarUrl),
      loggedIn,
      baseUrl: baseUrl()
    });
    if (loggedIn) {
      this.loadUploadedPlaces();
    } else {
      this.setData({ uploadedPlaces: [] });
    }
  },

  async loadUploadedPlaces() {
    try {
      const places = await request({ url: "/miniapp/api/places/mine" });
      this.setData({ uploadedPlaces: asList(places) });
    } catch (error) {
      this.setData({ uploadedPlaces: [] });
      if (!isLoggedIn()) {
        this.setData({ user: {}, userAvatarSrc: "", loggedIn: false });
      }
    }
  },

  openFeature(event) {
    const page = event.currentTarget.dataset.page;
    wx.navigateTo({ url: `/pages/${page}/${page}` });
  },

  goLogin() {
    wx.setStorageSync("loginRedirectTab", "/pages/mine/mine");
    wx.reLaunch({ url: "/pages/login/login" });
  },

  handleProfileTap() {
    if (!this.data.loggedIn) {
      this.goLogin();
      return;
    }
    const user = this.data.user || {};
    this.setData({
      showProfileEditor: true,
      profileForm: {
        nickname: user.nickname || user.username || "",
        avatarUrl: user.avatarUrl || ""
      },
      profileAvatarSrc: avatarSrc(user.avatarUrl)
    });
  },

  closeProfileEditor() {
    this.setData({ showProfileEditor: false });
  },

  noop() {},

  onChooseAvatar(event) {
    const avatarUrl = event.detail.avatarUrl || "";
    this.setData({ "profileForm.avatarUrl": avatarUrl, profileAvatarSrc: avatarUrl });
  },

  onNicknameInput(event) {
    this.setData({ "profileForm.nickname": event.detail.value || "" });
  },

  async saveProfile() {
    const form = this.data.profileForm;
    if (!form.nickname.trim()) {
      wx.showToast({ title: "请填写昵称", icon: "none" });
      return;
    }
    try {
      const isLocalAvatar = form.avatarUrl && !form.avatarUrl.startsWith("http") && !form.avatarUrl.startsWith("/miniapp/");
      const data = isLocalAvatar
        ? await upload({
            url: "/miniapp/api/profile/avatar",
            filePath: form.avatarUrl,
            formData: { nickname: form.nickname.trim() }
          })
        : await request({
            url: "/miniapp/api/profile",
            method: "PUT",
            data: {
              nickname: form.nickname.trim(),
              avatarUrl: form.avatarUrl || ""
            }
          });
      wx.setStorageSync("user", data.user);
      this.setData({
        user: data.user,
        userAvatarSrc: avatarSrc(data.user.avatarUrl),
        profileAvatarSrc: avatarSrc(data.user.avatarUrl),
        showProfileEditor: false
      });
      wx.showToast({ title: "已保存", icon: "success" });
    } catch (error) {
      if (!isLoggedIn()) {
        this.setData({ user: {}, userAvatarSrc: "", loggedIn: false, showProfileEditor: false });
      }
    }
  },

  openPlace(event) {
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${event.currentTarget.dataset.id}` });
  },

  logout() {
    wx.showModal({
      title: "退出登录",
      content: "确定退出当前账号吗？",
      confirmText: "退出",
      success: (res) => {
        if (!res.confirm) return;
        wx.removeStorageSync("token");
        wx.removeStorageSync("user");
        getApp().globalData.token = "";
        this.setData({ user: {}, userAvatarSrc: "", loggedIn: false, uploadedPlaces: [] });
      }
    });
  }
});

function avatarSrc(value) {
  if (!value) return "";
  if (value.startsWith("/miniapp/")) {
    return `${baseUrl()}${value}`;
  }
  return value;
}
