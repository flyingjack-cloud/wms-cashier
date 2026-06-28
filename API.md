# wms-cashier API

Base URL (dev): `http://localhost:8086`

All endpoints require a valid JWT Bearer token (`Authorization: Bearer <token>`) unless noted otherwise.

All responses use the unified wrapper:
```json
{
  "code": 200,
  "message": "OK",
  "data": ...,
  "timestamp": "2025-01-01T00:00:00Z"
}
```

Timestamps in request params are **epoch milliseconds**. Timestamps in responses are **ISO-8601 UTC** strings.

---

## OAuth `/oauth`

这三个端点**不需要** Bearer token，专门用于完成 OAuth 2.0 PKCE 授权码回调、token 刷新和登出。  
服务端用 `WMS_SESSION` httpOnly cookie（7 天有效期）在 Redis 中维护会话，前端无需感知 refresh token。

### POST `/oauth/callback`
用授权码 + PKCE verifier 换取 access token，建立服务端会话。

| | |
|---|---|
| Auth | 无 |
| Content-Type | `application/json` |

**Request body**
```json
{ "code": "auth-code-from-uac", "code_verifier": "pkce-verifier" }
```

**Response** `data: TokenResult`
```json
{ "access_token": "eyJ...", "expires_in": 7200 }
```

同时在响应中设置 `WMS_SESSION` httpOnly cookie（maxAge = 604800 s）。

**Error**
- `401` — 授权码无效或已过期（`error.security.authenticated.default`）

---

### POST `/oauth/refresh`
用 `WMS_SESSION` cookie 刷新 access token（refresh token 对前端透明）。

| | |
|---|---|
| Auth | 无（依赖 WMS_SESSION cookie）|

**Response** `data: TokenResult`
```json
{ "access_token": "eyJ...", "expires_in": 7200 }
```

同时重置 `WMS_SESSION` cookie 有效期（maxAge = 604800 s）。

**Error**
- `401` — 无 cookie、会话不存在或 refresh token 已过期

---

### POST `/oauth/logout`
清除服务端会话并清除 cookie。

| | |
|---|---|
| Auth | 无（依赖 WMS_SESSION cookie，可选）|

**Response** `data: null`

若无 cookie 也返回 200，不报错。  
响应中通过 `maxAge=0` 清除 `WMS_SESSION` cookie。

---

## Category `/category`

### GET `/category/parent/{parentId}`
Get sub-categories by parent ID.

| | |
|---|---|
| Auth | Required |
| Path | `parentId: int` |

**Response** `data: Category[]`
```json
[{ "id": 1, "groupId": 10, "parentId": 0, "name": "Electronics" }]
```

---

### GET `/category/{id}`
Get a single category by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: Category`

---

### POST `/category/`
Create a new category.

| | |
|---|---|
| Auth | Required |
| Params | `parentId: int`, `name: string` |

**Response** `data: int` — new category ID

---

### DELETE `/category/{id}`
Delete a category by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

---

## Group `/group`

### GET `/group/`
Get the group that the current user belongs to.

| | |
|---|---|
| Auth | Required |

**Response** `data: Group`
```json
{ "id": 1, "storeName": "My Store", "address": "123 St", "contact": "138xxxx", "createdAt": "2025-01-01T00:00:00Z" }
```

---

### POST `/group/`
Create a new group (store).

| | |
|---|---|
| Auth | Required |
| Params | `storeName: string`, `address?: string`, `contact?: string`, `createTime?: long (epoch ms)` |

**Response** `data: null`

---

### PUT `/group/storename`
Update the store name.

| | |
|---|---|
| Auth | Required |
| Params | `storeName: string` |

**Response** `data: null`

---

### PUT `/group/address`
Update the store address.

| | |
|---|---|
| Auth | Required |
| Params | `address: string` |

**Response** `data: null`

---

### PUT `/group/contact`
Update the store contact.

| | |
|---|---|
| Auth | Required |
| Params | `contact: string` |

**Response** `data: null`

---

### GET `/group/staffs`
Get all staff members in the group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |

**Response** `data: WmsUserProfile[]`

---

### DELETE `/group/staff`
Remove a staff member from the group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: null`

---

### POST `/group/join/id`
Submit a join request by group ID.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |
| Params | `groupId: int` |

**Response** `data: null`

---

### POST `/group/join/phone`
Submit a join request by the owner's phone number.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |
| Params | `phone: string` |

**Response** `data: null`

---

### GET `/group/join/`
Get the group that the current user has a pending join request for.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |

