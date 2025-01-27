CREATE TABLE IF NOT EXISTS "tenant" (
    "id"           bigserial PRIMARY KEY,
    "tenant_id"    varchar(128) DEFAULT NULL,
    "tenant_name"  varchar(128) DEFAULT NULL,
    "tenant_desc"  varchar(256) DEFAULT NULL,
    "owner"        varchar(32)  DEFAULT '-',
    "gmt_create"   timestamp DEFAULT NULL,
    "gmt_modified" timestamp DEFAULT NULL,
    "del_flag"     smallint DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS "item" (
    "id"           bigserial PRIMARY KEY,
    "tenant_id"    varchar(128) DEFAULT NULL,
    "item_id"      varchar(128) DEFAULT NULL,
    "item_name"    varchar(128) DEFAULT NULL,
    "item_desc"    varchar(256) DEFAULT NULL,
    "owner"        varchar(32)  DEFAULT NULL,
    "gmt_create"   timestamp DEFAULT NULL,
    "gmt_modified" timestamp DEFAULT NULL,
    "del_flag"     smallint DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS "config" (
    "id"                         bigserial PRIMARY KEY,
    "tenant_id"                  varchar(128) DEFAULT NULL,
    "item_id"                    varchar(128) DEFAULT NULL,
    "tp_id"                      varchar(256)  DEFAULT NULL,
    "tp_name"                    varchar(56)  DEFAULT NULL,
    "core_size"                  integer DEFAULT NULL,
    "max_size"                   integer DEFAULT NULL,
    "queue_type"                 integer DEFAULT NULL,
    "capacity"                   integer DEFAULT NULL,
    "rejected_type"              integer DEFAULT NULL,
    "keep_alive_time"            integer DEFAULT NULL,
    "execute_time_out"           integer DEFAULT NULL,
    "allow_core_thread_time_out" smallint DEFAULT NULL,
    "content"                    text,
    "md5"                        varchar(32) NOT NULL,
    "is_alarm"                   smallint DEFAULT NULL,
    "capacity_alarm"             integer DEFAULT NULL,
    "liveness_alarm"             integer DEFAULT NULL,
    "gmt_create"                 timestamp DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified"               timestamp DEFAULT CURRENT_TIMESTAMP,
    "del_flag"                   smallint DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS "inst_config" (
    "id"           bigserial PRIMARY KEY,
    "tenant_id"    varchar(128) DEFAULT NULL,
    "item_id"      varchar(128) DEFAULT NULL,
    "tp_id"        varchar(256)  DEFAULT NULL,
    "instance_id"  varchar(64) DEFAULT NULL,
    "content"      text,
    "md5"          varchar(32) NOT NULL,
    "gmt_create"   timestamp DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "his_run_data" (
    "id"                       bigserial PRIMARY KEY,
    "tenant_id"                varchar(128) DEFAULT NULL,
    "item_id"                  varchar(128) DEFAULT NULL,
    "tp_id"                    varchar(256)  DEFAULT NULL,
    "instance_id"              varchar(64) DEFAULT NULL,
    "current_load"             bigint DEFAULT NULL,
    "peak_load"                bigint DEFAULT NULL,
    "pool_size"                bigint DEFAULT NULL,
    "active_size"              bigint DEFAULT NULL,
    "queue_capacity"           bigint DEFAULT NULL,
    "queue_size"               bigint DEFAULT NULL,
    "queue_remaining_capacity" bigint DEFAULT NULL,
    "completed_task_count"     bigint DEFAULT NULL,
    "reject_count"             bigint DEFAULT NULL,
    "timestamp"                bigint DEFAULT NULL,
    "gmt_create"               timestamp DEFAULT NULL,
    "gmt_modified"             timestamp DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS "log_record_info" (
    "id"          bigserial PRIMARY KEY,
    "tenant"      varchar(128)  NOT NULL DEFAULT '',
    "biz_key"     varchar(128)  NOT NULL DEFAULT '',
    "biz_no"      varchar(128)  NOT NULL DEFAULT '',
    "operator"    varchar(64)   NOT NULL DEFAULT '',
    "action"      varchar(128)  NOT NULL DEFAULT '',
    "category"    varchar(128)  NOT NULL DEFAULT '',
    "detail"      varchar(2048) NOT NULL DEFAULT '',
    "create_time" timestamp      DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "user" (
    "id"           bigserial PRIMARY KEY,
    "user_name"    varchar(64)  NOT NULL,
    "password"     varchar(512) NOT NULL,
    "role"         varchar(50)  NOT NULL,
    "gmt_create"   timestamp     NOT NULL,
    "gmt_modified" timestamp     NOT NULL,
    "del_flag"     smallint NOT NULL
);

CREATE TABLE IF NOT EXISTS "role" (
    "id"           bigserial PRIMARY KEY,
    "role"         varchar(64) NOT NULL,
    "user_name"    varchar(64) NOT NULL,
    "gmt_create"   timestamp    NOT NULL,
    "gmt_modified" timestamp    NOT NULL,
    "del_flag"     smallint NOT NULL
);

CREATE TABLE IF NOT EXISTS "permission" (
    "id"           bigserial PRIMARY KEY,
    "role"         varchar(512) NOT NULL,
    "resource"     varchar(512) NOT NULL,
    "action"       varchar(8)   NOT NULL,
    "gmt_create"   timestamp     NOT NULL,
    "gmt_modified" timestamp     NOT NULL,
    "del_flag"     smallint NOT NULL
);

CREATE TABLE IF NOT EXISTS "notify" (
    "id"           bigserial PRIMARY KEY,
    "tenant_id"    varchar(128) NOT NULL DEFAULT '',
    "item_id"      varchar(128) NOT NULL,
    "tp_id"        varchar(256) NOT NULL,
    "platform"     varchar(32)  NOT NULL,
    "type"         varchar(32)  NOT NULL,
    "secret_key"   varchar(256) NOT NULL,
    "interval"     integer DEFAULT NULL,
    "receives"     varchar(512) NOT NULL,
    "enable"       smallint DEFAULT NULL,
    "gmt_create"   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "del_flag"     smallint NOT NULL
);

CREATE TABLE IF NOT EXISTS "his_config_verify" (
    "id"            bigserial PRIMARY KEY,
    "type"          integer DEFAULT NULL,
    "mark"          varchar(128) DEFAULT NULL,
    "tenant_id"     varchar(128) DEFAULT NULL,
    "item_id"       varchar(128) DEFAULT NULL,
    "tp_id"         varchar(256) DEFAULT NULL,
    "identify"      varchar(64)  DEFAULT NULL,
    "content"       text,
    "modify_all"    smallint DEFAULT NULL,
    "gmt_create"    timestamp DEFAULT NULL,
    "modify_user"   varchar(128) DEFAULT NULL,
    "verify_status" smallint DEFAULT NULL,
    "gmt_verify"    timestamp DEFAULT NULL,
    "verify_user"   varchar(128) DEFAULT NULL
);

INSERT INTO "tenant" ("id", "tenant_id", "tenant_name", "tenant_desc", "owner", "gmt_create", "gmt_modified", "del_flag") VALUES ('1', 'prescription', '处方组', '负责维护处方服务, 包括不限于电子处方等业务', '谢良辰', '2021-10-24 13:42:11', '2021-10-24 13:42:11', '0');

INSERT INTO "item" ("id", "tenant_id", "item_id", "item_name", "item_desc", "owner", "gmt_create", "gmt_modified", "del_flag") VALUES ('1', 'prescription', 'dynamic-threadpool-example', '动态线程池示例项目', '动态线程池示例项目，对应 Hippo 项目的 example 模块', '马称', '2021-10-24 16:11:00', '2021-10-24 16:11:00', '0');

INSERT INTO "config"("id","tenant_id","item_id","tp_id","tp_name","core_size","max_size","queue_type","capacity","rejected_type","keep_alive_time","allow_core_thread_time_out","content","md5","is_alarm","capacity_alarm","liveness_alarm","gmt_create","gmt_modified","del_flag")
VALUES ('1', 'prescription', 'dynamic-threadpool-example', 'message-consume', '示例消费者线程池', '5', '10', '9', '1024', '2', '9999', '0', '{\"tenantId\":\"prescription\",\"itemId\":\"dynamic-threadpool-example\",\"tpId\":\"message-consume\",\"coreSize\":5,\"maxSize\":10,\"queueType\":9,\"capacity\":1024,\"keepAliveTime\":9999,\"rejectedType\":2,\"isAlarm\":0,\"capacityAlarm\":80,\"livenessAlarm\":80,\"allowCoreThreadTimeOut\":0}', 'f80ea89044889fb6cec20e1a517f2ec3', '0', '80', '80', '2021-10-24 10:24:00', '2021-12-22 08:58:55', '0');

INSERT INTO "config"("id","tenant_id","item_id","tp_id","tp_name","core_size","max_size","queue_type","capacity","rejected_type","keep_alive_time","allow_core_thread_time_out","content","md5","is_alarm","capacity_alarm","liveness_alarm","gmt_create","gmt_modified","del_flag")
VALUES ('2', 'prescription', 'dynamic-threadpool-example', 'message-produce', '示例生产者线程池', '5', '15', '9', '1024', '1', '9999', '0', '{\"tenantId\":\"prescription\",\"itemId\":\"dynamic-threadpool-example\",\"tpId\":\"message-produce\",\"coreSize\":5,\"maxSize\":15,\"queueType\":9,\"capacity\":1024,\"keepAliveTime\":9999,\"rejectedType\":1,\"isAlarm\":0,\"capacityAlarm\":30,\"livenessAlarm\":30,\"allowCoreThreadTimeOut\":0}', '525e1429468bcfe98df7e70a75710051', '0', '30', '30', '2021-10-24 10:24:00', '2021-12-22 08:59:02', '0');
INSERT INTO "user" ("id", "user_name", "password", "role", "gmt_create", "gmt_modified", "del_flag") VALUES ('1', 'admin', '$2a$10$2KCqRbra0Yn2TwvkZxtfLuWuUP5KyCWsljO/ci5pLD27pqR3TV1vy', 'ROLE_ADMIN', '2021-11-04 21:35:17', '2021-11-15 23:04:59', '0');

INSERT INTO "notify" ("id", "tenant_id", "item_id", "tp_id", "platform", "type", "secret_key", "interval", "receives", "enable", "gmt_create", "gmt_modified", "del_flag") VALUES ('1', 'prescription', 'dynamic-threadpool-example', 'message-produce', 'DING', 'CONFIG', '4a582a588a161d6e3a1bd1de7eea9ee9f562cdfcbe56b6e72029e7fd512b2eae', NULL, '15601166691', '0', '2021-11-18 22:49:50', '2021-11-18 22:49:50', '0'), ('2', 'prescription', 'dynamic-threadpool-example', 'message-produce', 'DING', 'ALARM', '4a582a588a161d6e3a1bd1de7eea9ee9f562cdfcbe56b6e72029e7fd512b2eae', '30', '15601166691', '0', '2021-11-18 22:50:06', '2021-11-18 22:50:06', '0');