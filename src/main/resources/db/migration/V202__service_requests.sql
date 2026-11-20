CREATE TABLE service_requests (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 table_id UUID NOT NULL REFERENCES restaurant_tables(id),
 request_type VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
 notes VARCHAR(250),
 assigned_staff_id UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 accepted_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 cancelled_at TIMESTAMPTZ,
 CONSTRAINT service_request_type_ck CHECK (request_type IN ('WAITER','CLEANING','WATER','CUTLERY','BILL','OTHER')),
 CONSTRAINT service_request_status_ck CHECK (status IN ('REQUESTED','ON_THE_WAY','COMPLETED','CANCELLED'))
);
CREATE INDEX idx_service_requests_staff ON service_requests(tenant_id,branch_id,status,created_at);
CREATE INDEX idx_service_requests_table ON service_requests(table_id,created_at DESC);
CREATE UNIQUE INDEX uq_service_request_active_type ON service_requests(table_id,request_type)
 WHERE status IN ('REQUESTED','ON_THE_WAY');
