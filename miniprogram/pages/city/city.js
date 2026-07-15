Page({
  data: {
    currentCity: "上海市",
    cityGroups: [
      {
        letter: "B",
        cities: [
          { name: "北京市", tip: "华北", latitude: 39.9042, longitude: 116.4074 }
        ]
      },
      {
        letter: "C",
        cities: [
          { name: "成都市", tip: "四川", latitude: 30.5728, longitude: 104.0668 },
          { name: "重庆市", tip: "西南", latitude: 29.563, longitude: 106.5516 },
          { name: "长沙市", tip: "湖南", latitude: 28.2282, longitude: 112.9388 },
          { name: "常州市", tip: "江苏", latitude: 31.8107, longitude: 119.9737 }
        ]
      },
      {
        letter: "G",
        cities: [
          { name: "广州市", tip: "广东", latitude: 23.1291, longitude: 113.2644 },
          { name: "贵阳市", tip: "贵州", latitude: 26.647, longitude: 106.6302 }
        ]
      },
      {
        letter: "H",
        cities: [
          { name: "杭州市", tip: "浙江", latitude: 30.2741, longitude: 120.1551 },
          { name: "合肥市", tip: "安徽", latitude: 31.8206, longitude: 117.2272 },
          { name: "海口市", tip: "海南", latitude: 20.044, longitude: 110.1999 }
        ]
      },
      {
        letter: "J",
        cities: [
          { name: "济南市", tip: "山东", latitude: 36.6512, longitude: 117.1201 }
        ]
      },
      {
        letter: "N",
        cities: [
          { name: "南京市", tip: "江苏", latitude: 32.0603, longitude: 118.7969 },
          { name: "宁波市", tip: "浙江", latitude: 29.8683, longitude: 121.544 },
          { name: "南昌市", tip: "江西", latitude: 28.682, longitude: 115.8579 }
        ]
      },
      {
        letter: "Q",
        cities: [
          { name: "青岛市", tip: "山东", latitude: 36.0671, longitude: 120.3826 }
        ]
      },
      {
        letter: "S",
        cities: [
          { name: "上海市", tip: "默认推荐", latitude: 31.2304, longitude: 121.4737 },
          { name: "深圳市", tip: "广东", latitude: 22.5431, longitude: 114.0579 },
          { name: "苏州市", tip: "江苏", latitude: 31.2989, longitude: 120.5853 },
          { name: "沈阳市", tip: "辽宁", latitude: 41.8057, longitude: 123.4315 }
        ]
      },
      {
        letter: "T",
        cities: [
          { name: "天津市", tip: "华北", latitude: 39.3434, longitude: 117.3616 }
        ]
      },
      {
        letter: "W",
        cities: [
          { name: "武汉市", tip: "湖北", latitude: 30.5928, longitude: 114.3055 },
          { name: "无锡市", tip: "江苏", latitude: 31.4912, longitude: 120.3119 }
        ]
      },
      {
        letter: "X",
        cities: [
          { name: "西安市", tip: "陕西", latitude: 34.3416, longitude: 108.9398 },
          { name: "厦门市", tip: "福建", latitude: 24.4798, longitude: 118.0894 }
        ]
      },
      {
        letter: "Z",
        cities: [
          { name: "郑州市", tip: "河南", latitude: 34.7466, longitude: 113.6254 }
        ]
      }
    ]
  },

  onLoad(options) {
    this.setData({ currentCity: decodeParam(options.city) || wx.getStorageSync("selectedCity") || "上海市" });
  },

  selectCity(event) {
    const city = event.currentTarget.dataset.city;
    const selected = this.data.cityGroups
      .flatMap((group) => group.cities)
      .find((item) => item.name === city);
    wx.setStorageSync("selectedCity", city);
    if (selected) {
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

function decodeParam(value) {
  if (!value) return "";
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
}
