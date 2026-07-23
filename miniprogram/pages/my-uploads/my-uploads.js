const { request, asList, ensureLogin } = require("../../utils/request");

const typeOrder = ["HOSPITAL", "PET_STORE", "PARK", "MALL", "HOTEL", "RESTAURANT"];

Page({
  data: {
    places: [],
    loading: true,
    saving: false,
    showForm: false,
    editingId: null,
    placeTypes: [],
    placeTags: [],
    formTagGroups: [],
    placeSearchKeyword: "",
    placeSuggestions: [],
    form: emptyForm()
  },

  onLoad() { this.loadOptions(); },
  onShow() { this.load(); },
  onUnload() { if (this.placeSearchTimer) clearTimeout(this.placeSearchTimer); },

  async load() {
    this.setData({ loading: true });
    try {
      this.setData({ places: asList(await request({ url: "/miniapp/api/places/mine" })) });
    } finally { this.setData({ loading: false }); }
  },

  async loadOptions() {
    const data = await request({ url: "/miniapp/api/options" });
    const placeTypes = [...(data.placeTypes || [])].sort((left, right) => {
      const a = typeOrder.indexOf(left.value);
      const b = typeOrder.indexOf(right.value);
      return (a < 0 ? 999 : a) - (b < 0 ? 999 : b);
    });
    this.setData({ placeTypes, placeTags: data.placeTags || [] });
    this.refreshTags();
  },

  addPlace() {
    if (!ensureLogin()) return;
    this.setData({
      showForm: true,
      editingId: null,
      placeSearchKeyword: "",
      placeSuggestions: [],
      form: emptyForm()
    });
    this.refreshTags();
  },

  openPlace(event) {
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${event.currentTarget.dataset.id}` });
  },

  editPlace(event) {
    if (!ensureLogin()) return;
    const place = this.data.places[Number(event.currentTarget.dataset.index)];
    if (!place) return;
    this.setData({
      showForm: true,
      editingId: Number(place.id),
      placeSearchKeyword: place.name || "",
      placeSuggestions: [],
      form: {
        name: place.name || "",
        type: place.type || "PARK",
        typeName: place.typeName || "散步",
        address: place.address || "",
        phone: place.phone || "",
        cityCode: place.cityCode || "",
        cityName: place.cityName || "",
        latitude: place.latitude,
        longitude: place.longitude,
        description: place.description || "",
        policyNote: place.policyNote || "",
        tags: place.tags || []
      }
    });
    this.refreshTags();
  },

  closeForm() {
    if (this.data.saving) return;
    if (this.placeSearchTimer) clearTimeout(this.placeSearchTimer);
    this.setData({ showForm: false, placeSuggestions: [] });
  },
  noop() {},

  onFormInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value || "" });
  },

  onPlaceSearchInput(event) {
    const keyword = event.detail.value || "";
    this.setData({ placeSearchKeyword: keyword });
    if (this.placeSearchTimer) clearTimeout(this.placeSearchTimer);
    if (keyword.trim().length < 2) {
      this.setData({ placeSuggestions: [] });
      return;
    }
    this.placeSearchTimer = setTimeout(async () => {
      try {
        const city = wx.getStorageSync("selectedCity") || "";
        const result = await request({ url: `/miniapp/api/map/search?q=${encodeURIComponent(keyword.trim())}&city=${encodeURIComponent(city)}` });
        this.setData({ placeSuggestions: asList(result).slice(0, 8) });
      } catch (error) { this.setData({ placeSuggestions: [] }); }
    }, 320);
  },

  async choosePlaceSuggestion(event) {
    const place = this.data.placeSuggestions[Number(event.currentTarget.dataset.index)];
    if (!place) return;
    this.setData({
      placeSearchKeyword: place.name || "",
      placeSuggestions: [],
      "form.name": place.name || "",
      "form.address": place.address || "",
      "form.phone": place.phone || "",
      "form.cityCode": place.cityCode || "",
      "form.cityName": place.cityName || "",
      "form.latitude": place.latitude,
      "form.longitude": place.longitude,
      "form.type": place.type || this.data.form.type,
      "form.typeName": place.typeName || this.data.form.typeName
    });
    if (!place.cityCode) await this.resolveCity(place.latitude, place.longitude);
  },

  async useCurrentPoint() {
    try {
      const point = await currentPoint();
      let place = { name: "当前位置", address: "当前位置", latitude: point.latitude, longitude: point.longitude };
      try {
        place = { ...place, ...(await request({ url: `/miniapp/api/map/reverse?latitude=${point.latitude}&longitude=${point.longitude}` })) };
      } catch (error) {}
      this.setData({
        placeSearchKeyword: place.name || place.address || "当前位置",
        placeSuggestions: [],
        "form.name": place.name || place.address || "当前位置",
        "form.address": place.address || "",
        "form.cityCode": place.cityCode || "",
        "form.cityName": place.cityName || "",
        "form.latitude": point.latitude,
        "form.longitude": point.longitude
      });
    } catch (error) {
      wx.showToast({ title: "需要定位权限", icon: "none" });
    }
  },

  onTypeChange(event) {
    const option = this.data.placeTypes[Number(event.detail.value)];
    if (option) this.setData({ "form.type": option.value, "form.typeName": option.label });
  },

  toggleTag(event) {
    const tag = event.currentTarget.dataset.tag;
    const tags = [...(this.data.form.tags || [])];
    const index = tags.indexOf(tag);
    if (index >= 0) tags.splice(index, 1); else tags.push(tag);
    this.setData({ "form.tags": tags });
    this.refreshTags();
  },

  refreshTags() {
    const selected = new Set(this.data.form.tags || []);
    this.setData({ formTagGroups: buildTagGroups(this.data.placeTags || [], selected) });
  },

  async resolveCity(latitude, longitude) {
    if (latitude == null || longitude == null) return;
    try {
      const reverse = await request({ url: `/miniapp/api/map/reverse?latitude=${latitude}&longitude=${longitude}` });
      this.setData({ "form.cityCode": reverse.cityCode || "", "form.cityName": reverse.cityName || "" });
    } catch (error) {
      this.setData({ "form.cityCode": "", "form.cityName": "" });
    }
  },

  async savePlace() {
    if (this.data.saving || !ensureLogin()) return;
    const form = this.data.form;
    if (!form.name.trim() || form.latitude == null || form.longitude == null) {
      wx.showToast({ title: "请选择地点或当前位置", icon: "none" });
      return;
    }
    this.setData({ saving: true });
    try {
      if (!form.cityCode) await this.resolveCity(form.latitude, form.longitude);
      const payload = this.data.form;
      if (!payload.cityCode) {
        wx.showToast({ title: "无法确定地点所在城市", icon: "none" });
        return;
      }
      await request({
        url: this.data.editingId ? `/miniapp/api/places/${this.data.editingId}` : "/miniapp/api/places",
        method: this.data.editingId ? "PUT" : "POST",
        data: payload
      });
      this.setData({ showForm: false });
      wx.showToast({ title: "已保存", icon: "success" });
      await this.load();
    } finally { this.setData({ saving: false }); }
  }
});

function emptyForm() {
  return { name: "", type: "PARK", typeName: "散步", address: "", phone: "", cityCode: "", cityName: "", latitude: null, longitude: null, description: "", policyNote: "", tags: [] };
}

function buildTagGroups(tags, selected) {
  const definitions = [
    { name: "宠物友好", names: ["大狗友好", "猫咪友好", "无小孩", "环境安静"] },
    { name: "设施服务", names: ["停车", "饮水", "室内可进", "可进店", "草坪大", "阴凉多", "夜间照明"] },
    { name: "规则费用", names: ["免费", "需牵引", "可预约"] }
  ];
  const used = new Set();
  const groups = definitions.map(group => ({
    name: group.name,
    tags: group.names.filter(name => tags.includes(name)).map(name => {
      used.add(name);
      return { name, active: selected.has(name) };
    })
  })).filter(group => group.tags.length);
  const other = tags.filter(name => !used.has(name)).map(name => ({ name, active: selected.has(name) }));
  if (other.length) groups.push({ name: "其他", tags: other });
  return groups;
}

function currentPoint() {
  return new Promise((resolve, reject) => wx.getLocation({ type: "gcj02", success: resolve, fail: reject }));
}
