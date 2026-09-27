INSERT INTO revisions (
    id,
    user_id,
    entity_type,
    entity_id,
    revision_no,
    action_code,
    reason,
    effective_at,
    before_json,
    after_json
)
SELECT
    'demo-goal-revision-1',
    'demo-user',
    'GOAL',
    'demo-data-analysis-goal',
    1,
    'CREATE',
    '创建并启动示例目标',
    '2026-07-01 00:00:00.000',
    NULL,
    JSON_OBJECT(
        'id', 'demo-data-analysis-goal',
        'title', '完成数据分析课程，并发布 2 个可展示作品',
        'status', 'ACTIVE',
        'progressMode', 'MILESTONE_WEIGHTED',
        'version', 1
    )
WHERE NOT EXISTS (
    SELECT 1 FROM revisions WHERE id = 'demo-goal-revision-1'
);
