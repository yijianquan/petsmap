# 宠趣行

Java 17 + Spring Boot 后端 + 微信小程序前端。当前主功能聚焦宠物出行地图。

## 功能

- 登录：默认账号 `admin`，默认密码 `admin123`
- 地图：上传和浏览宠物友好地点，支持分类、标签、评论和评分
- 我的：微信用户登录后查看自己上传的地点并退出登录
- AI 问答：后端能力保留，可继续为宠物照护问题提供回答
- 数据库：MySQL
- 登录会话缓存：Redis，应用重启后无需重新登录

## 运行前准备

1. 安装 JDK 17。
2. 启动 MySQL，并保证账号密码与 `src/main/resources/application.yml` 一致。
3. 启动 Redis，默认地址 `localhost:6379`。
4. 如需改数据库账号密码，编辑 `application.yml`。

登录会话会存到 Redis，默认保留 7 天。应用重启后，只要 Redis 里的 session 还在、浏览器 Cookie 未清除，就不用重新登录。

## 数据库脚本

脚本位置：

```text
db/mysql-init.sql
```

导入方式：

```powershell
mysql -uroot -pmysql_XHZW@2021 < db\mysql-init.sql
```

## 运行

```powershell
cd D:\project\wu-jia-you-chong
mvn spring-boot:run
```

如果要使用指定 Maven：

```powershell
D:\maven\bin\mvn.cmd spring-boot:run
```

打开：

```text
http://localhost:8080
```

## API

- `GET /api/places`
- `GET /api/map/search?q=静安寺&city=上海`

## 地图地点搜索

上传地点时，名称输入框会调用地图搜索接口获取候选地点、地址和经纬度。

默认配置为空时使用 OpenStreetMap Nominatim 兜底；如果要使用高德地图，在 `application.yml` 中配置：

```yaml
app:
  map:
    default-city: 上海
    amap-key: 你的高德Web服务Key
```
