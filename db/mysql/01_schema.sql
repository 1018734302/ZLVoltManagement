-- ============================================================
-- ZL-Volt 光伏电站全生命周期管理平台
-- MySQL 8.0 MVP Schema
-- 说明：
--   1. 生产环境按微服务拆库；本脚本创建 9 个业务库，表归属与 Spec 7.1 一致。
--   2. 不建跨库外键，服务间通过 ID 逻辑关联。
--   3. 所有金额 DECIMAL(18,2)，数量 DECIMAL(18,4)，税率 DECIMAL(6,4)。
--   4. 业务单据禁止物理删除，统一 deleted + status。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 系统库 zl_system
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_system;

DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;

CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0为根',
    code        VARCHAR(32)  NOT NULL COMMENT '部门编码',
    name        VARCHAR(64)  NOT NULL COMMENT '部门名称',
    type        VARCHAR(16)  NOT NULL DEFAULT 'DEPT' COMMENT 'GROUP/COMPANY/DEPT',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (code),
    KEY idx_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织部门';

CREATE TABLE sys_user (
    id            BIGINT       NOT NULL COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希；开发种子使用 {noop} 前缀',
    real_name     VARCHAR(64)  NOT NULL COMMENT '姓名',
    phone         VARCHAR(32)  NULL COMMENT '手机号',
    email         VARCHAR(128) NULL COMMENT '邮箱',
    dept_id       BIGINT       NULL COMMENT '部门ID',
    supplier_id   BIGINT       NULL COMMENT '供应商ID（供应商门户账号）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/LOCKED',
    last_login_at DATETIME     NULL COMMENT '最后登录时间',
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_dept (dept_id),
    KEY idx_user_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL COMMENT '主键',
    code        VARCHAR(32)  NOT NULL COMMENT '角色编码',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    data_scope  VARCHAR(32)  NOT NULL DEFAULT 'SELF' COMMENT 'ALL/DEPT/DEPT_AND_CHILD/SELF/PROJECT/SUPPLIER',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    remark      VARCHAR(255) NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    role_id     BIGINT   NOT NULL COMMENT '角色ID',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_ur_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';

-- ============================================================
-- 2. CRM 库 zl_crm
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_crm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_crm;

DROP TABLE IF EXISTS crm_contract;
DROP TABLE IF EXISTS crm_bid;
DROP TABLE IF EXISTS crm_opportunity;
DROP TABLE IF EXISTS crm_lead;
DROP TABLE IF EXISTS crm_contact;
DROP TABLE IF EXISTS crm_customer;

CREATE TABLE crm_customer (
    id            BIGINT       NOT NULL COMMENT '主键',
    code          VARCHAR(32)  NOT NULL COMMENT '客户编码',
    name          VARCHAR(128) NOT NULL COMMENT '客户名称',
    type          VARCHAR(32)  NOT NULL COMMENT 'EPC/INVESTOR/ROOFTOP_OWNER/PARTNER',
    level         VARCHAR(16)  NOT NULL DEFAULT 'B' COMMENT 'A/B/C',
    region        VARCHAR(64)  NULL COMMENT '区域',
    address       VARCHAR(255) NULL COMMENT '地址',
    credit_code   VARCHAR(32)  NULL COMMENT '统一社会信用代码',
    contact_name  VARCHAR(64)  NULL COMMENT '主联系人',
    contact_phone VARCHAR(32)  NULL COMMENT '联系电话',
    owner_id      BIGINT       NOT NULL COMMENT '销售负责人',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    remark        VARCHAR(512) NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (code),
    UNIQUE KEY uk_customer_credit (credit_code),
    KEY idx_customer_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户';

CREATE TABLE crm_contact (
    id          BIGINT       NOT NULL COMMENT '主键',
    customer_id BIGINT       NOT NULL COMMENT '客户ID',
    name        VARCHAR(64)  NOT NULL COMMENT '姓名',
    position    VARCHAR(64)  NULL COMMENT '职位',
    phone       VARCHAR(32)  NULL COMMENT '电话',
    email       VARCHAR(128) NULL COMMENT '邮箱',
    is_primary  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主联系人',
    remark      VARCHAR(255) NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_contact_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联系人';

CREATE TABLE crm_lead (
    id                    BIGINT        NOT NULL COMMENT '主键',
    code                  VARCHAR(32)   NOT NULL COMMENT '线索编码',
    name                  VARCHAR(128)  NOT NULL COMMENT '线索名称',
    customer_id           BIGINT        NULL COMMENT '客户ID（可空）',
    source                VARCHAR(32)   NULL COMMENT '来源',
    estimated_capacity_mw DECIMAL(12,4) NULL COMMENT '预估容量MW',
    estimated_amount      DECIMAL(18,2) NULL COMMENT '预估金额',
    owner_id              BIGINT        NOT NULL COMMENT '负责人',
    stage                 VARCHAR(32)   NOT NULL DEFAULT 'NEW' COMMENT 'NEW/FOLLOWING/QUALIFIED/CONVERTED/LOST',
    next_action           VARCHAR(255)  NULL COMMENT '下一步动作',
    status                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    remark                VARCHAR(512)  NULL,
    created_by            BIGINT        NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            BIGINT        NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    version               INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lead_code (code),
    KEY idx_lead_customer (customer_id),
    KEY idx_lead_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线索';

CREATE TABLE crm_opportunity (
    id                  BIGINT        NOT NULL COMMENT '主键',
    code                VARCHAR(32)   NOT NULL COMMENT '商机编码',
    lead_id             BIGINT        NULL COMMENT '线索ID',
    customer_id         BIGINT        NOT NULL COMMENT '客户ID',
    name                VARCHAR(128)  NOT NULL COMMENT '商机名称',
    capacity_mw         DECIMAL(12,4) NULL COMMENT '容量MW',
    amount              DECIMAL(18,2) NULL COMMENT '商机金额',
    win_rate            DECIMAL(5,2)  NOT NULL DEFAULT 0 COMMENT '赢率%',
    stage               VARCHAR(32)   NOT NULL DEFAULT 'QUALIFYING' COMMENT 'QUALIFYING/PROPOSAL/BIDDING/NEGOTIATION/WON/LOST',
    expected_sign_date  DATE          NULL COMMENT '预计签约日期',
    owner_id            BIGINT        NOT NULL COMMENT '负责人',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    remark              VARCHAR(512)  NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT        NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_opp_code (code),
    KEY idx_opp_customer (customer_id),
    KEY idx_opp_lead (lead_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机';

CREATE TABLE crm_bid (
    id             BIGINT        NOT NULL COMMENT '主键',
    code           VARCHAR(32)   NOT NULL COMMENT '投标编码',
    opportunity_id BIGINT        NOT NULL COMMENT '商机ID',
    bid_no         VARCHAR(64)   NULL COMMENT '标书编号',
    bid_date       DATE          NULL COMMENT '投标日期',
    bid_amount     DECIMAL(18,2) NULL COMMENT '投标金额',
    deposit_amount DECIMAL(18,2) NULL COMMENT '投标保证金',
    result         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/WIN/LOSE/CANCEL',
    remark         VARCHAR(512)  NULL,
    created_by     BIGINT        NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT        NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    version        INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_code (code),
    KEY idx_bid_opp (opportunity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投标';

CREATE TABLE crm_contract (
    id              BIGINT        NOT NULL COMMENT '主键',
    code            VARCHAR(32)   NOT NULL COMMENT '合同编码',
    customer_id     BIGINT        NOT NULL COMMENT '客户ID',
    opportunity_id  BIGINT        NULL COMMENT '商机ID',
    name            VARCHAR(128)  NOT NULL COMMENT '合同名称',
    type            VARCHAR(32)   NOT NULL COMMENT 'EPC/OM/SUPPLY',
    amount          DECIMAL(18,2) NOT NULL COMMENT '合同金额',
    tax_rate        DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率',
    sign_date       DATE          NULL COMMENT '签订日期',
    start_date      DATE          NULL COMMENT '开始日期',
    end_date        DATE          NULL COMMENT '结束日期',
    payment_terms   VARCHAR(512)  NULL COMMENT '付款条款',
    retention_rate  DECIMAL(6,4)  NOT NULL DEFAULT 0.0500 COMMENT '质保金比例',
    warranty_months INT           NOT NULL DEFAULT 12 COMMENT '质保期月数',
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/EFFECTIVE/TERMINATED/COMPLETED',
    attachment_url  VARCHAR(512)  NULL COMMENT '合同附件',
    remark          VARCHAR(512)  NULL,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_code (code),
    KEY idx_contract_customer (customer_id),
    KEY idx_contract_opp (opportunity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售合同';

-- ============================================================
-- 3. 项目管理库 zl_pm
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_pm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_pm;

DROP TABLE IF EXISTS pm_cost_item;
DROP TABLE IF EXISTS pm_change_order;
DROP TABLE IF EXISTS pm_milestone;
DROP TABLE IF EXISTS pm_wbs;
DROP TABLE IF EXISTS pm_project;

CREATE TABLE pm_project (
    id                  BIGINT        NOT NULL COMMENT '主键',
    code                VARCHAR(32)   NOT NULL COMMENT '项目编码',
    name                VARCHAR(128)  NOT NULL COMMENT '项目名称',
    contract_id         BIGINT        NULL COMMENT '销售合同ID',
    customer_id         BIGINT        NULL COMMENT '客户ID',
    type                VARCHAR(32)   NOT NULL COMMENT 'CENTRALIZED/DISTRIBUTED/ROOFTOP',
    capacity_mw         DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '装机容量MW',
    location            VARCHAR(255)  NULL COMMENT '项目地点',
    manager_id          BIGINT        NULL COMMENT '项目经理',
    start_date          DATE          NULL COMMENT '开工日期',
    planned_grid_date   DATE          NULL COMMENT '计划并网日期',
    actual_grid_date    DATE          NULL COMMENT '实际并网日期',
    budget_amount       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '项目预算',
    actual_cost         DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际成本快照',
    status              VARCHAR(32)   NOT NULL DEFAULT 'INITIATED' COMMENT 'INITIATED/APPROVING/IN_PROGRESS/GRID_CONNECTED/SETTLING/SETTLED/CLOSED',
    remark              VARCHAR(512)  NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT        NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (code),
    KEY idx_project_contract (contract_id),
    KEY idx_project_customer (customer_id),
    KEY idx_project_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

CREATE TABLE pm_wbs (
    id            BIGINT        NOT NULL COMMENT '主键',
    project_id    BIGINT        NOT NULL COMMENT '项目ID',
    parent_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '父WBS',
    code          VARCHAR(32)   NOT NULL COMMENT 'WBS编码',
    name          VARCHAR(128)  NOT NULL COMMENT 'WBS名称',
    type          VARCHAR(32)   NULL COMMENT 'COST/PAYMENT/SCHEDULE',
    budget_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预算金额',
    owner_id      BIGINT        NULL COMMENT '负责人',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_wbs_project (project_id),
    KEY idx_wbs_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目WBS';

CREATE TABLE pm_milestone (
    id          BIGINT       NOT NULL COMMENT '主键',
    project_id  BIGINT       NOT NULL COMMENT '项目ID',
    name        VARCHAR(128) NOT NULL COMMENT '里程碑名称',
    plan_date   DATE         NULL COMMENT '计划日期',
    actual_date DATE         NULL COMMENT '实际日期',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/DELAYED',
    sort        INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ms_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目里程碑';

CREATE TABLE pm_change_order (
    id                   BIGINT        NOT NULL COMMENT '主键',
    code                 VARCHAR(32)   NOT NULL COMMENT '变更单号',
    project_id           BIGINT        NOT NULL COMMENT '项目ID',
    wbs_id               BIGINT        NULL COMMENT 'WBS ID',
    type                 VARCHAR(32)   NOT NULL COMMENT 'DESIGN/QUANTITY/SCHEDULE/OTHER',
    amount_delta         DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额增减',
    schedule_delta_days  INT           NOT NULL DEFAULT 0 COMMENT '工期影响天数',
    reason               VARCHAR(512)  NULL COMMENT '变更原因',
    status               VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
    approval_instance_id BIGINT        NULL COMMENT '审批实例',
    created_by           BIGINT        NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT        NULL,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_change_code (code),
    KEY idx_change_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更签证';

CREATE TABLE pm_cost_item (
    id            BIGINT        NOT NULL COMMENT '主键',
    project_id    BIGINT        NOT NULL COMMENT '项目ID',
    wbs_id        BIGINT        NULL COMMENT 'WBS ID',
    cost_type     VARCHAR(32)   NOT NULL COMMENT 'MATERIAL/LABOR/MACHINERY/SUBCONTRACT/OTHER',
    source_type   VARCHAR(32)   NOT NULL COMMENT 'ISSUE/SUBCONTRACT_SETTLEMENT/PAYROLL/EXPENSE',
    source_id     BIGINT        NULL COMMENT '来源单据ID',
    amount        DECIMAL(18,2) NOT NULL COMMENT '成本金额',
    occurred_date DATE          NOT NULL COMMENT '发生日期',
    remark        VARCHAR(512)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cost_project (project_id),
    KEY idx_cost_wbs (wbs_id),
    KEY idx_cost_type (cost_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成本明细';

-- ============================================================
-- 4. SRM 库 zl_srm
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_srm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_srm;

DROP TABLE IF EXISTS srm_delivery;
DROP TABLE IF EXISTS srm_po_line;
DROP TABLE IF EXISTS srm_purchase_order;
DROP TABLE IF EXISTS srm_material;
DROP TABLE IF EXISTS srm_supplier;

CREATE TABLE srm_supplier (
    id             BIGINT        NOT NULL COMMENT '主键',
    code           VARCHAR(32)   NOT NULL COMMENT '供应商编码',
    name           VARCHAR(128)  NOT NULL COMMENT '供应商名称',
    type           VARCHAR(32)   NOT NULL COMMENT 'MODULE/INVERTER/BRACKET/CABLE/CONSTRUCTION/OTHER',
    level          VARCHAR(16)   NOT NULL DEFAULT 'B' COMMENT 'A/B/C',
    credit_code    VARCHAR(32)   NULL COMMENT '统一社会信用代码',
    contact_name   VARCHAR(64)   NULL COMMENT '联系人',
    contact_phone  VARCHAR(32)   NULL COMMENT '联系电话',
    bank_name      VARCHAR(128)  NULL COMMENT '开户银行',
    bank_account   VARCHAR(64)   NULL COMMENT '银行账号',
    tax_no         VARCHAR(64)   NULL COMMENT '税号',
    address        VARCHAR(255)  NULL COMMENT '地址',
    payment_terms  VARCHAR(255)  NULL COMMENT '付款条件',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/QUALIFIED/SUSPENDED/BLACKLIST',
    remark         VARCHAR(512)  NULL,
    created_by     BIGINT        NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT        NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    version        INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (code),
    UNIQUE KEY uk_supplier_credit (credit_code),
    KEY idx_supplier_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商';

CREATE TABLE srm_material (
    id            BIGINT        NOT NULL COMMENT '主键',
    code          VARCHAR(32)   NOT NULL COMMENT '物料编码',
    name          VARCHAR(128)  NOT NULL COMMENT '物料名称',
    category      VARCHAR(32)   NOT NULL COMMENT 'MODULE/INVERTER/BRACKET/CABLE/COMBINER/TRANSFORMER/OTHER',
    spec          VARCHAR(255)  NULL COMMENT '规格型号',
    unit          VARCHAR(16)   NOT NULL COMMENT '单位',
    tax_rate      DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率',
    safety_stock  DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '安全库存',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_code (code),
    KEY idx_material_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料主数据';

CREATE TABLE srm_purchase_order (
    id                  BIGINT        NOT NULL COMMENT '主键',
    code                VARCHAR(32)   NOT NULL COMMENT '采购订单号',
    project_id          BIGINT        NOT NULL COMMENT '项目ID',
    supplier_id         BIGINT        NOT NULL COMMENT '供应商ID',
    contract_id         BIGINT        NULL COMMENT '关联合同',
    order_date          DATE          NOT NULL COMMENT '下单日期',
    delivery_date       DATE          NULL COMMENT '要求到货日期',
    total_amount        DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '含税总金额',
    tax_amount          DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    currency            VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    payment_terms       VARCHAR(512)  NULL COMMENT '付款条款',
    status              VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED',
    approval_instance_id BIGINT       NULL COMMENT '审批实例',
    remark              VARCHAR(512)  NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT        NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_code (code),
    KEY idx_po_project (project_id),
    KEY idx_po_supplier (supplier_id),
    KEY idx_po_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单';

CREATE TABLE srm_po_line (
    id           BIGINT        NOT NULL COMMENT '主键',
    po_id        BIGINT        NOT NULL COMMENT '采购订单ID',
    material_id  BIGINT        NOT NULL COMMENT '物料ID',
    qty          DECIMAL(18,4) NOT NULL COMMENT '采购数量',
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已收货数量',
    unit_price   DECIMAL(18,4) NOT NULL COMMENT '含税单价',
    tax_rate     DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率',
    amount       DECIMAL(18,2) NOT NULL COMMENT '含税金额',
    delivery_date DATE         NULL COMMENT '行交期',
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_poline_po (po_id),
    KEY idx_poline_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单行';

CREATE TABLE srm_delivery (
    id            BIGINT       NOT NULL COMMENT '主键',
    code          VARCHAR(32)  NOT NULL COMMENT '发货单号',
    po_id         BIGINT       NOT NULL COMMENT '采购订单ID',
    supplier_id   BIGINT       NOT NULL COMMENT '供应商ID',
    delivery_date DATE         NULL COMMENT '发货日期',
    logistics_no  VARCHAR(64)  NULL COMMENT '物流单号',
    status        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SHIPPED/ARRIVED/RECEIVED/CANCELLED',
    remark        VARCHAR(512) NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_code (code),
    KEY idx_delivery_po (po_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货通知ASN';

-- ============================================================
-- 5. 库存库 zl_inv
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_inv DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_inv;

DROP TABLE IF EXISTS inv_stock_movement;
DROP TABLE IF EXISTS inv_issue_line;
DROP TABLE IF EXISTS inv_issue;
DROP TABLE IF EXISTS inv_receipt_line;
DROP TABLE IF EXISTS inv_receipt;
DROP TABLE IF EXISTS inv_stock;
DROP TABLE IF EXISTS inv_warehouse;

CREATE TABLE inv_warehouse (
    id          BIGINT       NOT NULL COMMENT '主键',
    code        VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    name        VARCHAR(64)  NOT NULL COMMENT '仓库名称',
    project_id  BIGINT       NULL COMMENT '所属项目（项目仓）',
    location    VARCHAR(255) NULL COMMENT '仓库地址',
    manager_id  BIGINT       NULL COMMENT '仓管员',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库';

CREATE TABLE inv_stock (
    id           BIGINT        NOT NULL COMMENT '主键',
    warehouse_id BIGINT        NOT NULL COMMENT '仓库ID',
    material_id  BIGINT        NOT NULL COMMENT '物料ID',
    qty          DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '现存量',
    locked_qty   DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '锁定数量',
    avg_cost     DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '移动加权平均单价',
    amount       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '库存金额',
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_wh_material (warehouse_id, material_id),
    KEY idx_stock_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存';

CREATE TABLE inv_receipt (
    id            BIGINT        NOT NULL COMMENT '主键',
    code          VARCHAR(32)   NOT NULL COMMENT '收货单号',
    po_id         BIGINT        NOT NULL COMMENT '采购订单ID',
    delivery_id   BIGINT        NULL COMMENT '发货单ID',
    warehouse_id  BIGINT        NOT NULL COMMENT '收货仓库',
    project_id    BIGINT        NOT NULL COMMENT '项目ID',
    receipt_date  DATE          NOT NULL COMMENT '收货日期',
    inspector_id  BIGINT        NULL COMMENT '质检员',
    status        VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/INSPECTING/QUALIFIED/REJECTED/STORED',
    total_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收货金额',
    remark        VARCHAR(512)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_code (code),
    KEY idx_receipt_po (po_id),
    KEY idx_receipt_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

CREATE TABLE inv_receipt_line (
    id            BIGINT        NOT NULL COMMENT '主键',
    receipt_id    BIGINT        NOT NULL COMMENT '收货单ID',
    po_line_id    BIGINT        NOT NULL COMMENT '采购订单行ID',
    material_id   BIGINT        NOT NULL COMMENT '物料ID',
    qty           DECIMAL(18,4) NOT NULL COMMENT '收货数量',
    qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    unit_price    DECIMAL(18,4) NOT NULL COMMENT '含税单价',
    amount        DECIMAL(18,2) NOT NULL COMMENT '含税金额',
    batch_no      VARCHAR(64)   NULL COMMENT '批次号',
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_receiptline_receipt (receipt_id),
    KEY idx_receiptline_poline (po_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货明细';

CREATE TABLE inv_issue (
    id           BIGINT        NOT NULL COMMENT '主键',
    code         VARCHAR(32)   NOT NULL COMMENT '出库单号',
    project_id   BIGINT        NOT NULL COMMENT '项目ID',
    wbs_id       BIGINT        NULL COMMENT 'WBS ID',
    warehouse_id BIGINT        NOT NULL COMMENT '仓库ID',
    issue_date   DATE          NOT NULL COMMENT '出库日期',
    type         VARCHAR(32)   NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/MAINTENANCE/OTHER',
    status       VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CANCELLED',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '出库金额',
    remark       VARCHAR(512)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_code (code),
    KEY idx_issue_project (project_id),
    KEY idx_issue_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单';

CREATE TABLE inv_issue_line (
    id          BIGINT        NOT NULL COMMENT '主键',
    issue_id    BIGINT        NOT NULL COMMENT '出库单ID',
    material_id BIGINT        NOT NULL COMMENT '物料ID',
    qty         DECIMAL(18,4) NOT NULL COMMENT '出库数量',
    unit_cost   DECIMAL(18,4) NOT NULL COMMENT '单位成本',
    amount      DECIMAL(18,2) NOT NULL COMMENT '出库金额',
    batch_no    VARCHAR(64)   NULL COMMENT '批次号',
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_issueline_issue (issue_id),
    KEY idx_issueline_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库明细';

CREATE TABLE inv_stock_movement (
    id           BIGINT        NOT NULL COMMENT '主键',
    warehouse_id BIGINT        NOT NULL COMMENT '仓库ID',
    material_id  BIGINT        NOT NULL COMMENT '物料ID',
    biz_type     VARCHAR(32)   NOT NULL COMMENT 'RECEIPT/ISSUE/ADJUST/RETURN',
    biz_id       BIGINT        NULL COMMENT '业务单据ID',
    biz_code     VARCHAR(32)   NULL COMMENT '业务单号',
    qty_delta    DECIMAL(18,4) NOT NULL COMMENT '数量增减',
    unit_cost    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '单位成本',
    amount       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额',
    occurred_at  DATETIME      NOT NULL COMMENT '发生时间',
    remark       VARCHAR(512)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_move_wh_material (warehouse_id, material_id),
    KEY idx_move_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

-- ============================================================
-- 6. 财务结算库 zl_fin
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_fin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_fin;

DROP TABLE IF EXISTS fin_payment_allocation;
DROP TABLE IF EXISTS fin_payable;
DROP TABLE IF EXISTS fin_invoice;
DROP TABLE IF EXISTS fin_reconciliation_line;
DROP TABLE IF EXISTS fin_reconciliation;

CREATE TABLE fin_reconciliation (
    id                 BIGINT        NOT NULL COMMENT '主键',
    code               VARCHAR(32)   NOT NULL COMMENT '对账单号',
    supplier_id        BIGINT        NOT NULL COMMENT '供应商ID',
    project_id         BIGINT        NOT NULL COMMENT '项目ID',
    period             VARCHAR(7)    NOT NULL COMMENT '对账期间 yyyy-MM',
    start_date         DATE          NOT NULL COMMENT '开始日期',
    end_date           DATE          NOT NULL COMMENT '结束日期',
    total_qty          DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '对账总数量',
    total_amount       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '对账总金额',
    tax_amount         DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    diff_amount        DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '差异金额',
    status             VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUPPLIER_CONFIRMED/BUYER_CONFIRMED/FINANCE_CONFIRMED/INVOICED/CLOSED/VOID/DISPUTED',
    supplier_confirm_at DATETIME     NULL,
    buyer_confirm_at   DATETIME      NULL,
    finance_confirm_at DATETIME      NULL,
    remark             VARCHAR(512)  NULL,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_code (code),
    KEY idx_rec_supplier (supplier_id),
    KEY idx_rec_project (project_id),
    KEY idx_rec_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商对账单';

CREATE TABLE fin_reconciliation_line (
    id                 BIGINT        NOT NULL COMMENT '主键',
    reconciliation_id  BIGINT        NOT NULL COMMENT '对账单ID',
    po_id              BIGINT        NOT NULL COMMENT '采购订单ID',
    po_line_id         BIGINT        NOT NULL COMMENT '采购订单行ID',
    receipt_id         BIGINT        NOT NULL COMMENT '收货单ID',
    material_id        BIGINT        NOT NULL COMMENT '物料ID',
    qty                DECIMAL(18,4) NOT NULL COMMENT '对账数量',
    unit_price         DECIMAL(18,4) NOT NULL COMMENT '对账单价',
    amount             DECIMAL(18,2) NOT NULL COMMENT '对账金额',
    diff_qty           DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '数量差异',
    diff_amount        DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额差异',
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_recline_rec (reconciliation_id),
    KEY idx_recline_po (po_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账明细';

CREATE TABLE fin_invoice (
    id                 BIGINT        NOT NULL COMMENT '主键',
    code               VARCHAR(32)   NOT NULL COMMENT '发票登记号',
    invoice_no         VARCHAR(64)   NOT NULL COMMENT '发票号码',
    invoice_type       VARCHAR(32)   NOT NULL COMMENT 'VAT_SPECIAL/VAT_NORMAL/OTHER',
    supplier_id        BIGINT        NOT NULL COMMENT '供应商ID',
    reconciliation_id  BIGINT        NULL COMMENT '对账单ID',
    invoice_date       DATE          NOT NULL COMMENT '开票日期',
    amount             DECIMAL(18,2) NOT NULL COMMENT '含税金额',
    tax_amount         DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    tax_rate           DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率',
    status             VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/MATCHED/MISMATCH/VERIFIED/VOID',
    verify_result      VARCHAR(255)  NULL COMMENT '查验结果',
    match_result       VARCHAR(32)   NULL COMMENT 'MATCHED/MISMATCH/PARTIAL',
    remark             VARCHAR(512)  NULL,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_code (code),
    UNIQUE KEY uk_invoice_no (invoice_no),
    KEY idx_invoice_supplier (supplier_id),
    KEY idx_invoice_rec (reconciliation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票登记';

CREATE TABLE fin_payable (
    id                BIGINT        NOT NULL COMMENT '主键',
    code              VARCHAR(32)   NOT NULL COMMENT '应付单号',
    supplier_id       BIGINT        NOT NULL COMMENT '供应商ID',
    project_id        BIGINT        NOT NULL COMMENT '项目ID',
    source_type       VARCHAR(32)   NOT NULL COMMENT 'RECONCILIATION/SUBCONTRACT/EXPENSE',
    source_id         BIGINT        NULL COMMENT '来源单据ID',
    amount            DECIMAL(18,2) NOT NULL COMMENT '应付金额',
    paid_amount       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已付金额',
    prepaid_offset    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预付款抵扣',
    retention_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '质保金',
    available_amount  DECIMAL(18,2) NOT NULL COMMENT '可用余额',
    due_date          DATE          NULL COMMENT '到期日',
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PARTIAL/PAID/FROZEN/CLOSED',
    remark            VARCHAR(512)  NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    version           INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payable_code (code),
    KEY idx_payable_supplier (supplier_id),
    KEY idx_payable_project (project_id),
    KEY idx_payable_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付账款';

CREATE TABLE fin_payment_allocation (
    id          BIGINT        NOT NULL COMMENT '主键',
    payment_id  BIGINT        NOT NULL COMMENT '付款记录ID',
    payable_id  BIGINT        NOT NULL COMMENT '应付ID',
    amount      DECIMAL(18,2) NOT NULL COMMENT '核销金额',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alloc_payment_payable (payment_id, payable_id),
    KEY idx_alloc_payable (payable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款核销';

-- ============================================================
-- 7. 资金库 zl_fund
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_fund;

DROP TABLE IF EXISTS fund_payment_record;
DROP TABLE IF EXISTS fund_payment_request;
DROP TABLE IF EXISTS fund_plan;
DROP TABLE IF EXISTS fund_account;

CREATE TABLE fund_account (
    id          BIGINT        NOT NULL COMMENT '主键',
    code        VARCHAR(32)   NOT NULL COMMENT '账户编码',
    name        VARCHAR(64)   NOT NULL COMMENT '账户名称',
    bank_name   VARCHAR(128)  NOT NULL COMMENT '开户银行',
    account_no  VARCHAR(64)   NOT NULL COMMENT '银行账号',
    balance     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '账户余额',
    status      VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    remark      VARCHAR(255)  NULL,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_account_code (code),
    UNIQUE KEY uk_fund_account_no (account_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金账户';

CREATE TABLE fund_plan (
    id              BIGINT        NOT NULL COMMENT '主键',
    code            VARCHAR(32)   NOT NULL COMMENT '计划编码',
    period          VARCHAR(7)    NOT NULL COMMENT '期间 yyyy-MM',
    project_id      BIGINT        NULL COMMENT '项目ID',
    planned_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '计划金额',
    approved_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '批准金额',
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/CLOSED',
    remark          VARCHAR(512)  NULL,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_plan_code (code),
    KEY idx_plan_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金计划';

CREATE TABLE fund_payment_request (
    id                   BIGINT        NOT NULL COMMENT '主键',
    code                 VARCHAR(32)   NOT NULL COMMENT '付款申请号',
    payable_id           BIGINT        NOT NULL COMMENT '应付ID',
    supplier_id          BIGINT        NOT NULL COMMENT '供应商ID',
    project_id           BIGINT        NOT NULL COMMENT '项目ID',
    amount               DECIMAL(18,2) NOT NULL COMMENT '申请金额',
    payment_type         VARCHAR(32)   NOT NULL COMMENT 'ADVANCE/PROGRESS/FINAL/RETENTION',
    account_id           BIGINT        NOT NULL COMMENT '付款账户',
    apply_date           DATE          NOT NULL COMMENT '申请日期',
    status               VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/PAYING/PAID/FAILED/RECONCILED',
    approval_instance_id BIGINT        NULL COMMENT '审批实例',
    request_id           VARCHAR(64)   NOT NULL COMMENT '幂等键',
    remark               VARCHAR(512)  NULL,
    created_by           BIGINT        NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT        NULL,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payreq_code (code),
    UNIQUE KEY uk_payreq_request (request_id),
    KEY idx_payreq_payable (payable_id),
    KEY idx_payreq_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款申请';

CREATE TABLE fund_payment_record (
    id                  BIGINT        NOT NULL COMMENT '主键',
    code                VARCHAR(32)   NOT NULL COMMENT '付款记录号',
    payment_request_id  BIGINT        NOT NULL COMMENT '付款申请ID',
    account_id          BIGINT        NOT NULL COMMENT '付款账户',
    amount              DECIMAL(18,2) NOT NULL COMMENT '付款金额',
    payment_date        DATETIME      NOT NULL COMMENT '付款时间',
    method              VARCHAR(32)   NOT NULL DEFAULT 'TRANSFER' COMMENT 'TRANSFER/ACCEPTANCE/CASH',
    bank_serial_no      VARCHAR(64)   NULL COMMENT '银行流水号',
    status              VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    fail_reason         VARCHAR(512)  NULL,
    remark              VARCHAR(512)  NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT        NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_code (code),
    KEY idx_payment_request (payment_request_id),
    KEY idx_payment_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款记录';

-- ============================================================
-- 8. 运维库 zl_om
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_om DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_om;

DROP TABLE IF EXISTS om_work_order;
DROP TABLE IF EXISTS om_alarm;
DROP TABLE IF EXISTS om_device;
DROP TABLE IF EXISTS om_station;

CREATE TABLE om_station (
    id                   BIGINT        NOT NULL COMMENT '主键',
    code                 VARCHAR(32)   NOT NULL COMMENT '电站编码',
    name                 VARCHAR(128)  NOT NULL COMMENT '电站名称',
    project_id           BIGINT        NULL COMMENT '项目ID',
    customer_id          BIGINT        NULL COMMENT '客户ID',
    capacity_mw          DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '装机容量MW',
    grid_connection_date DATE          NULL COMMENT '并网日期',
    address              VARCHAR(255)  NULL COMMENT '电站地址',
    manager_id           BIGINT        NULL COMMENT '运维负责人',
    status               VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    created_by           BIGINT        NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT        NULL,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_code (code),
    KEY idx_station_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电站';

CREATE TABLE om_device (
    id             BIGINT       NOT NULL COMMENT '主键',
    code           VARCHAR(32)  NOT NULL COMMENT '设备编码',
    station_id     BIGINT       NOT NULL COMMENT '电站ID',
    name           VARCHAR(128) NOT NULL COMMENT '设备名称',
    type           VARCHAR(32)  NOT NULL COMMENT 'INVERTER/METER/COMBINER_BOX/TRANSFORMER/WEATHER_STATION',
    model          VARCHAR(64)  NULL COMMENT '型号',
    manufacturer   VARCHAR(128) NULL COMMENT '厂商',
    install_date   DATE         NULL COMMENT '安装日期',
    status         VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE/FAULT',
    last_online_at DATETIME     NULL COMMENT '最后在线时间',
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT       NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (code),
    KEY idx_device_station (station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';

CREATE TABLE om_alarm (
    id            BIGINT       NOT NULL COMMENT '主键',
    code          VARCHAR(32)  NOT NULL COMMENT '告警编码',
    station_id    BIGINT       NOT NULL COMMENT '电站ID',
    device_id     BIGINT       NULL COMMENT '设备ID',
    alarm_type    VARCHAR(64)  NOT NULL COMMENT '告警类型',
    level         VARCHAR(16)  NOT NULL COMMENT 'CRITICAL/MAJOR/MINOR/WARNING',
    content       VARCHAR(512) NOT NULL COMMENT '告警内容',
    alarm_time    DATETIME     NOT NULL COMMENT '告警时间',
    status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACKED/CLEARED',
    work_order_id BIGINT       NULL COMMENT '关联工单',
    cleared_at    DATETIME     NULL COMMENT '清除时间',
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alarm_code (code),
    KEY idx_alarm_station (station_id),
    KEY idx_alarm_device (device_id),
    KEY idx_alarm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警';

CREATE TABLE om_work_order (
    id          BIGINT        NOT NULL COMMENT '主键',
    code        VARCHAR(32)   NOT NULL COMMENT '工单号',
    station_id  BIGINT        NOT NULL COMMENT '电站ID',
    device_id   BIGINT        NULL COMMENT '设备ID',
    alarm_id    BIGINT        NULL COMMENT '告警ID',
    type        VARCHAR(32)   NOT NULL COMMENT 'REPAIR/INSPECTION/MAINTENANCE/EMERGENCY',
    priority    VARCHAR(16)   NOT NULL COMMENT 'CRITICAL/MAJOR/MINOR/WARNING',
    title       VARCHAR(128)  NOT NULL COMMENT '标题',
    description TEXT          NULL COMMENT '描述',
    assignee_id BIGINT        NULL COMMENT '处理人',
    plan_start  DATETIME      NULL COMMENT '计划开始',
    plan_end    DATETIME      NULL COMMENT '计划结束',
    actual_end  DATETIME      NULL COMMENT '实际完成',
    status      VARCHAR(32)   NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/ASSIGNED/PROCESSING/COMPLETED/CLOSED/CANCELLED',
    cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '工单成本',
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_code (code),
    KEY idx_wo_station (station_id),
    KEY idx_wo_status (status),
    KEY idx_wo_alarm (alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维工单';

-- ============================================================
-- 9. IoT 元数据库 zl_iot
-- ============================================================
CREATE DATABASE IF NOT EXISTS zl_iot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE zl_iot;

DROP TABLE IF EXISTS iot_alarm_rule;

CREATE TABLE iot_alarm_rule (
    id           BIGINT        NOT NULL COMMENT '主键',
    name         VARCHAR(128)  NOT NULL COMMENT '规则名称',
    device_type  VARCHAR(32)   NOT NULL COMMENT '设备类型',
    metric       VARCHAR(64)   NOT NULL COMMENT '指标名',
    operator     VARCHAR(8)    NOT NULL COMMENT '> >= < <= == !=',
    threshold    DECIMAL(18,4) NOT NULL COMMENT '阈值',
    duration_sec INT           NOT NULL DEFAULT 0 COMMENT '持续秒数',
    level        VARCHAR(16)   NOT NULL COMMENT 'CRITICAL/MAJOR/MINOR/WARNING',
    enabled      TINYINT       NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark       VARCHAR(255)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_rule_device_type (device_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT告警规则';

SET FOREIGN_KEY_CHECKS = 1;
