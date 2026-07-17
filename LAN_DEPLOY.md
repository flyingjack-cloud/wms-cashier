# 局域网 Docker 镜像部署

这套脚本用于本地开发环境，不依赖镜像仓库。它会在本机执行 Maven 构建和
`docker build`，把镜像、运行配置和远端部署脚本打成一个 tar 包，然后通过
SCP 上传至目标服务器并运行。`wms-cashier` 默认映射业务端口 `8086` 和管理端口
`8081`，并通过 `SPRING_APPLICATION_JSON` 指向局域网 auth-service。默认使用
`Dockerfile.lan` 的 JRE 基础镜像缩小
传输包，不改变项目现有 `Dockerfile` 的构建行为。

## 准备配置

```bash
cp .env.lan.example .env.lan
chmod 600 .env.lan
```

修改 `.env.lan`。其中 `MAVEN_*`、`IMAGE_*`、`DOCKER_*`、容器/端口、
`HEALTHCHECK_*`、`DEPLOY_*` 和 `BUNDLE_PATH` 只供部署脚本使用，其余变量会写入
包内的 `app.env` 并传给 Spring Boot 容器。

`.env.lan` 会作为 Bash env 文件加载，因此带空格或 shell 特殊字符的值应使用
单引号，例如 `DB_PASSWORD='password with spaces'`。该文件包含密钥且不会被 Git
跟踪；镜像包同样包含运行时密钥，应只在可信局域网中传输并妥善删除。

本机需要 Maven、Docker、tar、ssh 和 scp。目标服务器需要 Bash、Docker 和 tar；
若开启健康检查，还需要 curl。建议提前配置 SSH key 登录：

```bash
ssh-copy-id deploy@192.168.31.100
```

## 使用

构建可通过 SCP 传输的镜像包：

```bash
./scripts/lan-deploy.sh build
```

产物位于 `dist/lan/*.tar.gz`。只上传并部署最近一次构建的包：

```bash
./scripts/lan-deploy.sh deploy
```

一次完成构建、上传和部署：

```bash
./scripts/lan-deploy.sh all
```

也可以指定另一个配置文件：

```bash
./scripts/lan-deploy.sh all .env.lan.test-server
```

若希望手工 SCP，先上传包并在目标服务器执行：

```bash
mkdir -p /tmp/wms-cashier-lan
tar -xzf flyingjack-wms-cashier-1.0.0-rc.tar.gz -C /tmp/wms-cashier-lan
/tmp/wms-cashier-lan/deploy.sh /tmp/wms-cashier-lan \
  /tmp/wms-cashier-lan/flyingjack-wms-cashier-1.0.0-rc.tar.gz
```

脚本会加载镜像、替换同名旧容器、按 env 启动新容器并等待 Actuator 健康检查。
部署失败时会打印容器最近 100 行日志并保留容器用于排查。
