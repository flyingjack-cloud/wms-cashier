# wms-cashier 部署操作手册

## 概述

本服务通过 ArgoCD GitOps 部署，k8s 配置由 `k8s-gitops` 仓库管理。**Secrets 不进 GitOps 仓库**，需在目标集群手动提前创建；ArgoCD 只管理 Deployment / ConfigMap / Service 等无密态资源。

所有敏感配置通过 `envFrom: secretRef` 注入为 OS 环境变量，服务启动时由 Spring Boot 读取，不依赖 Spring Cloud Kubernetes API。

| 环境 | 访问方式 | TLS |
|---|---|---|
| beta | 公网，Istio Gateway，HTTPS，`api.flyingjack.top/cashier` | cert-manager 自动签发/续签 |
| prod | 公网，Istio Gateway，HTTPS，`api.flyingjack.top/cashier` | cert-manager 自动签发/续签 |

---

## 手动创建 Secrets（首次部署或凭据轮换时执行）

本服务需两组 Secret，通过 `envFrom: secretRef` 直接注入为 OS 环境变量，无需特殊标签。

### Beta 环境

```bash
# 切换到目标命名空间（如不存在先创建）
kubectl create namespace flyingjack-beta --dry-run=client -o yaml | kubectl apply -f -

# 数据库连接凭据
kubectl create secret generic cashier-connect \
  --from-literal=DB_URL=jdbc:postgresql://beta.flyingcloud.local:5432/wms_cashier \
  --from-literal=DB_USERNAME=postgres \
  --from-literal=DB_PASSWORD=<实际密码> \
  -n flyingjack-beta \
  --dry-run=client -o yaml | kubectl apply -f -

# Redis 凭据
kubectl create secret generic cashier-cache-secret \
  --from-literal=REDIS_HOST=<beta Redis地址> \
  --from-literal=REDIS_PASSWORD=<Redis密码，无密码则留空字符串> \
  -n flyingjack-beta \
  --dry-run=client -o yaml | kubectl apply -f -
```

### Prod 环境

```bash
kubectl create namespace flyingjack-prod --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic cashier-connect \
  --from-literal=DB_URL=jdbc:postgresql://prod.flyingcloud.local:5432/wms_cashier \
  --from-literal=DB_USERNAME=<prod数据库用户> \
  --from-literal=DB_PASSWORD=<prod数据库密码> \
  -n flyingjack-prod \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic cashier-cache-secret \
  --from-literal=REDIS_HOST=<prod Redis地址> \
  --from-literal=REDIS_PASSWORD=<prod Redis密码> \
  -n flyingjack-prod \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 镜像仓库拉取凭据（仅认证 registry 需要）

使用无认证的 registry:2 时跳过此步骤。如果 registry 需要认证（如 Harbor），需在每个命名空间创建拉取凭据，并在 `deployment-patch.yaml` 中补充 `imagePullSecrets`：

```bash
kubectl create secret docker-registry harbor-pull-secret \
  --docker-server=<registry地址> \
  --docker-username=<用户名> \
  --docker-password=<密码> \
  -n flyingjack-beta   # prod 环境替换命名空间重复执行
```

---

## 验证 Secrets 是否正确

### Beta 环境

```bash
# 检查 secret 存在
kubectl get secret cashier-connect cashier-cache-secret -n flyingjack-beta

# 查看 secret 的 key（不显示值）
kubectl get secret cashier-connect -n flyingjack-beta -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
kubectl get secret cashier-cache-secret -n flyingjack-beta -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
```

### Prod 环境

```bash
kubectl get secret cashier-connect cashier-cache-secret -n flyingjack-prod

kubectl get secret cashier-connect -n flyingjack-prod -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
kubectl get secret cashier-cache-secret -n flyingjack-prod -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
```

---

## AUTH_ISSUER_URI 说明

`AUTH_ISSUER_URI` 不是敏感信息，注入方式与其他服务不同：直接写在 `k8s-gitops/wms-cashier/overlays/{profile}/kustomization.yaml` 的 `configMapGenerator` 里，不需要手动创建 Secret。

- 该值必须与 auth-service 在对应环境下签发 JWT 时实际使用的 issuer 完全一致，否则 JWKS 校验会失败。
- beta 当前值为 `http://100.107.74.15:30880`（auth-service beta 的 Tailscale NodePort 地址，见 `auth-service/DEPLOY.md`）。
- prod 当前值为 `https://auth.flyingjack.top`。
- 若 auth-service 的部署地址发生变化，需同步更新此处，并 `kubectl rollout restart deployment/wms-cashier-v1 -n <namespace>`。

---

## 前置：确认 api.flyingjack.top HTTPS 已就绪

本服务通过共享 Gateway 暴露在 `https://api.flyingjack.top`，TLS 证书由 `shared-networking` 统一管理。**首次在新集群部署前，需确认 `shared-networking` 已部署且证书已签发**，步骤见 `k8s-gitops/shared-networking/DEPLOY.md`。

---

## ArgoCD Application 创建

> **前置条件**：Namespace 须提前手动创建，见 `k8s-gitops/shared/DEPLOY.md`。ArgoCD Application 不负责创建 Namespace，防止 auto-prune 误删命名空间。

在 ArgoCD 所在集群执行（或通过 ArgoCD UI 导入）：

```bash
kubectl apply -f - <<EOF
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: wms-cashier-beta
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/flyingjack-cloud/k8s-gitops
    targetRevision: main
    path: wms-cashier/overlays/beta
  destination:
    server: https://kubernetes.default.svc
    namespace: flyingjack-beta
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
EOF
```

prod 环境将 `beta` 替换为 `prod` 重复执行。

---

## 部署验证（Smoke Test）

本服务所有接口均要求 OAuth2 Bearer Token（`anyRequest().authenticated()`，无匿名端点），因此无法用业务接口直接验证返回数据，只能验证路由是否打通。

```
GET https://api.flyingjack.top/cashier/profile/
```

预期返回：HTTP `401 Unauthorized`（未携带 token）

验证点：
- 返回 401 说明 **Istio `/cashier/` 前缀路由**正常（前缀已被剥除，请求正确到达 wms-cashier）
- 若返回 502/503，检查 Pod 是否正常运行：`kubectl get pods -n flyingjack-beta`（或 `flyingjack-prod`）
- 若返回 404，检查 VirtualService 路由规则是否已被 ArgoCD 同步
- 携带合法 token 重试应返回 200 及 profile 数据，用于验证 JWKS 校验（即 `AUTH_ISSUER_URI` 配置正确）是否生效

---

## 注意事项

- Secret 变更后需手动 `kubectl rollout restart deployment/wms-cashier-v1 -n <namespace>` 触发 Pod 重启以读取新值。
- prod 环境禁止直接 `kubectl apply`，所有变更须通过 ArgoCD 同步。