**Response** `data: Group`

---

### DELETE `/group/join/delete`
Cancel the current user's own join request.

| | |
|---|---|
| Auth | `ROLE_DEFAULT` |

**Response** `data: null`

---

### DELETE `/group/join/delete/id`
Reject a specific user's join request (owner action).

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: null`

---

### GET `/group/join/users`
Get all users with a pending join request for the owner's group.

| | |
|---|---|
| Auth | `ROLE_OWNER` |

**Response** `data: WmsUserProfile[]`

---

### POST `/group/join/agree`
Approve a user's join request and assign permissions.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long`, `shopping: boolean`, `inventory: boolean`, `statistics: boolean` |

**Response** `data: null`

---

### PUT `/group/permissions`
Update an existing staff member's permissions.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long`, `shopping: boolean`, `inventory: boolean`, `statistics: boolean` |

**Response** `data: null`

---

### GET `/group/permissions`
Get a staff member's permission list.

| | |
|---|---|
| Auth | `ROLE_OWNER` |
| Params | `userId: long` |

**Response** `data: string[]` — e.g. `["PERMISSION:shopping", "PERMISSION:inventory"]`

---

## Merchandise `/merchandise`

### GET `/merchandise/`
Get paginated merchandise list.

| | |
|---|---|
| Auth | Required |
| Params | `sold: boolean`, `limit: int (1–999)`, `offset: int (≥0)` |

**Response** `data: { count: int, merchandise: Merchandise[] }`
```json
{
  "count": 42,
  "merchandise": [
    { "id": 1, "groupId": 10, "cateId": 2, "cost": "100.00", "price": "150.00", "imei": "123456789", "sold": false, "createdAt": "2025-01-01T00:00:00Z" }
  ]
}
```

---

### GET `/merchandise/cate`
Get all merchandise under a category.

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int` |

**Response** `data: Merchandise[]`

---

### POST `/merchandise/`
Add merchandise (supports batch via IMEI list).

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int`, `cost: decimal`, `price: decimal`, `imei_list: string[]`, `create_time: long (epoch ms)` |

**Response** `data: null`

---

### PUT `/merchandise/{id}`
Update merchandise cost, price, and IMEI.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |
| Params | `cost: decimal`, `price: decimal`, `imei: string` |

**Response** `data: null`

---

### DELETE `/merchandise/{id}`
Delete merchandise by ID.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

---

### GET `/merchandise/search`
Full-text search merchandise by IMEI or other fields.

| | |
|---|---|
| Auth | Required |
| Params | `text: string`, `sold: boolean` |

**Response** `data: Merchandise[]`

---

### GET `/merchandise/account`
Get merchandise count statistics grouped by category.

| | |
|---|---|
| Auth | Required |

**Response** `data: MeCount[]`

---

## Notice `/notice`

### GET `/notice/`
Get the latest notice of a given type.

| | |
|---|---|
| Auth | Required |
| Params | `type: string` |

**Response** `data: Notice`

---

## Order `/order`

### POST `/order/`
Create a single order.

| | |
|---|---|
| Auth | Required |
| Params | `me_id: int`, `selling_price: decimal`, `selling_time?: long (epoch ms)`, `remark: string` |

**Response** `data: int` — new order ID

---

### POST `/order/batch`
Batch create orders.

| | |
|---|---|
| Auth | Required |
| Body | `Order[]` |

```json
[
  {
    "groupId": 10,
    "meId": 5,
    "sellingPrice": "150.00",
    "sellingTime": "2025-01-01T00:00:00Z",
    "remark": "cash",
    "returned": false
  }
]
```

**Response** `data: null`

---

### GET `/order/range`
Get orders within a time range.

| | |
|---|---|
| Auth | Required |
| Params | `start: long (epoch ms)`, `end: long (epoch ms)` |

**Response** `data: Order[]`

---

### PUT `/order/return/{id}`
Mark an order as returned.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

---

## Profile `/profile`

### GET `/profile/role`
Get the current user's role.

| | |
|---|---|
| Auth | Required |

**Response** `data: string` — e.g. `"ROLE_OWNER"`

---

### GET `/profile/permissions`
Get the current user's permission list.

| | |
|---|---|
| Auth | Required |

**Response** `data: string[]` — e.g. `["PERMISSION:shopping", "PERMISSION:inventory"]`

---

### PUT `/profile/nickname`
Update the current user's nickname.

| | |
|---|---|
| Auth | Required |
| Params | `nickname: string` |

**Response** `data: null`
