const { request, upload, asList, baseUrl } = require("../../utils/request");
const SPECIES = [{ code: "CAT", label: "猫咪", icon: "猫" }, { code: "DOG", label: "狗狗", icon: "狗" }, { code: "OTHER", label: "异宠", icon: "宠" }];
Page({
  data: { pets: [], speciesOptions: SPECIES, currentDate: "", showForm: false, breeds: [], genders: [], breedIndex: -1, genderIndex: -1, avatarPath: "", form: { species: "CAT", name: "", breedCode: "", genderCode: "", neutered: false, birthDate: "" } },
  onLoad() { const d=new Date(),pad=v=>String(v).padStart(2,"0");this.setData({currentDate:`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`}); },
  onShow() { this.load(); },
  async load() { const root=baseUrl(); const pets=asList(await request({url:"/miniapp/api/pets"})).map(p=>({...p,avatarSrc:p.avatarUrl?`${root}${p.avatarUrl}`:""}));this.setData({pets}); },
  async openAdd() { this.setData({ showForm:true, avatarPath:"", breedIndex:-1, genderIndex:-1, form:{species:"CAT",name:"",breedCode:"",genderCode:"",neutered:false,birthDate:""} }); await Promise.all([this.loadBreeds("CAT"),this.loadGenders()]); },
  closeForm(){this.setData({showForm:false});}, noop(){},
  async selectSpecies(e){const species=e.currentTarget.dataset.code;this.setData({"form.species":species,"form.breedCode":"",breedIndex:-1});await this.loadBreeds(species);},
  async loadBreeds(species){this.setData({breeds:asList(await request({url:`/miniapp/api/dictionaries?type=PET_BREED&parentCode=${species}`}))});},
  async loadGenders(){this.setData({genders:asList(await request({url:"/miniapp/api/dictionaries?type=PET_GENDER"}))});},
  onName(e){this.setData({"form.name":e.detail.value||""});},
  onBreed(e){const i=Number(e.detail.value);this.setData({breedIndex:i,"form.breedCode":this.data.breeds[i].code});},
  onGender(e){const i=Number(e.detail.value);this.setData({genderIndex:i,"form.genderCode":this.data.genders[i].code});},
  onNeutered(e){this.setData({"form.neutered":e.currentTarget.dataset.value===true||e.currentTarget.dataset.value==="true"});},
  onBirth(e){this.setData({"form.birthDate":e.detail.value});},
  chooseAvatar(){wx.chooseMedia({count:1,mediaType:["image"],sourceType:["album","camera"],success:r=>this.setData({avatarPath:r.tempFiles[0].tempFilePath})});},
  async save(){const f=this.data.form;if(!f.name.trim()||!f.breedCode||!f.genderCode){wx.showToast({title:"请完整填写宠物信息",icon:"none"});return;}if(!this.data.avatarPath){wx.showToast({title:"请上传宠物头像",icon:"none"});return;}await upload({url:"/miniapp/api/pets",filePath:this.data.avatarPath,name:"file",formData:{species:f.species,name:f.name.trim(),breedCode:f.breedCode,genderCode:f.genderCode,neutered:String(f.neutered),birthDate:f.birthDate}});this.setData({showForm:false});wx.showToast({title:"添加成功",icon:"success"});this.load();},
  remove(e){const id=e.currentTarget.dataset.id;wx.showModal({title:"删除宠物",content:"确定删除这只宠物吗？",success:async r=>{if(r.confirm){await request({url:`/miniapp/api/pets/${id}`,method:"DELETE"});this.load();}}});}
});
