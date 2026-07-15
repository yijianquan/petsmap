const app = getApp();

function baseUrl() {
  return app.globalData.baseUrl || "http://localhost:8080";
}

function token() {
  return wx.getStorageSync("token") || "";
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl()}${options.url}`,
      method: options.method || "GET",
      data: options.data || {},
      header: {
        "content-type": "application/json",
        "X-Miniapp-Token": token(),
        ...(options.header || {})
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
          return;
        }
        const message = res.data && res.data.message ? res.data.message : "操作没有完成";
        if (message.includes("请先登录")) {
          clearLogin();
        }
        wx.showToast({ title: message, icon: "none" });
        reject(new Error(message));
      },
      fail(error) {
        wx.showToast({ title: "网络不可用，请检查后端服务", icon: "none" });
        reject(error);
      }
    });
  });
}

function upload(options) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${baseUrl()}${options.url}`,
      filePath: options.filePath,
      name: options.name || "file",
      formData: options.formData || {},
      header: {
        "X-Miniapp-Token": token()
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(JSON.parse(res.data || "{}"));
          return;
        }
        wx.showToast({ title: "上传失败", icon: "none" });
        reject(new Error("上传失败"));
      },
      fail(error) {
        wx.showToast({ title: "上传失败", icon: "none" });
        reject(error);
      }
    });
  });
}

function ensureLogin() {
  if (!token()) {
    wx.showModal({
      title: "需要微信登录",
      content: "登录后可以上传地点、评论和管理自己的出行内容。",
      confirmText: "去登录",
      success(res) {
        if (res.confirm) {
          wx.switchTab({ url: "/pages/mine/mine" });
        } else {
          wx.switchTab({ url: "/pages/map/map" });
        }
      }
    });
    return false;
  }
  return true;
}

function isLoggedIn() {
  return Boolean(token());
}

function clearLogin() {
  wx.removeStorageSync("token");
  wx.removeStorageSync("user");
  app.globalData.token = "";
}

function asList(value, fallbackMessage) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.data)) return value.data;
  if (value && Array.isArray(value.list)) return value.list;
  if (fallbackMessage) {
    wx.showToast({ title: fallbackMessage, icon: "none" });
  }
  return [];
}

module.exports = {
  baseUrl,
  request,
  upload,
  ensureLogin,
  isLoggedIn,
  clearLogin,
  asList
};
