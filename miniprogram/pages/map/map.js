const { request, ensureLogin, baseUrl, asList } = require("../../utils/request");

const defaultLocation = { latitude: 31.2304, longitude: 121.4737, label: "默认位置：上海" };
const defaultCityLocation = { name: "上海市", latitude: defaultLocation.latitude, longitude: defaultLocation.longitude };
const markerIcons = {
  RESTAURANT: "./marker-restaurant.png",
  MALL: "./marker-mall.png",
  HOTEL: "./marker-hotel.png",
  PARK: "./marker-park.png",
  SCENIC: "./marker-park.png",
  LAWN: "./marker-park.png",
  PET_STORE: "./marker-pet-store.png",
  HOSPITAL: "./marker-hospital.png"
};
const typeColors = {
  RESTAURANT: "#F28C28",
  MALL: "#111111",
  HOTEL: "#2F80ED",
  PARK: "#178F5D",
  SCENIC: "#178F5D",
  LAWN: "#178F5D",
  PET_STORE: "#8E5CF7",
  HOSPITAL: "#E84D4F"
};
const typeSoftColors = {
  RESTAURANT: "#FFF2E3",
  MALL: "#F1F1F1",
  HOTEL: "#EAF3FF",
  PARK: "#EAF7F0",
  SCENIC: "#EAF7F0",
  LAWN: "#EAF7F0",
  PET_STORE: "#F3EEFF",
  HOSPITAL: "#FFEEEE"
};
const cityCenters = {
  上海市: defaultCityLocation,
  北京市: { name: "北京市", latitude: 39.9042, longitude: 116.4074 },
  杭州市: { name: "杭州市", latitude: 30.2741, longitude: 120.1551 },
  苏州市: { name: "苏州市", latitude: 31.2989, longitude: 120.5853 },
  深圳市: { name: "深圳市", latitude: 22.5431, longitude: 114.0579 },
  广州市: { name: "广州市", latitude: 23.1291, longitude: 113.2644 },
  成都市: { name: "成都市", latitude: 30.5728, longitude: 104.0668 },
  南京市: { name: "南京市", latitude: 32.0603, longitude: 118.7969 }
};

