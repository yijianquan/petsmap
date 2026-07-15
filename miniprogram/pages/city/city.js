const { request, asList } = require("../../utils/request");

Page({
  data: {
    currentCity: "上海市",
    cityGroups: []
  },

  onLoad(options) {
    this.setData({ currentCity: decodeParam(options.city) || wx.getStorageSync("selectedCity") || "上海市" });
    this.loadCities();
  },

  async loadCities() {
    try {
      const cities = asList(await request({ url: "/miniapp/api/cities" }));
      this.setData({ cityGroups: groupCities(cities) });
    } catch (error) {
      this.setData({ cityGroups: [] });
    }
  },

  selectCity(event) {
    const city = event.currentTarget.dataset.city;
    const selected = this.data.cityGroups
      .flatMap((group) => group.cities)
      .find((item) => item.name === city);
    wx.setStorageSync("selectedCity", city);
    if (selected) {
      wx.setStorageSync("selectedCityCode", selected.cityCode || "");
      wx.setStorageSync("selectedCityLocation", {
        name: selected.name,
        latitude: selected.latitude,
        longitude: selected.longitude
      });
    }
    wx.removeStorageSync("selectedDestination");
    wx.navigateBack();
  }
});

function groupCities(cities) {
  const buckets = {};
  (cities || []).forEach((city) => {
    const letter = (city.letter || "#").slice(0, 1).toUpperCase();
    if (!buckets[letter]) {
      buckets[letter] = [];
    }
    buckets[letter].push({
      ...city,
      tip: city.provinceName || "已开通"
    });
  });
  return Object.keys(buckets)
    .sort((left, right) => left.localeCompare(right))
    .map((letter) => ({
      letter,
      cities: buckets[letter].sort((left, right) => (left.name || "").localeCompare(right.name || ""))
    }));
}

function decodeParam(value) {
  if (!value) return "";
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
}
