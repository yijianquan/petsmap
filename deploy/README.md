# 吾家有宠阿里云部署说明

推荐用轻量应用服务器 + Docker Compose，先把 MySQL 和 Redis 放在同一台服务器里，成本最低。

## 服务器需要开放

- 22：SSH 登录
- 80：网站访问
- 443：后续配置 HTTPS 时再开放

不要开放 3306 和 6379。

## 第一次部署

在本机打包：

```powershell
cd D:\project\wu-jia-you-chong
mvn -DskipTests package
```

把整个项目上传到服务器，比如 `/opt/wu-jia-you-chong`。

服务器安装 Docker 后执行：

```bash
cd /opt/wu-jia-you-chong
cp .env.example .env
vi .env
docker compose up -d --build
docker compose logs -f app
```

`.env` 里至少要改：

```text
MYSQL_ROOT_PASSWORD=你的强密码
APP_ADMIN_PASSWORD=你的后台管理员密码
APP_MAP_AMAP_KEY=你的高德Web服务Key
```

打开：

```text
http://服务器公网IP
```

## 更新版本

本机重新打包后，把新的 `target/wu-jia-you-chong-0.0.1-SNAPSHOT.jar` 上传到服务器项目目录，然后执行：

```bash
cd /opt/wu-jia-you-chong
docker compose up -d --build app
docker compose logs -f app
```

## 常用命令

```bash
docker compose ps
docker compose logs -f app
docker compose restart app
docker compose down
```

## 数据备份

```bash
docker exec wjyc-mysql mysqldump -uroot -p wu_jia_you_chong > backup.sql
```

备份文件要定期下载到本地电脑，服务器损坏时才能恢复。
