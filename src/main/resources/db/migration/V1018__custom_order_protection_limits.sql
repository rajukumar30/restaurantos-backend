-- Replace only the numeric checks on the existing settings table; preserve mode and all stored values.
DO $$
DECLARE c record;
BEGIN
 FOR c IN SELECT conname FROM pg_constraint
   WHERE conrelid='branch_order_security'::regclass AND contype='c'
   AND (pg_get_constraintdef(oid) LIKE '%max_order_amount%' OR pg_get_constraintdef(oid) LIKE '%max_visit_amount%' OR pg_get_constraintdef(oid) LIKE '%max_units%')
 LOOP
   EXECUTE format('ALTER TABLE branch_order_security DROP CONSTRAINT %I', c.conname);
 END LOOP;
END $$;
ALTER TABLE branch_order_security
 ADD CONSTRAINT branch_order_security_order_range CHECK (max_order_amount BETWEEN 100 AND 10000),
 ADD CONSTRAINT branch_order_security_visit_range CHECK (max_visit_amount BETWEEN max_order_amount AND 50000),
 ADD CONSTRAINT branch_order_security_units_range CHECK (max_units BETWEEN 1 AND 40);

CREATE TABLE branch_order_security_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 changed_by UUID NOT NULL REFERENCES app_users(id),
 mode VARCHAR(10) NOT NULL CHECK(mode IN ('SECURE','EXPRESS','STRICT')),
 max_order_amount NUMERIC(12,2) NOT NULL,
 max_visit_amount NUMERIC(12,2) NOT NULL,
 max_units INTEGER NOT NULL,
 changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_security_audit_branch ON branch_order_security_audit(tenant_id,branch_id,changed_at DESC);
