const { request, asList } = require("../../utils/request");

Page({
  data: {
    currentCity: "上海市",
    cityGroups: [],
    allCities: [],
    cityKeyword: ""
  },

  onLoad(options) {
    this.setData({ currentCity: decodeParam(options.city) || wx.getStorageSync("selectedCity") || "上海市" });
    this.loadCities();
  },

  async loadCities() {
    try {
      const cities = asList(await request({ url: "/miniapp/api/cities" }));
      this.setData({ allCities: cities, cityGroups: groupCities(cities) });
    } catch (error) {
      this.setData({ cityGroups: [] });
    }
  },

  onCitySearch(event) {
    const cityKeyword = (event.detail.value || "").trim();
    const cities = cityKeyword
      ? this.data.allCities.filter((city) => (city.name || "").includes(cityKeyword))
      : this.data.allCities;
    this.setData({ cityKeyword, cityGroups: groupCities(cities) });
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
    wx.removeStorageSync("selectedPlaceKeyword");
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
    .sort((left, right) => {
      if (left === "#") return 1;
      if (right === "#") return -1;
      return left.localeCompare(right, "en");
    })
    .map((letter) => ({
      letter,
      cities: buckets[letter].sort((left, right) => (left.name || "").localeCompare(right.name || "", "zh-CN"))
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
