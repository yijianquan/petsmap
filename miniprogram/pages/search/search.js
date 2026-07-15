const { request, asList } = require("../../utils/request");

Page({
  data: {
    city: "上海市",
    keyword: "",
    mode: "destination",
    destinationResults: [],
    placeResults: [],
    allPlaces: [],
    empty: false
  },

  async onLoad(options) {
    const city = decodeParam(options.city) || wx.getStorageSync("selectedCity") || "上海市";
    this.setData({ city });
    try {
      const places = await request({ url: "/miniapp/api/places" });
      this.setData({ allPlaces: asList(places) });
    } catch (error) {
      this.setData({ allPlaces: [] });
    }
  },

  onKeyword(event) {
    this.setData({ keyword: event.detail.value });
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.runSearch(), 260);
  },

  switchMode(event) {
    this.setData({ mode: event.currentTarget.dataset.mode });
    this.runSearch();
  },

  async runSearch() {
    const keyword = this.data.keyword.trim();
    if (!keyword) {
      this.setData({ destinationResults: [], placeResults: [], empty: false });
      return;
    }
    if (this.data.mode === "destination") {
      const results = await request({ url: `/miniapp/api/map/search?q=${encodeURIComponent(keyword)}&city=${encodeURIComponent(this.data.city)}` });
      this.setData({ destinationResults: results || [], empty: !results || results.length === 0 });
      return;
    }
    const lower = keyword.toLowerCase();
    const placeResults = this.data.allPlaces.filter((place) => {
      const text = `${place.name || ""}${place.address || ""}${place.description || ""}${(place.tags || []).join("")}`.toLowerCase();
      return text.includes(lower);
    });
    this.setData({ placeResults, empty: placeResults.length === 0 });
  },

  chooseDestination(event) {
    const destination = this.data.destinationResults[Number(event.currentTarget.dataset.index)];
    if (!destination || destination.latitude == null || destination.longitude == null) {
      wx.showToast({ title: "这个目的地缺少坐标", icon: "none" });
      return;
    }
    wx.setStorageSync("selectedDestination", {
      name: destination.name,
      address: destination.address,
      latitude: destination.latitude,
      longitude: destination.longitude
    });
    wx.setStorageSync("selectedCity", this.data.city);
    wx.switchTab({ url: "/pages/map/map" });
  },

  openPlace(event) {
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${event.currentTarget.dataset.id}` });
  },

  goBack() {
    wx.navigateBack();
  }
});

function decodeParam(value) {
  if (!value) return "";
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
}
