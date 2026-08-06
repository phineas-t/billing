INSERT INTO plans (
    code, display_name, stripe_price_id,
    monthly_price_cents, limits
)
VALUES
(
    'FREE',
    'Free',
    NULL,
    0,
    '{"apiCallsPerMonth": 1000, "seats": 1, "storageMb": 512}'
),
(
    'PRO',
    'Pro',
    'price_PRO_ID_HERE',
    2900,
    '{"apiCallsPerMonth": 50000, "seats": 5, "storageMb": 10240}'
),
(
    'ENTERPRISE',
    'Enterprise',
    'price_ENT_ID_HERE',
    9900,
    '{"apiCallsPerMonth": 500000, "seats": 25, "storageMb": 102400}'
);