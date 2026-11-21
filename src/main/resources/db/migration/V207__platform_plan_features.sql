CREATE TABLE platform_plan_features (
 plan_id UUID NOT NULL REFERENCES platform_subscription_plans(id) ON DELETE CASCADE,
 feature_key VARCHAR(80) NOT NULL REFERENCES feature_catalog(feature_key),
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(plan_id,feature_key)
);
