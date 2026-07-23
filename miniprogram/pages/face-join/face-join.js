const { request, asList, baseUrl, ensureLogin } = require("../../utils/request");
const realtime = require("../../utils/realtime");

Page({
  data: { groupId: null, groupName: "", code: "", digits: ["", "", "", ""], inputFocus: false, session: null, participants: [], loading: false, remaining: "", baseUrl: "" },
  onLoad(options) {
    if (!ensureLogin()) return;
    this.setData({ groupId: options.groupId ? Number(options.groupId) : null, groupName: decode(options.groupName), baseUrl: baseUrl() });
    wx.setNavigationBarTitle({ title: options.groupId ? "面对面邀请" : "面对面进群" });
    this.offRealtime = realtime.on(event => {
      if (event.type === "face.updated") this.refresh();
      if (event.type === "join.approved") wx.showModal({ title: "申请已通过", content: "你现在可以进入群聊。", showCancel: false, success: () => this.openGroup() });
      if (event.type === "join.rejected") wx.showToast({ title: "入群申请未通过", icon: "none" });
    });
    realtime.connect();
    if (options.groupId) this.create();
    else setTimeout(() => this.setData({ inputFocus: true }), 120);
  },
  onUnload() { if (this.offRealtime) this.offRealtime(); if (this.timer) clearInterval(this.timer); },
  beginInput() {
    if (this.data.groupId || this.data.loading) return;
    this.setData({ code: "", digits: ["", "", "", ""], session: null, participants: [], inputFocus: false });
    setTimeout(() => this.setData({ inputFocus: true }), 30);
  },
  onCode(event) { const code = String(event.detail.value || "").replace(/\D/g, "").slice(0, 4); this.setData({ code, digits: [0,1,2,3].map(index => code[index] || ""), inputFocus: code.length < 4 }); },
  async create() {
    if (this.data.loading) return; this.setData({ loading: true });
    try {
      const location = await getLocation();
      const session = await request({ url: `/miniapp/api/walk-groups/${this.data.groupId}/face-sessions`, method: "POST", data: location });
      this.setData({ session, code: session.code, digits: session.code.split("") });
      realtime.watchFace(session.code); this.startCountdown(); this.refresh();
    } finally { this.setData({ loading: false }); }
  },
  async enter() {
    if (this.data.code.length !== 4 || this.data.loading) { wx.showToast({ title: "请输入四位数字", icon: "none" }); return; }
    this.setData({ loading: true });
    try {
      const location = await getLocation();
      const result = await request({ url: "/miniapp/api/face-sessions/enter", method: "POST", data: { code: this.data.code, ...location }, silent: true });
      this.setData({ session: result, inputFocus: false }); realtime.watchFace(result.code); this.startCountdown(); await this.refresh();
      if (result.status === "JOINED") wx.showModal({ title: "已加入群聊", content: result.groupName, showCancel: false, success: () => this.openGroup() });
      else wx.showModal({ title: "已提交申请", content: "群主或管理员同意后即可进入群聊。", showCancel: false });
    } catch (error) {
      this.setData({ code: "", digits: ["", "", "", ""], inputFocus: false });
      const missing = String(error && error.message || "").includes("过期或不存在");
      wx.showModal({ title: missing ? "没有找到面对面邀请" : "暂时无法进入", content: missing ? "请让已加入该群的成员先从群信息中点击“面对面邀请”，再输入对方屏幕上的四位数字。" : String(error && error.message || "请稍后重试"), showCancel: false, success: () => setTimeout(() => this.setData({ inputFocus: true }), 80) });
    } finally { this.setData({ loading: false }); }
  },
  async refresh() {
    if (!this.data.code || this.data.code.length !== 4) return;
    try { const session = await request({ url: `/miniapp/api/face-sessions/${this.data.code}` }); this.setData({ session, participants: asList(session.participants).map(item => ({ ...item, avatarSrc: avatar(item.avatarUrl, this.data.baseUrl) })) }); } catch (error) {}
  },
  startCountdown() { if (this.timer) clearInterval(this.timer); const tick=()=>{const left=Math.max(0,Math.ceil((((this.data.session&&this.data.session.expiresAt)||0)-Date.now())/1000));this.setData({remaining:left?`${Math.floor(left/60)}:${String(left%60).padStart(2,"0")}`:"已过期"});};tick();this.timer=setInterval(tick,1000); },
  openGroup() { const session=this.data.session;if(!session)return;wx.redirectTo({url:`/pages/walk-chat/walk-chat?id=${session.groupId}&name=${encodeURIComponent(session.groupName||"")}`}); }
});
function getLocation(){return new Promise((resolve,reject)=>wx.getLocation({type:"gcj02",success:res=>resolve({latitude:res.latitude,longitude:res.longitude}),fail:()=>{wx.showToast({title:"需要定位才能确认面对面距离",icon:"none"});reject(new Error("location denied"));}}));}
function decode(value){try{return decodeURIComponent(value||"");}catch(error){return value||"";}}
function avatar(value,root){if(!value)return"";return value.startsWith("/miniapp/")?`${root}${value}`:value;}
