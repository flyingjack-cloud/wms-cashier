# wms-cashier API

Base URL (dev): `http://localhost:8086`

All endpoints require a valid JWT Bearer token (`Authorization: Bearer <token>`) unless noted otherwise.

## Timestamps

All timestamps — in query/form params, JSON request bodies, and responses alike — are an **ISO-8601 string with a `Z` suffix** (e.g. `"2025-01-01T00:00:00Z"`), denoting UTC (zero offset). This is symmetric in every direction: a field looks the same whether you're sending it or receiving it, and whether it's a query param (e.g. `GET /order/range?start=2025-01-01T00:00:00Z`) or a JSON body field (e.g. `POST /order/batch`'s `sellingTime`).

The backend stores and handles time exclusively as `Instant` — a single, timezone-free point on the UTC timeline. There is no offset/timezone concept anywhere in this API; converting to a user's local time is entirely the frontend's responsibility.

Never send an offset-less local string (e.g. `"2025-01-01T00:00:00"`, no `Z`/offset) in a JSON body — it's rejected outright since it can't be resolved to an unambiguous instant. A bare epoch number in a JSON body is **not** rejected, but don't send one: Jackson silently parses it as epoch *seconds* rather than milliseconds, producing the wrong instant (this is exactly the bug that prompted this convention). Query/form params are more lenient purely as a side effect of the framework: a bare number there *is* still accepted, and correctly treated as epoch *milliseconds*, for backward compatibility with older clients — but ISO-8601 is the canonical, documented format going forward; new code should always send strings in both places.

All responses use the unified wrapper:
```json
{
  "code": 200,
  "message": "OK",
  "data": ...,
  "timestamp": "2025-01-01T00:00:00Z"
}
```

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
| Params | `storeName: string`, `address?: string`, `contact?: string`, `createTime?: string (ISO-8601 UTC)` |

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
| Params | `sold?: boolean (default false)`, `limit: int (1–999)`, `offset: int (≥0)` |

**Response** `data: { count: int, merchandise: MerchandiseWithCategoryDto[] }` — each item is a `Merchandise` plus a nested `category` object (`null` if the category was since deleted)
```json
{
  "count": 42,
  "merchandise": [
    {
      "id": 1, "groupId": 10, "cateId": 2, "cost": "100.00", "price": "150.00", "imei": "123456789", "sold": false, "createdAt": "2025-01-01T00:00:00Z",
      "category": { "id": 2, "groupId": 10, "parentId": 0, "name": "手机" }
    }
  ]
}
```

---

### GET `/merchandise/cate`
Get merchandise under a category.

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int`, `sold?: boolean (default false)` |

**Response** `data: Merchandise[]`

---

### POST `/merchandise/`
Add merchandise (supports batch via IMEI list).

| | |
|---|---|
| Auth | Required |
| Params | `cate_id: int`, `cost: decimal`, `price: decimal`, `imei_list: string[]`, `create_time: string (ISO-8601 UTC)` |

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
| Params | `text: string`, `sold?: boolean (default false)` |

**Response** `data: MerchandiseWithCategoryDto[]` — each item is a `Merchandise` plus a nested `category` object (`null` if the category was since deleted)

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

### POST `/order`
Create a single order. Marks the referenced merchandise as `sold`.

| | |
|---|---|
| Auth | Required |
| Params | `me_id: int`, `selling_price: decimal`, `selling_time?: string (ISO-8601 UTC)`, `remark: string` |

**Response** `data: int` — new order ID

**Errors** `400` — `me_id` doesn't reference an existing merchandise in the current group, or that merchandise is already `sold`

---

### POST `/order/batch`
Batch create orders. Marks each referenced merchandise as `sold`.

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

**Errors** `400` — any order's `meId` doesn't reference an existing merchandise in the current group, that merchandise is already `sold`, or the same `meId` appears more than once in the batch

---

### GET `/order/range`
Get paginated orders within a time range.

| | |
|---|---|
| Auth | Required |
| Params | `start: string (ISO-8601 UTC)`, `end: string (ISO-8601 UTC)`, `limit: int (1–999)`, `offset: int (≥0)` |

**Response** `data: { count: int, orders: OrderListItemDto[] }` — each item is an `Order` plus a nested `merchandise` object (`null` if the merchandise record was since deleted), which itself nests a `category` object (`null` if the category was since deleted)
```json
{
  "count": 12,
  "orders": [
    {
      "id": 1, "groupId": 10, "meId": 2, "sellingPrice": "150.00", "sellingTime": "2025-01-01T00:00:00Z", "remark": "备注", "returned": false,
      "merchandise": {
        "id": 2, "groupId": 10, "cateId": 1, "cost": "100.00", "price": "150.00", "imei": "123456789", "sold": true, "createdAt": "2025-01-01T00:00:00Z",
        "category": { "id": 1, "groupId": 10, "parentId": 0, "name": "手机" }
      }
    }
  ]
}
```

---

### PUT `/order/return/{id}`
Mark an order as returned. Marks the referenced merchandise as not `sold` again.

| | |
|---|---|
| Auth | Required |
| Path | `id: int` |

**Response** `data: null`

**Errors** `400` — `id` doesn't reference an existing order in the current group

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