Page({
  data: {
    latitude: defaultLocation.latitude,
    longitude: defaultLocation.longitude,
    mapScale: 14,
    city: "上海市",
    locationLabel: defaultLocation.label,
    searchLabel: "搜索目的地 / 地点",
    baseUrl: "",
    places: [],
    filteredPlaces: [],
    displayedPlaces: [],
    markers: [],
    placeTypes: [],
    typeFilters: [],
    placeTags: [],
    formTagOptions: [],
    formTagGroups: [],
    selectedTypes: [],
    keyword: "",
    destination: null,
    drawerExpanded: false,
    drawerTouchStartY: 0,
    showForm: false,
    placeSearchKeyword: "",
    placeSuggestions: [],
    editingId: null,
    form: emptyForm()
  },

  onShow() {
    this.setData({ baseUrl: baseUrl() });
    const city = wx.getStorageSync("selectedCity") || "上海市";
    const cityLocation = wx.getStorageSync("selectedCityLocation") || cityCenters[city] || null;
    const destination = wx.getStorageSync("selectedDestination") || null;
    const searchPlace = wx.getStorageSync("selectedPlaceKeyword") || "";
    if (destination) {
      wx.removeStorageSync("selectedDestination");
      this.setData({
        city,
        destination,
        latitude: destination.latitude,
        longitude: destination.longitude,
        mapScale: 14,
        locationLabel: `目的地：${destination.name}`,
        searchLabel: destination.name,
        keyword: searchPlace
      });
      this.loadOptions();
      this.loadPlaces();
      return;
    }
    if (cityLocation && cityLocation.latitude != null && cityLocation.longitude != null) {
      this.setData({
        city,
        keyword: searchPlace,
        searchLabel: searchPlace || "搜索目的地 / 地点",
        locationLabel: city,
        latitude: cityLocation.latitude,
        longitude: cityLocation.longitude,
        mapScale: 14,
        destination: null
      });
      this.loadOptions();
      this.loadPlaces();
      return;
    }
    this.setData({
      city,
      keyword: searchPlace,
      searchLabel: searchPlace || "搜索目的地 / 地点",
      locationLabel: defaultLocation.label,
      latitude: defaultLocation.latitude,
      longitude: defaultLocation.longitude,
      mapScale: 14
    });
    this.loadOptions();
    this.locate();
  },

  async loadOptions() {
    const data = await request({ url: "/miniapp/api/options" });
    const placeTypes = data.placeTypes || [];
    const selectedTypes = this.data.selectedTypes || [];
    this.setData({
      placeTypes,
      selectedTypes,
      typeFilters: buildTypeFilters(placeTypes, selectedTypes),
      placeTags: data.placeTags || []
    });
    this.refreshFormTagOptions();
  },

  locate() {
    wx.getLocation({
      type: "gcj02",
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          mapScale: 14,
          locationLabel: "已使用当前位置排序"
        });
        this.loadPlaces();
      },
      fail: () => {
        if (this.data.destination) {
          this.loadPlaces();
          return;
        }
        wx.setStorageSync("selectedCity", "上海市");
        wx.setStorageSync("selectedCityLocation", defaultCityLocation);
        this.setData({
          city: "上海市",
          latitude: defaultLocation.latitude,
          longitude: defaultLocation.longitude,
          mapScale: 14,
          locationLabel: defaultLocation.label
        });
        this.loadPlaces();
      }
    });
  },

  async loadPlaces() {
    try {
      const bounds = await this.currentMapBounds();
      const query = `minLat=${encodeURIComponent(bounds.minLat)}&maxLat=${encodeURIComponent(bounds.maxLat)}&minLng=${encodeURIComponent(bounds.minLng)}&maxLng=${encodeURIComponent(bounds.maxLng)}`;
      const places = await request({ url: `/miniapp/api/places?${query}` });
      const enriched = asList(places, "地点数据格式异常").map((place) => this.withDistance(place));
      enriched.sort((a, b) => (a.distance || 999999) - (b.distance || 999999) || (b.averageRating || 0) - (a.averageRating || 0));
      this.setData({ places: enriched });
      this.refreshList();
    } catch (error) {
      this.setData({ places: [], filteredPlaces: [], displayedPlaces: [], markers: [] });
    }
  },

  currentMapBounds() {
    return new Promise((resolve) => {
      const fallback = boundsAround(this.data.latitude, this.data.longitude);
      const mapContext = wx.createMapContext("mainMap", this);
      mapContext.getRegion({
        success: (res) => {
          if (!res || !res.southwest || !res.northeast) {
            resolve(fallback);
            return;
          }
          resolve({
            minLat: res.southwest.latitude,
            maxLat: res.northeast.latitude,
            minLng: res.southwest.longitude,
            maxLng: res.northeast.longitude
          });
        },
        fail: () => resolve(fallback)
      });
    });
  },

  onRegionChange(event) {
    if (event.type !== "end" || this.data.showForm) {
      return;
    }
    if (this.regionTimer) {
      clearTimeout(this.regionTimer);
    }
    this.regionTimer = setTimeout(() => this.loadPlaces(), 360);
  },

  withDistance(place) {
    const typeColor = typeColors[place.type] || "#6B7280";
    const typeSoftColor = typeSoftColors[place.type] || "#F1F3F5";
    if (place.latitude == null || place.longitude == null) {
      return { ...place, distance: 999999, distanceText: "未标坐标", typeColor, typeSoftColor };
    }
    const distance = calcDistance(this.data.latitude, this.data.longitude, place.latitude, place.longitude);
    return {
      ...place,
      distance,
      distanceText: distance < 1000 ? `${Math.round(distance)}m` : `${(distance / 1000).toFixed(1)}km`,
      typeColor,
      typeSoftColor
    };
  },

  refreshList() {
    const keyword = this.data.keyword.trim().toLowerCase();
    const selectedTypes = this.data.selectedTypes || [];
    const filteredPlaces = this.data.places.filter((place) => {
      const matchType = selectedTypes.length === 0 || selectedTypes.includes(place.type);
      const haystack = `${place.name || ""}${place.address || ""}${place.description || ""}${(place.tags || []).join("")}`.toLowerCase();
      return matchType && (!keyword || haystack.includes(keyword));
    });
    this.setData({
      filteredPlaces,
      displayedPlaces: this.data.drawerExpanded ? filteredPlaces : filteredPlaces.slice(0, 1),
      markers: filteredPlaces
        .filter((place) => place.latitude != null && place.longitude != null)
        .map((place, index) => ({
          id: Number(place.id),
          latitude: place.latitude,
          longitude: place.longitude,
          title: place.name,
          iconPath: markerIcons[place.type] || "./marker-default.png",
          width: 43,
          height: 43,
          anchor: { x: 0.5, y: 1 },
          callout: {
            content: place.name,
            display: "BYCLICK",
            color: "#111111",
            bgColor: "#ffffff",
            fontSize: 13,
            padding: 8,
            borderRadius: 8
          }
        }))
    });
  },

  toggleDrawer() {
    this.setData({ drawerExpanded: !this.data.drawerExpanded });
    this.refreshList();
  },

  onDrawerTouchStart(event) {
    this.setData({ drawerTouchStartY: event.changedTouches[0].clientY });
  },

  onDrawerTouchEnd(event) {
    const endY = event.changedTouches[0].clientY;
    const delta = endY - this.data.drawerTouchStartY;
    if (delta < -24 && !this.data.drawerExpanded) {
      this.setData({ drawerExpanded: true });
      this.refreshList();
    }
    if (delta > 24 && this.data.drawerExpanded) {
      this.setData({ drawerExpanded: false });
      this.refreshList();
    }
  },

  selectType(event) {
    const type = event.currentTarget.dataset.type || "";
    const selectedTypes = [...(this.data.selectedTypes || [])];
    const index = selectedTypes.indexOf(type);
    if (index >= 0) {
      selectedTypes.splice(index, 1);
    } else {
      selectedTypes.push(type);
    }
    this.setData({
      selectedTypes,
      typeFilters: buildTypeFilters(this.data.placeTypes || [], selectedTypes)
    });
    this.refreshList();
  },

  onKeyword(event) {
    this.setData({ keyword: event.detail.value });
  },

  openCity() {
    wx.navigateTo({ url: `/pages/city/city?city=${encodeURIComponent(this.data.city)}` });
  },

  openSearch() {
    wx.navigateTo({ url: `/pages/search/search?city=${encodeURIComponent(this.data.city)}` });
  },

  focusPlace(event) {
    const id = Number(event.currentTarget.dataset.id);
    const place = this.data.places.find((item) => Number(item.id) === id);
    if (place && place.latitude != null && place.longitude != null) {
      this.setData({ latitude: place.latitude, longitude: place.longitude, mapScale: 14 });
    }
  },

  openPlaceDetail(event) {
    const id = event.currentTarget.dataset.id;
    this.navigateToPlace(id);
  },

  onMarkerTap(event) {
    const id = event.detail.markerId;
    const place = this.data.places.find((item) => Number(item.id) === Number(id));
    if (place) {
      this.navigateToPlace(place.id);
    }
  },

  navigateToPlace(id) {
    if (!id) return;
    wx.navigateTo({ url: `/pages/place-detail/place-detail?id=${id}` });
  },

  openAdd() {
    if (!ensureLogin()) return;
    this.setData({
      showForm: true,
      editingId: null,
      placeSearchKeyword: "",
      placeSuggestions: [],
      form: emptyForm()
    });
    this.refreshFormTagOptions();
  },

  editPlace(event) {
    if (!ensureLogin()) return;
    const id = Number(event.currentTarget.dataset.id);
    const place = this.data.places.find((item) => Number(item.id) === id);
    if (!place) return;
    this.setData({
      showForm: true,
      editingId: id,
      placeSearchKeyword: place.name || "",
      placeSuggestions: [],
      form: {
        name: place.name || "",
        type: place.type || "RESTAURANT",
        typeName: place.typeName || "吃饭",
        address: place.address || "",
        latitude: place.latitude,
        longitude: place.longitude,
        description: place.description || "",
        policyNote: place.policyNote || "",
        tags: place.tags || []
      }
    });
    this.refreshFormTagOptions();
  },

  closeForm() {
    if (this.placeSearchTimer) {
      clearTimeout(this.placeSearchTimer);
      this.placeSearchTimer = null;
    }
    this.setData({ showForm: false, placeSuggestions: [] });
  },

  noop() {},

  onFormInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: event.detail.value });
  },

  onPlaceSearchInput(event) {
    const keyword = event.detail.value;
    this.setData({ placeSearchKeyword: keyword });
    if (this.placeSearchTimer) {
      clearTimeout(this.placeSearchTimer);
    }
    if (!keyword || keyword.trim().length < 2) {
      this.setData({ placeSuggestions: [] });
      return;
    }
    this.placeSearchTimer = setTimeout(async () => {
      try {
        const results = await request({
          url: `/miniapp/api/map/search?q=${encodeURIComponent(keyword.trim())}&city=${encodeURIComponent(this.data.city)}`
        });
        this.setData({ placeSuggestions: asList(results).slice(0, 8) });
      } catch (error) {
        this.setData({ placeSuggestions: [] });
      }
    }, 320);
  },

  choosePlaceSuggestion(event) {
    const index = Number(event.currentTarget.dataset.index);
    const place = this.data.placeSuggestions[index];
    if (!place) return;
    this.setData({
      placeSearchKeyword: place.name || "",
      placeSuggestions: [],
      "form.name": place.name || "",
      "form.address": place.address || "",
      "form.latitude": place.latitude,
      "form.longitude": place.longitude,
      "form.type": place.type || this.data.form.type,
      "form.typeName": place.typeName || this.data.form.typeName
    });
  },

  onTypeChange(event) {
    const option = this.data.placeTypes[Number(event.detail.value)];
    this.setData({ "form.type": option.value, "form.typeName": option.label });
  },

  toggleTag(event) {
    const tag = event.currentTarget.dataset.tag;
    const tags = [...this.data.form.tags];
    const index = tags.indexOf(tag);
    if (index >= 0) {
      tags.splice(index, 1);
    } else {
      tags.push(tag);
    }
    this.setData({ "form.tags": tags });
    this.refreshFormTagOptions();
  },

  refreshFormTagOptions() {
    const selected = new Set(this.data.form.tags || []);
    const groupedTags = buildTagGroups(this.data.placeTags || [], selected);
    this.setData({
      formTagOptions: (this.data.placeTags || []).map((name) => ({ name, active: selected.has(name) })),
      formTagGroups: groupedTags
    });
  },

  async useCurrentPoint() {
    const point = await getCurrentPoint().catch(() => ({
      latitude: this.data.latitude,
      longitude: this.data.longitude
    }));
    let current = {
      name: "当前位置",
      address: this.data.locationLabel || this.data.city,
      latitude: point.latitude,
      longitude: point.longitude
    };
    try {
      const reverse = await request({
        url: `/miniapp/api/map/reverse?latitude=${encodeURIComponent(point.latitude)}&longitude=${encodeURIComponent(point.longitude)}`
      });
      current = { ...current, ...reverse };
    } catch (error) {
      // Keep the coordinate even when reverse geocoding is temporarily unavailable.
    }
    const name = current.name || current.address || "当前位置";
    this.setData({
      placeSearchKeyword: name,
      placeSuggestions: [],
      "form.name": name,
      "form.address": current.address || this.data.city,
      "form.latitude": current.latitude,
      "form.longitude": current.longitude
    });
    wx.showToast({ title: "已使用当前位置", icon: "none" });
  },

  async savePlace() {
    if (!ensureLogin()) return;
    const form = this.data.form;
    if (!form.name.trim() || form.latitude == null || form.longitude == null) {
      wx.showToast({ title: "请选择联想地点或当前位置", icon: "none" });
      return;
    }
    const payload = {
      ...form,
      latitude: form.latitude == null ? this.data.latitude : form.latitude,
      longitude: form.longitude == null ? this.data.longitude : form.longitude
    };
    if (this.data.editingId) {
      await request({ url: `/miniapp/api/places/${this.data.editingId}`, method: "PUT", data: payload });
    } else {
      await request({ url: "/miniapp/api/places", method: "POST", data: payload });
    }
    this.setData({ showForm: false });
    wx.showToast({ title: "已保存", icon: "success" });
    this.loadPlaces();
  },

  goMine() {
    wx.switchTab({ url: "/pages/mine/mine" });
  }
});

