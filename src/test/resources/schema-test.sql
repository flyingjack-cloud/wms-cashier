CREATE TABLE IF NOT EXISTS wms_user_profile (
    user_id     BIGINT PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    role        VARCHAR(32) NOT NULL DEFAULT 'ROLE_DEFAULT',
    nickname    VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS wms_authority (
    user_id     BIGINT NOT NULL,
    authority   VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, authority)
);

CREATE TABLE IF NOT EXISTS wms_group (
    id          SERIAL PRIMARY KEY,
    store_name  VARCHAR(128) NOT NULL,
    address     VARCHAR(256),
    contact     VARCHAR(32),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wms_join_request (
    user_id      BIGINT NOT NULL,
    group_id     INT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS wms_category (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    parent_id   INT NOT NULL DEFAULT 0,
    name        VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS wms_merchandise (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL,
    cate_id     INT NOT NULL,
    cost        NUMERIC(12,2) NOT NULL,
    price       NUMERIC(12,2) NOT NULL,
    imei        VARCHAR(64),
    sold        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS wms_order (
    id            SERIAL PRIMARY KEY,
    group_id      INT NOT NULL,
    me_id         INT NOT NULL,
    selling_price NUMERIC(12,2) NOT NULL,
    selling_time  TIMESTAMPTZ NOT NULL,
    remark        VARCHAR(256),
    is_returned   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS wms_notice (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    type        VARCHAR(32) NOT NULL,
    content     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
