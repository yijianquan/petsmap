const { request, upload, ensureLogin, baseUrl } = require("../../utils/request");

Page({
  data: {
    id: null,
    baseUrl: "",
    place: {},
    comments: [],
    showComment: false,
    ratingOptions: [
      { value: 5, label: "5星" },
      { value: 4, label: "4星" },
      { value: 3, label: "3星" },
      { value: 2, label: "2星" },
      { value: 1, label: "1星" }
    ],
    commentForm: {
      rating: 5,
      content: "",
      imagePath: ""
    }
  },

  onLoad(options) {
    this.setData({ id: options.id, baseUrl: baseUrl() });
    this.load();
  },

  async load() {
    const place = await request({ url: `/miniapp/api/places/${this.data.id}` });
    const comments = await request({ url: `/miniapp/api/places/${this.data.id}/comments` });
    this.setData({ place, comments: comments || [] });
  },

  openComment() {
    if (!ensureLogin()) return;
    this.setData({ showComment: true, commentForm: { rating: 5, content: "", imagePath: "" } });
  },

  closeComment() {
    this.setData({ showComment: false });
  },

  noop() {},

  selectRating(event) {
    this.setData({ "commentForm.rating": Number(event.currentTarget.dataset.rating) });
  },

  onCommentContent(event) {
    this.setData({ "commentForm.content": event.detail.value });
  },

  chooseCommentImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ["image"],
      sizeType: ["compressed"],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (file) {
          this.setData({ "commentForm.imagePath": file.tempFilePath });
        }
      }
    });
  },

  async submitComment() {
    if (!ensureLogin()) return;
    const form = this.data.commentForm;
    if (!form.content.trim()) {
      wx.showToast({ title: "请填写评论内容", icon: "none" });
      return;
    }
    if (form.imagePath) {
      await upload({
        url: `/miniapp/api/places/${this.data.id}/comments`,
        filePath: form.imagePath,
        formData: {
          rating: String(form.rating),
          content: form.content
        }
      });
    } else {
      await request({
        url: `/miniapp/api/places/${this.data.id}/comments/json`,
        method: "POST",
        data: { rating: form.rating, content: form.content }
      });
    }
    this.setData({ showComment: false });
    this.load();
  }
});