function emptyForm() {
  return {
    name: "",
    type: "RESTAURANT",
    typeName: "吃饭",
    address: "",
    latitude: null,
    longitude: null,
    description: "",
    policyNote: "",
    tags: []
  };
}

function buildTagGroups(tags, selected) {
  const groups = [
    { name: "宠物友好", names: ["大狗友好", "猫咪友好", "无小孩", "环境安静"] },
    { name: "设施服务", names: ["停车", "饮水", "室内可进", "可进店", "草坪大", "阴凉多", "夜间照明"] },
    { name: "规则费用", names: ["免费", "需牵引", "可预约"] }
  ];
  const used = new Set();
  const result = groups
    .map((group) => {
      const groupTags = group.names
        .filter((name) => tags.includes(name))
        .map((name) => {
          used.add(name);
          return { name, active: selected.has(name) };
        });
      return { name: group.name, tags: groupTags };
    })
    .filter((group) => group.tags.length > 0);
  const otherTags = tags
    .filter((name) => !used.has(name))
    .map((name) => ({ name, active: selected.has(name) }));
  if (otherTags.length > 0) {
    result.push({ name: "其他", tags: otherTags });
  }
  return result;
}

function buildTypeFilters(types, selectedTypes) {
  const selected = new Set(selectedTypes || []);
  return (types || []).map((type) => ({
    ...type,
    active: selected.has(type.value),
    color: typeColors[type.value] || "#6B7280",
    softColor: typeSoftColors[type.value] || "#F1F3F5"
  }));
}

function calcDistance(lat1, lng1, lat2, lng2) {
  const earthRadius = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
    + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function boundsAround(latitude, longitude) {
  const lat = Number(latitude) || defaultLocation.latitude;
  const lng = Number(longitude) || defaultLocation.longitude;
  return {
    minLat: lat - 0.025,
    maxLat: lat + 0.025,
    minLng: lng - 0.025,
    maxLng: lng + 0.025
  };
}

function toRad(value) {
  return value * Math.PI / 180;
}

function getCurrentPoint() {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: "gcj02",
      success: resolve,
      fail: reject
    });
  });
}
