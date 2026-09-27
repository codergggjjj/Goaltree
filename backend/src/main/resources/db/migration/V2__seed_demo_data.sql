INSERT INTO users (id, email, display_name, timezone, week_starts_on, default_weekly_capacity_minutes)
VALUES ('demo-user', 'demo@goalflow.local', '旅人', 'Asia/Shanghai', 1, 600);

INSERT INTO goals (
    id, user_id, title, description, start_date, target_date, priority, status,
    progress_mode, current_progress, version
) VALUES (
    'demo-data-analysis-goal', 'demo-user', '完成数据分析课程，并发布 2 个可展示作品',
    '通过完整课程与两个公开作品建立可展示的数据分析能力。',
    '2026-07-01', '2026-11-30', 'HIGH', 'ACTIVE', 'MILESTONE_WEIGHTED', 41, 1
);

INSERT INTO completion_criteria
    (id, user_id, goal_id, criterion_type, description, target_value, unit, required_flag)
VALUES
    ('demo-criterion-course', 'demo-user', 'demo-data-analysis-goal', 'METRIC', '课程核心章节完成率达到 100%', 100, '%', TRUE),
    ('demo-criterion-projects', 'demo-user', 'demo-data-analysis-goal', 'METRIC', '两个作品都有公开说明页', 2, '个', TRUE),
    ('demo-criterion-feedback', 'demo-user', 'demo-data-analysis-goal', 'METRIC', '至少获得一次外部反馈并完成修订', 1, '次', TRUE);

INSERT INTO milestones
    (id, user_id, goal_id, title, acceptance, target_date, weight, progress, status, sort_order)
VALUES
    ('demo-m1', 'demo-user', 'demo-data-analysis-goal', 'M1 基础模块完成', '完成基础模块的全部章节与练习', '2026-08-31', 25, 64, 'IN_PROGRESS', 1),
    ('demo-m2', 'demo-user', 'demo-data-analysis-goal', 'M2 项目一发布', '作品一有公开说明页和可复现分析', '2026-09-30', 30, 0, 'NOT_STARTED', 2),
    ('demo-m3', 'demo-user', 'demo-data-analysis-goal', 'M3 高级模块完成', '完成高级模块的全部核心章节', '2026-10-31', 20, 0, 'NOT_STARTED', 3),
    ('demo-m4', 'demo-user', 'demo-data-analysis-goal', 'M4 项目二发布', '作品二发布并根据外部反馈修订', '2026-11-30', 25, 0, 'NOT_STARTED', 4);

INSERT INTO period_plans
    (id, user_id, plan_type, start_date, end_date, status, revision_no, capacity_minutes, summary, confirmed_at, effective_at)
VALUES
    ('demo-week-plan-2026-07-20', 'demo-user', 'WEEK', '2026-07-20', '2026-07-26', 'CONFIRMED', 1, 480,
     '完成数据清洗章节和练习报告', '2026-07-19 12:00:00.000', '2026-07-20 00:00:00.000');

INSERT INTO plan_items
    (id, user_id, plan_id, goal_id, milestone_id, expected_result, planned_delta, planned_effort_minutes, status)
VALUES
    ('demo-week-plan-item', 'demo-user', 'demo-week-plan-2026-07-20', 'demo-data-analysis-goal', 'demo-m1',
     '完成数据清洗章节和练习报告', 10, 480, 'PLANNED');

INSERT INTO progress_entries
    (id, user_id, goal_id, plan_item_id, milestone_id, occurred_at, result, delta_value, effort_minutes, blocker, blocker_active)
VALUES
    ('demo-progress-entry', 'demo-user', 'demo-data-analysis-goal', 'demo-week-plan-item', 'demo-m1',
     '2026-07-25 12:00:00.000', '完成章节和练习，但报告缺少异常值分析。', 6, 360,
     '练习数据的质量和复杂度高于预期。', TRUE);
