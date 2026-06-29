import React from "react";
import ReactDOM from "react-dom/client";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Alert,
  App as AntApp,
  Badge,
  Button,
  Card,
  Col,
  Collapse,
  ConfigProvider,
  Descriptions,
  Divider,
  Empty,
  Form,
  Grid,
  Input,
  Layout,
  List,
  message,
  Popconfirm,
  Row,
  Segmented,
  Space,
  Spin,
  Statistic,
  Steps,
  Table,
  Tag,
  theme,
  Timeline,
  Typography
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { Bar, Line, Pie } from "@ant-design/plots";
import {
  AlertOutlined,
  BarChartOutlined,
  BellOutlined,
  CheckCircleOutlined,
  SendOutlined,
  ThunderboltOutlined
} from "@ant-design/icons";
import "./styles.css";

const { Header, Content } = Layout;
const { Title, Text, Paragraph } = Typography;
const DEFAULT_QUESTION = "2018-08-29 华东 GMV 为什么跌了？";
const ROLE_OPTIONS = [
  { label: "数据分析师", value: "analyst" },
  { label: "平台运营", value: "platform" },
  { label: "类目运营", value: "category" },
  { label: "增长运营", value: "growth" },
  { label: "售后治理", value: "after_sales" },
  { label: "运营主管", value: "manager" }
] as const;

type AnyRecord = Record<string, any>;
type RoleKey = "analyst" | "platform" | "category" | "growth" | "after_sales" | "manager";
type CategoryDelta = {
  category: string;
  current: number;
  previous: number;
  delta: number;
};
type AnomalyRow = {
  key: string;
  sourceSystem: string;
  analyzeEndpoint: string;
  routeName: string;
  routeSummary: string;
  routeOwner: string;
  date: string;
  metric: string;
  scope: string;
  current: string;
  previous: string;
  deltaRate: string;
  severity: string;
  status: string;
  notificationStatus?: WorkflowState["notificationStatus"];
  owner: string;
  confidence: string;
  source: string;
  question: string;
  description: string;
  nextStep: string;
};
type RouteRow = {
  key: string;
  owner: string;
  signal: string;
  problem: string;
  evidence: string;
  actionPlan: string[];
  drilldown: string;
  confidence: string;
};
type CauseRow = {
  key: string;
  title: string;
  summary: string;
  source: string;
  rankReason: string;
};
type ToolEvidenceRow = {
  key: string;
  title: string;
  summary: string;
  source: string;
  confidence: string;
};
type WorkflowState = {
  status: "待确认" | "已确认" | "已派发" | "处理中" | "已关闭" | "误报";
  notificationStatus: "未发送" | "已发送飞书" | "去重拦截" | "发送失败" | "已降级到日志";
  confirmedBy: string;
  assignee: string;
  finalReason: string;
  closeNote: string;
  isFalsePositive: boolean;
  timeline: Array<{ time: string; actor: string; action: string }>;
};

function App() {
  const [form] = Form.useForm();
  const [loading, setLoading] = React.useState(false);
  const [sending, setSending] = React.useState(false);
  const [mode, setMode] = React.useState<"answer" | "trigger">("answer");
  const [anomalyResult, setAnomalyResult] = React.useState<AnyRecord | null>(null);
  const [agentResult, setAgentResult] = React.useState<AnyRecord | null>(null);
  const [runtime, setRuntime] = React.useState<AnyRecord | null>(null);
  const [anomalyCenterRows, setAnomalyCenterRows] = React.useState<AnomalyRow[]>([]);
  const [anomalyIntegrationStory, setAnomalyIntegrationStory] = React.useState("");
  const [activeAnomalyKey, setActiveAnomalyKey] = React.useState<string>("");
  const [selectedAnomalyKey, setSelectedAnomalyKey] = React.useState<string>("");
  const [selectedAnomaly, setSelectedAnomaly] = React.useState<AnomalyRow | null>(null);
  const [activeAnomalyTitle, setActiveAnomalyTitle] = React.useState("");
  const [lastRunAt, setLastRunAt] = React.useState<string>("");
  const [currentRole, setCurrentRole] = React.useState<RoleKey>("analyst");
  const [workflowByAnomaly, setWorkflowByAnomaly] = React.useState<Record<string, WorkflowState>>({});
  const resultAnchorRef = React.useRef<HTMLDivElement | null>(null);
  const agentAnchorRef = React.useRef<HTMLDivElement | null>(null);
  const screens = Grid.useBreakpoint();
  const isDesktop = Boolean(screens.md);

  React.useEffect(() => {
    fetchJson("/api/ecommerce/runtime").then(setRuntime).catch(() => setRuntime(null));
    fetchJson("/api/ecommerce/anomalies")
      .then((payload) => {
        setAnomalyCenterRows(normalizeAnomalyRows(payload.items || []));
        setWorkflowByAnomaly((prev) => ({ ...prev, ...collectWorkflowStates(payload.items || []) }));
        setAnomalyIntegrationStory(payload.integration_story || "");
      })
      .catch(() => setAnomalyCenterRows([]));
    form.setFieldsValue({ session_id: "demo-ui-session", question: DEFAULT_QUESTION });
  }, [form]);

  const rootCause = resolveRootCause(anomalyResult);
  const analysisRoute = resolveAnalysisRoute(anomalyResult, selectedAnomaly);
  const agentRootCause = resolveRootCause(agentResult);
  const facts = rootCause?.facts || {};
  const notificationDraft = rootCause?.notification_draft || anomalyResult?.notification_draft || {};
  const notificationGate = resolveNotificationGate(notificationDraft, rootCause);
  const actionRouting = Array.isArray(rootCause?.action_routing) ? rootCause.action_routing : [];
  const causeRanking = buildCauseRows(rootCause);
  const toolEvidenceRows = buildToolEvidenceRows(rootCause);
  const currentRegion = first(facts.current_region);
  const previousRegion = first(facts.previous_region);
  const currentOrder = first(facts.current_order_structure);
  const previousOrder = first(facts.previous_order_structure);
  const currentUser = first(facts.current_user_metrics);
  const previousUser = first(facts.previous_user_metrics);
  const currentFunnel = first(facts.current_funnel);
  const previousFunnel = first(facts.previous_funnel);
  const categoryDeltas = resolveCategoryDeltas(facts.current_category_breakdown || facts.current_category_rank, facts.previous_category_breakdown || facts.previous_category_rank);
  const topCategoryDelta = categoryDeltas[0];
  const anomalyRows = anomalyCenterRows.filter((row) => row.key === "anom-20180829-east-gmv").slice(0, 1);
  const visibleAnomalyRows = applyWorkflowToRows(
    filterAnomalyRowsForRole(anomalyRows, currentRole, workflowByAnomaly),
    workflowByAnomaly
  );
  const routeRows = buildRouteRows(actionRouting);
  const visibleRouteRows = filterRouteRowsForRole(routeRows, currentRole);
  const feishuConfigured = Boolean(runtime?.reply_policy?.feishu_webhook_configured);
  const canSendNotification = Boolean(notificationDraft.body || rootCause?.summary || anomalyResult?.answer);
  const hasAnyResult = Boolean(anomalyResult || agentResult);
  const activeWorkflow = buildWorkflowState(selectedAnomaly, rootCause, workflowByAnomaly);
  const workflowSummary = summarizeWorkflow(workflowByAnomaly, anomalyRows);

  async function updateWorkflow(actionKey: string, action: string, patch: Partial<WorkflowState>, extra: AnyRecord = {}) {
    const key = selectedAnomaly?.key || String(anomalyResult?.anomaly_id || "");
    if (!key) {
      message.warning("请先选择一条异常");
      return;
    }
    const base = workflowByAnomaly[key] || createWorkflowState(selectedAnomaly, rootCause);
    const optimistic = {
      ...base,
      ...patch,
      timeline: [
        { time: new Date().toLocaleTimeString(), actor: roleLabel(currentRole), action },
        ...base.timeline
      ].slice(0, 8)
    };
    setWorkflowByAnomaly((prev) => {
      return {
        ...prev,
        [key]: optimistic
      };
    });
    try {
      const payload = await fetchJson(`/api/ecommerce/anomalies/${key}/workflow/${actionKey}`, {
        method: "POST",
        body: JSON.stringify({
          actor: roleLabel(currentRole),
          assignee_role: patch.assignee || selectedAnomaly?.routeOwner || selectedAnomaly?.owner || "",
          assignee_user: extra.assignee_user || "",
          notification_status: patch.notificationStatus || "",
          final_reason: patch.finalReason || extra.final_reason || "",
          close_note: patch.closeNote || extra.close_note || "",
          note: extra.note || action
        })
      });
      if (payload.workflow) {
        mergeWorkflowState(key, payload.workflow);
      }
    } catch (error) {
      message.error(`处理状态持久化失败：${errorMessage(error)}`);
    }
  }

  function mergeWorkflowState(key: string, workflow: AnyRecord) {
    setWorkflowByAnomaly((prev) => ({
      ...prev,
      [key]: workflowFromServer(workflow, selectedAnomaly, rootCause)
    }));
  }

  async function runAnswer() {
    const values = await form.validateFields();
    setMode("answer");
    setLoading(true);
    try {
      const payload = await fetchJson("/api/ecommerce/answer", {
        method: "POST",
        body: JSON.stringify({ question: values.question, session_id: values.session_id })
      });
      setAgentResult(payload);
      setLastRunAt(new Date().toLocaleTimeString());
      window.requestAnimationFrame(() => {
        agentAnchorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    } catch (error) {
      message.error(`分析失败：${errorMessage(error)}`);
    } finally {
      setLoading(false);
    }
  }

  async function analyzeAnomaly(row: AnomalyRow) {
    form.setFieldsValue({ question: row.question || DEFAULT_QUESTION });
    setActiveAnomalyKey(row.key);
    setSelectedAnomalyKey(row.key);
    setSelectedAnomaly(row);
    setActiveAnomalyTitle(`${row.date} ${row.scope.replace("：", " ")} ${row.metric} 异常`);
    setMode("answer");
    setLoading(true);
    window.requestAnimationFrame(() => {
      resultAnchorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    try {
      const payload = await fetchJson(row.analyzeEndpoint || `/api/ecommerce/anomalies/${row.key}/analyze`, {
        method: "POST",
        body: JSON.stringify({
          session_id: form.getFieldValue("session_id") || "demo-ui-session"
        })
      });
      setAnomalyResult(payload);
      if (payload.workflow) {
        mergeWorkflowState(row.key, payload.workflow);
      }
      setLastRunAt(new Date().toLocaleTimeString());
      message.success(`已进入异常分析：${row.metric} / ${row.scope}`);
    } catch (error) {
      message.error(`异常分析失败：${errorMessage(error)}`);
    } finally {
      setLoading(false);
      setActiveAnomalyKey("");
    }
  }

  async function runTrigger() {
    setMode("trigger");
    setLoading(true);
    try {
      const payload = await fetchJson("/api/ecommerce/triggers/gmv-drop-watch/run-once", { method: "POST" });
      setAnomalyResult(payload);
      setActiveAnomalyTitle("2018-08-29 华东 GMV 异常巡检");
      setLastRunAt(new Date().toLocaleTimeString());
    } catch (error) {
      message.error(`巡检失败：${errorMessage(error)}`);
    } finally {
      setLoading(false);
    }
  }

  const controlPanel = (
    <div className="command-center">
      <Card className="control-card primary-control command-card" bordered={false}>
        <Form form={form} layout="vertical">
          <Row gutter={[16, 12]} align="bottom">
            <Col xs={24} lg={6}>
              <Form.Item label="Session ID" name="session_id">
                <Input size="large" />
              </Form.Item>
            </Col>
            <Col xs={24} lg={12}>
              <Form.Item label="分析问题" name="question" rules={[{ required: true, message: "请输入分析问题" }]}>
                <Input.TextArea rows={isDesktop ? 2 : 3} size="large" />
              </Form.Item>
            </Col>
            <Col xs={24} lg={6}>
              <Space direction="vertical" size={12} className="action-bar">
                <Button type="primary" size="large" icon={<BarChartOutlined />} onClick={runAnswer} loading={loading && mode === "answer"}>
                  运行分析
                </Button>
                <Button size="large" icon={<ThunderboltOutlined />} onClick={runTrigger} loading={loading && mode === "trigger"}>
                  运行 GMV 异常巡检
                </Button>
              </Space>
            </Col>
          </Row>
        </Form>
        <Space wrap className="quick-inline">
          <Text type="secondary">快捷问题</Text>
          {["昨天 GMV 多少？", "订单量 / 客单价", "DAU / 活跃买家", "华东 root cause"].map((item) => (
            <Tag.CheckableTag key={item} checked={false} onChange={() => form.setFieldsValue({ question: item })}>
              {item}
            </Tag.CheckableTag>
          ))}
          <Button size="small" onClick={() => form.setFieldsValue({ question: DEFAULT_QUESTION })}>
            默认问题
          </Button>
        </Space>
      </Card>

      <Card className="control-card status-card" bordered={false}>
        <Space size={[16, 8]} wrap>
          <Badge status={hasAnyResult ? "success" : "default"} text={hasAnyResult ? "已有最新结果" : "等待运行"} />
          <Badge status={feishuConfigured ? "success" : "warning"} text={feishuConfigured ? "飞书 Webhook 已配置" : "飞书未配置，发送降级到日志"} />
          <Text type="secondary">最近运行：{lastRunAt || "-"}</Text>
          <Text type="secondary">去重：{runtime?.reply_policy?.persistent_dedup_enabled ? "持久化启用" : "仅内存/未知"}，超时：{runtime?.reply_policy?.webhook_timeout_seconds || "-"}s</Text>
        </Space>
      </Card>
    </div>
  );

  async function deliverFeishu(title: string, text: string) {
      const payload = await fetchJson("/api/ecommerce/notifications/feishu/send", {
        method: "POST",
        body: JSON.stringify({
          title,
          text,
          session_id: form.getFieldValue("session_id") || "demo-ui-session"
        })
      });
      if (payload.success) {
        if (payload.metadata?.dedupSuppressed) {
          await updateWorkflow("notification", "飞书通知被去重保护拦截，未重复推送", {
            notificationStatus: "去重拦截"
          });
          message.success("通知已被去重保护拦截，避免重复推送");
        } else {
          message.success(payload.metadata?.degraded ? "未配置飞书 Webhook，已降级写入服务端日志" : "已发送到飞书");
          await updateWorkflow("notification", "人工确认发送飞书通知", {
            notificationStatus: payload.metadata?.degraded ? "已降级到日志" : "已发送飞书"
          });
        }
      } else {
        await updateWorkflow("notification", "飞书通知发送失败", { notificationStatus: "发送失败" });
        message.error(payload.message || "飞书发送失败");
      }
  }

  async function sendFeishu() {
    const title = notificationDraft.title || "电商经营异常通知";
    const text = notificationDraft.body || rootCause?.summary || anomalyResult?.answer || "";
    if (!text) {
      message.warning("当前没有可发送的通知内容");
      return;
    }
    setSending(true);
    try {
      await deliverFeishu(title, text);
    } catch (error) {
      message.error(`飞书发送失败：${errorMessage(error)}`);
    } finally {
      setSending(false);
    }
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: "#2468f2",
          borderRadius: 12,
          fontFamily: "'Inter', 'DIN Alternate', 'PingFang SC', 'Microsoft YaHei', sans-serif"
        }
      }}
    >
      <AntApp>
        <Layout className="app-shell">
          <Layout>
            <Header className="workspace-header">
              <div className="brand header-brand">
                <div className="brand-mark">EA</div>
                <div>
                  <Title level={3}>电商运营异常分析工作台</Title>
                  <Text type="secondary">发现异常 → 拆解维度 → 形成解释 → 推动处理</Text>
                </div>
              </div>
            </Header>

            <Content className="workspace-content">
              <Card className="role-switch-card" bordered={false}>
                <div>
                  <Text type="secondary">当前视角</Text>
                  <Title level={5}>{roleTitle(currentRole)}</Title>
                  <Text type="secondary">{roleDescription(currentRole)}</Text>
                </div>
                <Segmented
                  size="large"
                  options={[...ROLE_OPTIONS]}
                  value={currentRole}
                  onChange={(value) => setCurrentRole(value as RoleKey)}
                />
              </Card>
              {controlPanel}
              <div ref={resultAnchorRef} className="result-anchor" />
              <Spin spinning={loading} tip={mode === "trigger" ? "正在运行异常巡检..." : "正在分析问题..."}>
                <Row gutter={[16, 16]} className="dashboard-row first-dashboard-row">
                  <Col xs={24}>
                    <Card
                      title={`${currentRole === "analyst" ? "异常中心" : `${roleLabel(currentRole)}待办`}（${visibleAnomalyRows.length}）`}
                      className="workspace-card"
                      extra={<Text type="secondary">{anomalyIntegrationStory || "原业务系统异常池 -> anomaly_id -> Agent 分析"}</Text>}
                    >
                      <Table<AnomalyRow>
                        rowKey="key"
                        size="middle"
                        pagination={{ pageSize: 5, showSizeChanger: false }}
                        scroll={{ x: 980 }}
                        className="anomaly-table"
                        dataSource={visibleAnomalyRows}
                        rowClassName={(row) => row.key === selectedAnomalyKey ? "selected-anomaly-row" : ""}
                        columns={anomalyColumns(analyzeAnomaly, activeAnomalyKey, loading && mode === "answer")}
                        locale={{ emptyText: <Empty description={currentRole === "analyst" || currentRole === "manager" ? "暂无异常，点击 GMV 异常巡检生成" : "暂无分配给当前角色的异常，需先由数据分析师确认并派发"} /> }}
                      />
                    </Card>
                  </Col>
                </Row>

                <div ref={agentAnchorRef} className="result-anchor" />
                {agentResult && (
                  <Card title="Agent 临时分析" className="workspace-card agent-result-card">
                    <Alert
                      type={agentResult.success === false ? "error" : "info"}
                      showIcon
                      message={headline(agentResult, agentRootCause)}
                      description={
                        <div className="agent-answer-md">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {agentRootCause?.summary || agentResult?.answer || "这块只展示你手动输入问题的临时分析，不会覆盖当前异常详情。"}
                          </ReactMarkdown>
                        </div>
                      }
                    />
                  </Card>
                )}

                {anomalyResult ? (
                  <>
                    <div className="section-title-row">
                      <div>
                        <Title level={4}>异常处理 Chain</Title>
                        <Text type="secondary">正在处理异常：{activeAnomalyTitle || "待确认经营异常"}，从发现、分析、确认、派发到业务闭环统一在这里推进。</Text>
                      </div>
                    </div>
                    <AgentCapabilityPanel
                      anomaly={selectedAnomaly}
                      rootCause={rootCause}
                      analysisRoute={analysisRoute}
                      actionRouting={actionRouting}
                      notificationGate={notificationGate}
                    />
                    <Row gutter={[16, 16]} className="dashboard-row workflow-priority-row">
                      <Col xs={24}>
                        <WorkflowCard
                          role={currentRole}
                          anomaly={selectedAnomaly}
                          workflow={activeWorkflow}
                          summary={workflowSummary}
                          onConfirm={() => updateWorkflow("confirm", "确认异常有效，进入派发前复核", {
                            status: "已确认",
                            confirmedBy: "数据分析师",
                            isFalsePositive: false
                          })}
                          onDispatch={() => updateWorkflow("dispatch", "派发给责任角色，等待业务方接手", {
                            status: "已派发",
                            assignee: selectedAnomaly?.routeOwner || selectedAnomaly?.owner || "业务负责人"
                          })}
                          onProgress={() => updateWorkflow("accept", `${roleLabel(currentRole)}接手并开始处理`, {
                            status: "处理中",
                            assignee: roleLabel(currentRole)
                          })}
                          onRecord={(note) => updateWorkflow("record", "补充处理记录", {
                            status: "处理中"
                          }, { note })}
                          onClose={() => updateWorkflow("close", "关闭异常并沉淀最终原因", {
                            status: "已关闭",
                            finalReason: rootCause?.cause_ranking?.[0]?.summary || rootCause?.summary || "已完成业务排查并确认最终原因。",
                            closeNote: "本轮处理已闭环，可进入复盘或沉淀为案例。"
                          })}
                          onFalsePositive={() => updateWorkflow("false-positive", "标记为误报，不再派发业务处理", {
                            status: "误报",
                            isFalsePositive: true,
                            finalReason: "经人工复核，当前异常证据不足或属于正常波动。",
                            closeNote: "不建议继续推送业务方。"
                          })}
                        />
                      </Col>
                    </Row>
                    <Card className="workspace-card analysis-route-card" title="Agent 分析重点">
                      <Row gutter={[16, 12]}>
                        <Col xs={24} lg={6}>
                          <Text type="secondary">异常类型路由</Text>
                          <Title level={5}>{analysisRoute.route_name || selectedAnomaly?.routeName || "GMV 完整 root cause"}</Title>
                          <Paragraph>{analysisRoute.focus_summary || selectedAnomaly?.routeSummary || "按标准 root cause 链路排查。"}</Paragraph>
                        </Col>
                        <Col xs={24} lg={6}>
                          <Text type="secondary">优先排查维度</Text>
                          <div className="tag-list">
                            {toStringList(analysisRoute.priority_dimensions).map((item) => (
                              <Tag color="blue" key={item}>{item}</Tag>
                            ))}
                          </div>
                        </Col>
                        <Col xs={24} lg={6}>
                          <Text type="secondary">责任配置</Text>
                          <Paragraph className="route-owner-line">
                            主责：<Text strong>{analysisRoute.primary_owner || selectedAnomaly?.routeOwner || selectedAnomaly?.owner || "经营分析"}</Text>
                          </Paragraph>
                          <div className="tag-list">
                            {toStringList(analysisRoute.handoff_roles).slice(0, 4).map((item) => (
                              <Tag color="geekblue" key={item}>{item}</Tag>
                            ))}
                          </div>
                          <Text type="secondary">通知：{analysisRoute.notification_channel || "经营异常总览群"}</Text>
                        </Col>
                        <Col xs={24} lg={6}>
                          <Text type="secondary">人工 SOP</Text>
                          <ol className="compact-ordered-list">
                            {toStringList(analysisRoute.human_sop).slice(0, 4).map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ol>
                        </Col>
                      </Row>
                      <Divider />
                      <Space direction="vertical" size={2}>
                        <Text type="secondary">派发规则：{analysisRoute.assignment_rule || "先由数据分析师复核，再派发给主责角色。"}</Text>
                        <Text type="secondary">{analysisRoute.agent_policy || "人定义排查 SOP，Agent 根据 anomaly_id 自动选择重点并生成证据。"}</Text>
                      </Space>
                    </Card>
                <Alert
                  className="hero-alert"
                  type={anomalyResult?.success === false ? "error" : "info"}
                  showIcon
                  icon={<AlertOutlined />}
                  message={headline(anomalyResult, rootCause)}
                  description={rootCause?.summary || anomalyResult?.answer || "运行分析后，这里会展示面向业务方的核心结论。"}
                />

                {anomalyResult && (
                  <Alert
                    className="notify-gate-alert"
                    type={notificationGate.recommendation === "recommend_notify" ? "success" : notificationGate.recommendation === "log_only" ? "warning" : "info"}
                    showIcon
                    message={`通知建议：${notificationGate.text}`}
                    description={`可信度：${notificationGate.confidenceText}。当前策略：Agent 只生成通知草稿，飞书发送必须人工确认。`}
                  />
                )}

                <Row gutter={[16, 16]} className="metric-row">
                  <Col xs={24} md={12} xl={6}>
                    <MetricCard title="GMV 变化" value={metricValue(readNumber(previousRegion, "gmv"), readNumber(currentRegion, "gmv"))} detail={`区域 GMV ${deltaText(readNumber(previousRegion, "gmv"), readNumber(currentRegion, "gmv"))}`} />
                  </Col>
                  <Col xs={24} md={12} xl={6}>
                    <MetricCard title="订单结构" value={metricValue(readNumber(previousOrder, "order_count"), readNumber(currentOrder, "order_count"))} detail={`客单价 ${metricValue(readNumber(previousOrder, "avg_order_value"), readNumber(currentOrder, "avg_order_value"))}`} />
                  </Col>
                  <Col xs={24} md={12} xl={6}>
                    <MetricCard title="用户规模" value={metricValue(readNumber(previousUser, "dau"), readNumber(currentUser, "dau"))} detail={`活跃买家 ${metricValue(readNumber(previousUser, "active_buyer_count"), readNumber(currentUser, "active_buyer_count"))}`} />
                  </Col>
                  <Col xs={24} md={12} xl={6}>
                    <MetricCard title="漏斗转化" value={metricValue(readNumber(previousFunnel, "view_to_pay_rate"), readNumber(currentFunnel, "view_to_pay_rate"))} detail={topCategoryDelta ? `品类拖累：${topCategoryDelta.category} ${formatNumber(topCategoryDelta.delta)}` : "暂无明确品类拖累"} />
                  </Col>
                </Row>

                <Row gutter={[16, 16]} className="dashboard-row">
                  <Col xs={24} xl={10}>
                    <Card title="主要原因排序" className="workspace-card">
                      {causeRanking.length ? (
                        <div className="cause-ranking-list">
                          {causeRanking.map((item, index) => (
                            <div className="cause-ranking-item" key={item.key}>
                              <span className="cause-rank-number">{index + 1}</span>
                              <div>
                                <Space wrap>
                                  <Text strong>{item.title}</Text>
                                  <Tag color={index === 0 ? "red" : "orange"}>{index === 0 ? "首要原因" : "重点原因"}</Tag>
                                </Space>
                                <Paragraph>{item.summary}</Paragraph>
                                <Text type="secondary">{item.rankReason}</Text>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <Empty description="暂无原因排序" />
                      )}
                    </Card>
                  </Col>
                  <Col xs={24} xl={14}>
                    <Card title="重点 Tool 结果" className="workspace-card">
                      {toolEvidenceRows.length ? (
                        <div className="tool-evidence-grid">
                          {toolEvidenceRows.map((item) => (
                            <div className="tool-evidence-card" key={item.key}>
                              <Space wrap>
                                <Text strong>{item.title}</Text>
                                <Tag color={item.confidence === "高" ? "green" : "blue"}>可信度：{item.confidence}</Tag>
                              </Space>
                              <Paragraph>{item.summary}</Paragraph>
                              <Text type="secondary">数据来源：{item.source || "当前分析结果"}</Text>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <Empty description="暂无重点 Tool 结果" />
                      )}
                    </Card>
                  </Col>
                </Row>

                <Row gutter={[16, 16]} className="dashboard-row">
                  <Col xs={24} xl={12}>
                    <Card title="GMV / 订单趋势" className="workspace-card">
                      {chartTrendData(previousRegion, currentRegion).length ? (
                        <Line
                          data={chartTrendData(previousRegion, currentRegion)}
                          xField="date"
                          yField="value"
                          colorField="metric"
                          height={260}
                          point={{ sizeField: 4 }}
                          axis={{ y: { title: false }, x: { title: false } }}
                        />
                      ) : (
                        <Empty description="暂无趋势数据" />
                      )}
                    </Card>
                  </Col>
                  <Col xs={24} xl={12}>
                    <Card title="品类拖累贡献" className="workspace-card">
                      {categoryDeltas.length ? (
                        <Bar
                          data={categoryDeltas.slice(0, 6).map((item) => ({ category: item.category, delta: item.delta }))}
                          xField="delta"
                          yField="category"
                          height={260}
                          colorField="delta"
                          style={{ fill: ({ delta }: AnyRecord) => (Number(delta) < 0 ? "#f4664a" : "#52c41a") }}
                          axis={{ x: { title: false }, y: { title: false } }}
                        />
                      ) : (
                        <Empty description="暂无品类拆解" />
                      )}
                    </Card>
                  </Col>
                </Row>

                <Row gutter={[16, 16]} className="dashboard-row">
                  <Col xs={24} xl={15}>
                    <Card title="责任分发" className="workspace-card">
                      {visibleRouteRows.length ? (
                        <div className="route-card-list">
                          {visibleRouteRows.map((item, index) => (
                            <ResponsibilityRouteCard key={item.key} item={item} index={index} />
                          ))}
                        </div>
                      ) : (
                        <Empty description={currentRole === "analyst" || currentRole === "manager" ? "暂无责任分发，先运行 root cause" : "当前角色暂无匹配的责任分发"} />
                      )}
                    </Card>
                  </Col>
                  <Col xs={24} xl={9}>
                    <NotificationCenterCard
                      notificationDraft={notificationDraft}
                      rootCause={rootCause}
                      routeRows={visibleRouteRows}
                      notificationGate={notificationGate}
                      canSendNotification={canSendNotification}
                      sending={sending}
                      onSend={sendFeishu}
                    />
                  </Col>
                </Row>

                <Row gutter={[16, 16]} className="lower-row">
                  <Col xs={24} xl={12}>
                    <Card title="数据口径 / 可信度" className="workspace-card">
                      <Descriptions column={1} size="small">
                        <Descriptions.Item label="公开数据">{lineageText(rootCause, "olist")}</Descriptions.Item>
                        <Descriptions.Item label="补齐口径">{lineageText(rootCause, "demo")}</Descriptions.Item>
                        <Descriptions.Item label="运行规则">{runtime ? "缓存、降级、幂等、只读保护已暴露" : "读取中"}</Descriptions.Item>
                      </Descriptions>
                    </Card>
                  </Col>
                  <Col xs={24} xl={12}>
                    <Card title="开发调试信息" className="workspace-card">
                      <Collapse
                        ghost
                        items={[
                          {
                            key: "debug",
                            label: "查看 Path / Tool / Raw Payload",
                            children: (
                              <pre className="debug-json">
                                {JSON.stringify(
                                  {
                                    path_type: anomalyResult?.path_type || rootCause?.path_type,
                                    tool_chain: anomalyResult?.tool_chain || rootCause?.tool_chain,
                                    runtime,
                                    anomalyResult,
                                    agentResult
                                  },
                                  null,
                                  2
                                )}
                              </pre>
                            )
                          }
                        ]}
                      />
                    </Card>
                  </Col>
                </Row>
                  </>
                ) : (
                  <Card className="workspace-card empty-detail-card">
                    <Empty
                      description="先在异常中心选择一条异常，右侧处理链路才会展开。手动输入问题会进入 Agent 临时分析，不会覆盖这里。"
                    />
                  </Card>
                )}
              </Spin>
            </Content>
          </Layout>
        </Layout>
      </AntApp>
    </ConfigProvider>
  );
}

function anomalyColumns(onAnalyze: (row: AnomalyRow) => void, activeKey: string, analyzing: boolean): ColumnsType<AnomalyRow> {
  return [
    { title: "日期", dataIndex: "date", width: 120 },
    {
      title: "异常对象",
      dataIndex: "metric",
      width: 210,
      render: (value, row) => (
        <Space direction="vertical" size={2} className="anomaly-object">
          <Text strong>{value}</Text>
          <Text type="secondary">{row.scope}</Text>
          <Text code>{row.key}</Text>
        </Space>
      )
    },
    {
      title: "变化",
      dataIndex: "deltaRate",
      width: 170,
      render: (_, row) => (
        <Space direction="vertical" size={2} className="anomaly-change">
          <Text strong>
            {row.previous} → {row.current}
          </Text>
          <Text type={row.deltaRate.startsWith("-") ? "danger" : "secondary"}>{row.deltaRate}</Text>
        </Space>
      )
    },
    {
      title: "状态 / 可信度",
      dataIndex: "status",
      width: 170,
      render: (value, row) => (
        <Space direction="vertical" size={6}>
          <Badge status={statusBadge(value)} text={`处理：${value}`} />
          <Tag color={notificationColor(row.notificationStatus)}>通知：{row.notificationStatus || "未发送"}</Tag>
          <Tag color={row.confidence === "高" ? "green" : "gold"}>可信度：{row.confidence}</Tag>
        </Space>
      )
    },
    {
      title: "主责",
      dataIndex: "owner",
      width: 150,
      render: (value) => <Tag color="blue" className="owner-tag">{value}</Tag>
    },
    {
      title: "问题摘要",
      dataIndex: "description",
      width: 310,
      render: (value, row) => (
        <div className="anomaly-summary">
          <Paragraph ellipsis={{ rows: 2, tooltip: value }}>{value}</Paragraph>
          <Paragraph type="secondary" ellipsis={{ rows: 1, tooltip: row.nextStep }}>
            下一步：{row.nextStep}
          </Paragraph>
        </div>
      )
    },
    {
      title: "操作",
      width: 80,
      render: (_, row) => (
        <Button
          size="small"
          type="link"
          loading={activeKey === row.key}
          disabled={analyzing && activeKey !== row.key}
          onClick={() => onAnalyze(row)}
        >
          {activeKey === row.key ? "分析中" : "分析"}
        </Button>
      )
    }
  ];
}

const routeColumns: ColumnsType<RouteRow> = [
  { title: "责任角色", dataIndex: "owner", width: 160, render: (value) => <Text strong>{value}</Text> },
  { title: "信号", dataIndex: "signal", width: 150 },
  { title: "具体问题", dataIndex: "problem", width: 240 },
  { title: "关键证据", dataIndex: "evidence" },
  {
    title: "行动方案",
    dataIndex: "actionPlan",
    width: 420,
    render: (value: string[]) => (
      <ol className="action-plan-list">
        {(Array.isArray(value) && value.length ? value : ["补充证据后继续排查。"]).map((item, index) => (
          <li key={`${index}-${item}`}>{stripStepPrefix(item)}</li>
        ))}
      </ol>
    )
  },
  { title: "下钻对象", dataIndex: "drilldown", width: 260 },
  { title: "可信度", dataIndex: "confidence", width: 96, render: (value) => <Tag color={value === "高" ? "green" : "blue"}>{value}</Tag> }
];

function ResponsibilityRouteCard({ item, index }: { item: RouteRow; index: number }) {
  const drilldownItems = item.drilldown.split("；").filter(Boolean);
  return (
    <Card className="route-card" bordered={false}>
      <div className="route-card-header">
        <Space size={10} wrap>
          <span className="route-rank">{index + 1}</span>
          <Title level={5}>{item.owner}</Title>
          <Tag color={index < 2 ? "red" : "orange"}>{index < 2 ? "重点原因" : "协同排查"}</Tag>
          <Tag color={item.confidence === "高" ? "green" : "blue"}>可信度：{item.confidence}</Tag>
        </Space>
      </div>
      <Row gutter={[16, 12]}>
        <Col xs={24} lg={10}>
          <div className="route-section">
            <Text type="secondary">具体问题</Text>
            <Paragraph>
              <Text strong>{item.problem}</Text>
            </Paragraph>
          </div>
          <div className="route-section">
            <Text type="secondary">关键证据</Text>
            <Paragraph>{item.evidence}</Paragraph>
          </div>
        </Col>
        <Col xs={24} lg={14}>
          <div className="route-section">
            <Text type="secondary">行动方案</Text>
            <ol className="action-plan-list">
              {item.actionPlan.map((step, stepIndex) => (
                <li key={`${item.key}-${stepIndex}`}>{stripStepPrefix(step)}</li>
              ))}
            </ol>
          </div>
          <div className="route-section route-drilldown">
            <Text type="secondary">下钻对象</Text>
            <div>
              {drilldownItems.map((target) => (
                <Tag key={target} className="drilldown-tag">
                  {target}
                </Tag>
              ))}
            </div>
          </div>
        </Col>
      </Row>
    </Card>
  );
}

function NotificationCenterCard({
  notificationDraft,
  rootCause,
  routeRows,
  notificationGate,
  canSendNotification,
  sending,
  onSend
}: {
  notificationDraft: AnyRecord;
  rootCause: AnyRecord | null;
  routeRows: RouteRow[];
  notificationGate: ReturnType<typeof resolveNotificationGate>;
  canSendNotification: boolean;
  sending: boolean;
  onSend: () => void;
}) {
  const title = notificationDraft.title || "待生成通知标题";
  const body = notificationDraft.body || rootCause?.summary || "root cause 跑完后，这里会生成可推送给责任人的正式文案。";
  const targetRoles = Array.isArray(notificationDraft.target_roles) && notificationDraft.target_roles.length
    ? notificationDraft.target_roles
    : routeRows.map((item) => item.owner);
  const preview = notificationPreview(body);

  return (
    <Card
      title="通知中心"
      className="workspace-card notification-card"
      extra={<ConfirmSendButton disabled={!canSendNotification || notificationGate.recommendation === "log_only"} loading={sending} onConfirm={onSend} />}
    >
      <Space direction="vertical" size={14} className="notification-stack">
        <Alert
          type={notificationGate.recommendation === "recommend_notify" ? "success" : notificationGate.recommendation === "log_only" ? "warning" : "info"}
          showIcon
          message={`统一发送入口：${notificationGate.text}`}
          description="发送飞书只更新通知状态，不等于派发工单；业务角色需要数据分析师点击“派发责任人”后才会看到待办。"
        />
        <div className="notification-title-block">
          <Text type="secondary">通知标题</Text>
          <Title level={5}>{title}</Title>
        </div>
        <div>
          <Text type="secondary">建议同步角色</Text>
          <div className="target-role-list">
            {targetRoles.map((role: unknown) => (
              <Tag key={String(role)} color="blue">
                {String(role)}
              </Tag>
            ))}
          </div>
        </div>
        <div className="notification-preview">
          <Text type="secondary">通知摘要</Text>
          <Paragraph>{preview}</Paragraph>
        </div>
        <Collapse
          ghost
          items={[
            {
              key: "draft",
              label: "查看完整飞书草稿",
              children: <pre className="notification-draft">{body}</pre>
            }
          ]}
        />
        <Divider />
        <List
          size="small"
          dataSource={routeRows.slice(0, 4)}
          locale={{ emptyText: "暂无待通知对象" }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta avatar={<BellOutlined className="route-icon" />} title={item.owner} description={stripStepPrefix(item.actionPlan[0] || "补充证据后继续排查")} />
            </List.Item>
          )}
        />
      </Space>
    </Card>
  );
}

function WorkflowCard({
  role,
  anomaly,
  workflow,
  summary,
  onConfirm,
  onDispatch,
  onProgress,
  onRecord,
  onClose,
  onFalsePositive
}: {
  role: RoleKey;
  anomaly: AnomalyRow | null;
  workflow: WorkflowState;
  summary: ReturnType<typeof summarizeWorkflow>;
  onConfirm: () => void;
  onDispatch: () => void;
  onProgress: () => void;
  onRecord: (note: string) => void;
  onClose: () => void;
  onFalsePositive: () => void;
}) {
  const managerMode = role === "manager";
  const canDispatch = workflow.status === "已确认";
  const alreadyDispatched = ["已派发", "处理中", "已关闭"].includes(workflow.status);
  const canMarkFalsePositive = ["待确认", "已确认"].includes(workflow.status);
  const stepIndex = workflowStepIndex(workflow.status);
  const [recordNote, setRecordNote] = React.useState("");
  const submitRecord = () => {
    const note = recordNote.trim();
    onRecord(note || "已根据建议动作继续下钻排查，等待补充更明确的业务原因。");
    setRecordNote("");
  };
  return (
    <Card
      title="异常处理链"
      className="workspace-card workflow-card"
      extra={<Tag color={workflow.status === "已关闭" ? "green" : workflow.status === "误报" ? "red" : "blue"}>{workflow.status}</Tag>}
    >
      {managerMode ? (
        <Row gutter={[16, 16]}>
          {[
            ["待确认", summary.pending],
            ["已确认", summary.confirmed],
            ["已派发", summary.dispatched],
            ["处理中", summary.processing],
            ["已关闭", summary.closed],
            ["误报", summary.falsePositive]
          ].map(([label, value]) => (
            <Col xs={12} md={8} xl={4} key={label}>
              <Statistic title={label} value={Number(value)} />
            </Col>
          ))}
        </Row>
      ) : (
        <>
          <div className="workflow-chain-overview">
            <Row gutter={[16, 12]}>
              <Col xs={24} lg={6}>
                <Text type="secondary">当前异常</Text>
                <Title level={5}>{anomaly ? `${anomaly.metric} / ${anomaly.scope}` : "待选择异常"}</Title>
                <Text code>{anomaly?.key || "-"}</Text>
              </Col>
              <Col xs={24} lg={5}>
                <Text type="secondary">指标变化</Text>
                <Paragraph strong>{anomaly ? `${anomaly.previous} → ${anomaly.current}（${anomaly.deltaRate}）` : "-"}</Paragraph>
              </Col>
              <Col xs={24} lg={6}>
                <Text type="secondary">主责 / 通知</Text>
                <Paragraph>{workflow.assignee || anomaly?.routeOwner || anomaly?.owner || "待派发"} · {workflow.notificationStatus}</Paragraph>
                <Text type="secondary">来源：{anomaly?.sourceSystem || "-"}</Text>
              </Col>
              <Col xs={24} lg={7}>
                <Text type="secondary">下一步</Text>
                <Paragraph strong>{workflowNextAction(workflow.status, role, anomaly?.nextStep)}</Paragraph>
              </Col>
            </Row>
            <Steps
              className="workflow-chain-steps"
              size="small"
              current={stepIndex}
              status={workflow.status === "误报" ? "error" : workflow.status === "已关闭" ? "finish" : "process"}
              items={[
                { title: "发现异常", description: "指标巡检生成 anomaly_id" },
                { title: "Agent 分析", description: "生成证据和建议" },
                { title: "人工确认", description: "分析师判断是否有效" },
                { title: "责任派发", description: "进入业务方待办" },
                { title: "业务处理", description: "接手、补记录、关闭" }
              ]}
            />
          </div>
          <Row gutter={[18, 16]}>
          <Col xs={24} xl={8}>
            <Descriptions column={1} size="small" className="workflow-descriptions">
              <Descriptions.Item label="异常 ID">{anomaly?.key || "-"}</Descriptions.Item>
              <Descriptions.Item label="处理状态">{workflow.status}</Descriptions.Item>
              <Descriptions.Item label="通知状态">{workflow.notificationStatus}</Descriptions.Item>
              <Descriptions.Item label="确认人">{workflow.confirmedBy || "待数据分析师确认"}</Descriptions.Item>
              <Descriptions.Item label="接手人 / 责任角色">{workflow.assignee || anomaly?.routeOwner || anomaly?.owner || "待派发"}</Descriptions.Item>
              <Descriptions.Item label="是否误报">{workflow.isFalsePositive ? "是" : "否"}</Descriptions.Item>
            </Descriptions>
          </Col>
          <Col xs={24} xl={8}>
            <div className="workflow-action-zone">
              <Text type="secondary">当前角色可做什么</Text>
              <Space wrap className="workflow-actions">
                {role === "analyst" ? (
                  <>
                    <Button type="primary" onClick={onConfirm} disabled={workflow.status !== "待确认"}>确认异常</Button>
                    <Button onClick={onDispatch} disabled={!canDispatch}>
                      {alreadyDispatched ? "已派发" : "派发责任人"}
                    </Button>
                    <Button danger onClick={onFalsePositive} disabled={!canMarkFalsePositive}>
                      {workflow.status === "误报" ? "已标记误报" : alreadyDispatched ? "已派发不可标误报" : "标记误报"}
                    </Button>
                  </>
                ) : (
                  <>
                    <Button type="primary" onClick={onProgress} disabled={workflow.status !== "已派发"}>接手处理</Button>
                    <Button onClick={onClose} disabled={workflow.status !== "处理中"}>关闭异常</Button>
                  </>
                )}
              </Space>
              {role !== "analyst" && (
                <div className="workflow-record-editor">
                  <Input.TextArea
                    value={recordNote}
                    onChange={(event) => setRecordNote(event.target.value)}
                    placeholder="写下当前处理进展，例如：已确认华东下滑集中在家居品类，正在同步类目运营排查重点商品库存。"
                    autoSize={{ minRows: 2, maxRows: 4 }}
                    disabled={workflow.status !== "处理中"}
                  />
                  <Button
                    className="workflow-record-submit"
                    onClick={submitRecord}
                    disabled={workflow.status !== "处理中"}
                  >
                    提交处理记录
                  </Button>
                </div>
              )}
              <Paragraph type="secondary">
                {role === "analyst"
                  ? "数据分析师负责判断异常是否有效、证据是否够、应该派给哪个业务角色。"
                  : `${roleLabel(role)}只处理分配到自己责任域的线索，补充处理过程，并在原因明确后关闭。`}
              </Paragraph>
            </div>
          </Col>
          <Col xs={24} xl={8}>
            <Text type="secondary">处理时间线</Text>
            <Timeline
              className="workflow-timeline"
              items={workflow.timeline.map((item) => ({
                children: (
                  <div>
                    <Text strong>{item.action}</Text>
                    <br />
                    <Text type="secondary">{item.time} · {item.actor}</Text>
                  </div>
                )
              }))}
            />
          </Col>
          <Col xs={24}>
            <Alert
              type={workflow.isFalsePositive ? "warning" : "info"}
              showIcon
              message={`最终原因：${workflow.finalReason || "待业务方处理后补充"}`}
              description={workflow.closeNote || "这里用于沉淀处理结论，后续可以接成真实工单、飞书卡片回调或人工复盘记录。"}
            />
          </Col>
        </Row>
        </>
      )}
    </Card>
  );
}

function AgentCapabilityPanel({
  anomaly,
  rootCause,
  analysisRoute,
  actionRouting,
  notificationGate
}: {
  anomaly: AnomalyRow | null;
  rootCause: AnyRecord | null;
  analysisRoute: AnyRecord;
  actionRouting: AnyRecord[];
  notificationGate: ReturnType<typeof resolveNotificationGate>;
}) {
  const priorityDimensions = toStringList(analysisRoute.priority_dimensions).slice(0, 5);
  const toolChain = toStringList(rootCause?.tool_chain || rootCause?.metadata?.tool_chain).slice(0, 6);
  const owners = actionRouting
    .map((item) => String(item.owner || item.role || ""))
    .filter(Boolean)
    .slice(0, 4);
  const routeName = analysisRoute.route_name || anomaly?.routeName || "GMV 完整 root cause";
  const routeSummary = analysisRoute.focus_summary || anomaly?.routeSummary || "按异常类型选择排查重点，避免所有问题都跑同一套模板。";
  return (
    <Card
      className="workspace-card agent-capability-card"
      title="Agent 接入链路"
      extra={<Tag color="blue">anomaly_id → route → tools → workflow</Tag>}
    >
      <Row gutter={[12, 12]}>
        <Col xs={24} md={12} xl={6}>
          <div className="agent-flow-item">
            <span className="agent-flow-index">1</span>
            <Text type="secondary">异常接入</Text>
            <Title level={5}>{anomaly?.key || "等待 anomaly_id"}</Title>
            <Paragraph>上游 BI / 监控规则先产生异常，Agent 不凭空猜异常。</Paragraph>
          </div>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <div className="agent-flow-item">
            <span className="agent-flow-index">2</span>
            <Text type="secondary">分析路由</Text>
            <Title level={5}>{routeName}</Title>
            <Paragraph>{routeSummary}</Paragraph>
            <div className="tag-list">
              {priorityDimensions.map((item) => (
                <Tag color="geekblue" key={item}>{item}</Tag>
              ))}
            </div>
          </div>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <div className="agent-flow-item">
            <span className="agent-flow-index">3</span>
            <Text type="secondary">Tool 取数</Text>
            <Title level={5}>{toolChain.length ? `${toolChain.length} 个工具链路` : "业务 Tool 证据链"}</Title>
            <Paragraph>{toolChain.length ? toolChain.join(" → ") : "GMV、订单、用户、品类、漏斗、退款和业务证据一起进入归因。"}</Paragraph>
          </div>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <div className="agent-flow-item">
            <span className="agent-flow-index">4</span>
            <Text type="secondary">业务交付</Text>
            <Title level={5}>{notificationGate.confidenceText}可信 · 人工确认</Title>
            <Paragraph>{owners.length ? `优先分发给：${owners.join("、")}` : "生成通知草稿和处理记录，等待人确认后派发。"}</Paragraph>
          </div>
        </Col>
      </Row>
      <Divider />
      <Alert
        type="info"
        showIcon
        message="这层展示的是 Agent 真正介入业务系统的位置。"
        description="它不是替代 BI，也不是单纯聊天窗口，而是把异常记录转成可复用的分析路线、证据结果、通知草稿和处理状态。"
      />
    </Card>
  );
}

function workflowStepIndex(status: WorkflowState["status"]) {
  if (status === "待确认") return 1;
  if (status === "已确认") return 2;
  if (status === "已派发") return 3;
  if (status === "处理中") return 4;
  if (status === "已关闭" || status === "误报") return 4;
  return 0;
}

function workflowNextAction(status: WorkflowState["status"], role: RoleKey, fallback?: string) {
  if (status === "待确认") return "数据分析师先确认异常是否有效";
  if (status === "已确认") return "数据分析师派发给主责业务角色";
  if (status === "已派发") return role === "analyst" ? "等待业务方接手处理" : "点击接手处理";
  if (status === "处理中") return "补充处理记录，原因明确后关闭";
  if (status === "已关闭") return "已闭环，可沉淀为案例";
  if (status === "误报") return "已标记误报，不再派发";
  return fallback || "继续处理当前异常";
}

function MetricCard({ title, value, detail }: { title: string; value: React.ReactNode; detail: string }) {
  return (
    <Card className="metric-card" bordered={false}>
      <Space direction="vertical" size={4}>
        <Text type="secondary">{title}</Text>
        <Statistic value={String(value)} valueStyle={{ fontSize: 24 }} />
        <Text type="secondary">{detail}</Text>
      </Space>
    </Card>
  );
}

function ConfirmSendButton({ disabled, loading, onConfirm }: { disabled: boolean; loading: boolean; onConfirm: () => void }) {
  return (
    <Popconfirm
      title="确认发送到飞书？"
      description="这一步只发送通知，不会自动派发给业务角色；派发需要在处理闭环里单独点击。"
      okText="确认发送"
      cancelText="先不发"
      onConfirm={onConfirm}
      disabled={disabled}
    >
      <Button type="primary" icon={<SendOutlined />} disabled={disabled} loading={loading}>
        人工确认发送飞书
      </Button>
    </Popconfirm>
  );
}

async function fetchJson(url: string, options: RequestInit = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json();
}

function resolveRootCause(result: AnyRecord | null) {
  if (!result) return null;
  return result.root_cause || result.analysis?.root_cause || null;
}

function resolveNotificationGate(notificationDraft: AnyRecord, rootCause: AnyRecord | null) {
  const confidence = String(notificationDraft?.confidence || rootCause?.evidence_confidence?.overall || "medium");
  const recommendation = String(notificationDraft?.notify_recommendation || "review_before_send");
  const text = String(
    notificationDraft?.notify_recommendation_text
      || (recommendation === "recommend_notify"
        ? "高可信：建议通知，但仍需要人工确认后发送。"
        : recommendation === "log_only"
          ? "低可信：仅记录，不建议推送。"
          : "中可信：建议人工复核后通知。")
  );
  return {
    confidence,
    confidenceText: confidenceLabel(confidence),
    recommendation,
    text
  };
}

function headline(result: AnyRecord | null, rootCause: AnyRecord | null) {
  if (!result) return "等待分析任务";
  if (result.success === false) return result.message || result.error_message || "执行失败";
  if (result.event_key) return `GMV 异常巡检：${result.demo_report_date || "运行完成"}`;
  if (rootCause) return "Root Cause 分析完成";
  return "分析完成";
}

function first(value: unknown) {
  return Array.isArray(value) && value.length > 0 ? value[0] : undefined;
}

function readValue(row: AnyRecord | undefined, field: string) {
  if (!row) return undefined;
  return row[field] ?? row[field.toUpperCase()] ?? row[toCamelCase(field)];
}

function readNumber(row: AnyRecord | undefined, field: string) {
  const value = readValue(row, field);
  return value === undefined || value === null ? undefined : Number(value);
}

function toCamelCase(field: string) {
  return field.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
}

function metricValue(before: unknown, after: unknown) {
  if (before === undefined || after === undefined) return "-";
  return `${formatNumber(before)} → ${formatNumber(after)}`;
}

function deltaText(before: unknown, after: unknown) {
  if (before === undefined || after === undefined) return "-";
  return formatNumber(Number(after || 0) - Number(before || 0));
}

function formatNumber(value: unknown) {
  const numeric = Number(value || 0);
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(2);
}

function formatPercent(value: unknown) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "-";
  return `${numeric >= 0 ? "+" : ""}${(numeric * 100).toFixed(1)}%`;
}

function statusBadge(value: unknown) {
  const text = String(value || "");
  if (text === "待确认" || text === "已确认" || text === "处理中") return "processing";
  if (text === "已派发") return "warning";
  if (text === "已关闭") return "success";
  return "default";
}

function notificationColor(value: unknown) {
  const text = String(value || "");
  if (text === "已发送飞书") return "green";
  if (text === "去重拦截") return "cyan";
  if (text === "发送失败") return "red";
  if (text === "已降级到日志") return "orange";
  return "default";
}

function resolveCategoryDeltas(currentRows: unknown, previousRows: unknown): CategoryDelta[] {
  if (!Array.isArray(currentRows) || !Array.isArray(previousRows)) return [];
  const current = new Map<string, number>();
  const previous = new Map<string, number>();
  currentRows.forEach((row: AnyRecord) => current.set(String(readValue(row, "category_l1") || readValue(row, "category") || "未分类"), Number(readValue(row, "gmv") || 0)));
  previousRows.forEach((row: AnyRecord) => previous.set(String(readValue(row, "category_l1") || readValue(row, "category") || "未分类"), Number(readValue(row, "gmv") || 0)));
  return [...new Set([...current.keys(), ...previous.keys()])]
    .map((category) => {
      const currentValue = current.get(category) || 0;
      const previousValue = previous.get(category) || 0;
      return { category, current: currentValue, previous: previousValue, delta: currentValue - previousValue };
    })
    .sort((a, b) => a.delta - b.delta);
}

function chartTrendData(previousRegion: AnyRecord | undefined, currentRegion: AnyRecord | undefined) {
  if (!previousRegion || !currentRegion) return [];
  return [
    { date: "前一日", metric: "GMV", value: readNumber(previousRegion, "gmv") || 0 },
    { date: "当前日", metric: "GMV", value: readNumber(currentRegion, "gmv") || 0 },
    { date: "前一日", metric: "支付订单", value: readNumber(previousRegion, "paid_order_count") || 0 },
    { date: "当前日", metric: "支付订单", value: readNumber(currentRegion, "paid_order_count") || 0 }
  ];
}

function normalizeAnomalyRows(items: AnyRecord[]): AnomalyRow[] {
  return items.map((item, index) => ({
    key: String(item.id || `anomaly-${index}`),
    sourceSystem: String(item.source_system_label || item.source_system || "业务监控系统"),
    analyzeEndpoint: String(item.analyze_endpoint || `/api/ecommerce/anomalies/${item.id || `anomaly-${index}`}/analyze`),
    routeName: String(item.analysis_route?.route_name || "GMV 完整 root cause"),
    routeSummary: String(item.analysis_route?.focus_summary || "按标准 root cause 链路排查。"),
    routeOwner: String(item.analysis_route?.primary_owner || item.owner_role || "经营分析"),
    date: String(item.stat_date || item.date || "-"),
    metric: String(item.metric_name || item.metric || "-"),
    scope: `${item.scope_type || "范围"}：${item.scope_name || "-"}`,
    current: formatNumber(item.current_value),
    previous: formatNumber(item.previous_value),
    deltaRate: formatPercent(item.delta_rate),
    severity: String(item.severity || "中"),
    status: String(item.workflow?.process_status || item.status || "待确认"),
    notificationStatus: String(item.workflow?.notification_status || item.notification_status || "未发送") as WorkflowState["notificationStatus"],
    owner: String(item.workflow?.assignee_role || item.owner_role || "经营分析"),
    confidence: confidenceLabel(item.confidence),
    source: String(item.source || "Demo 口径"),
    question: String(item.root_cause_question || DEFAULT_QUESTION),
    description: String(item.description || "需要继续排查异常原因"),
    nextStep: String(item.next_step || "进入 root cause 分析")
  }));
}

function collectWorkflowStates(items: AnyRecord[]) {
  return items.reduce<Record<string, WorkflowState>>((acc, item, index) => {
    const key = String(item.id || `anomaly-${index}`);
    if (item.workflow) {
      acc[key] = workflowFromServer(item.workflow, normalizeAnomalyRows([item])[0], null);
    }
    return acc;
  }, {});
}

function resolveAnalysisRoute(result: AnyRecord | null, selectedAnomaly: AnomalyRow | null) {
  const route = result?.analysis_route || result?.anomaly_event?.analysis_route || {};
  const fallbackDimensions = selectedAnomaly?.metric
    ? [selectedAnomaly.metric, selectedAnomaly.scope, selectedAnomaly.owner].filter(Boolean)
    : ["GMV", "订单量", "用户规模", "品类", "漏斗", "退款"];
  return {
    route_name: route.route_name || selectedAnomaly?.routeName || "GMV 完整 root cause",
    focus_summary: route.focus_summary || selectedAnomaly?.routeSummary || "按标准 root cause 链路排查。",
    primary_owner: route.primary_owner || selectedAnomaly?.routeOwner || selectedAnomaly?.owner || "经营分析",
    handoff_roles: Array.isArray(route.handoff_roles) && route.handoff_roles.length ? route.handoff_roles : [],
    notification_channel: route.notification_channel || "经营异常总览群",
    assignment_rule: route.assignment_rule || "先由数据分析师复核，再派发给主责角色。",
    priority_dimensions: Array.isArray(route.priority_dimensions) && route.priority_dimensions.length ? route.priority_dimensions : fallbackDimensions,
    human_sop: Array.isArray(route.human_sop) && route.human_sop.length
      ? route.human_sop
      : [selectedAnomaly?.nextStep || "确认异常影响范围，并进入 root cause 分析。"],
    agent_policy: route.agent_policy || "人定义排查 SOP，Agent 根据 anomaly_id 自动选择重点并生成证据。"
  };
}

function createWorkflowState(anomaly: AnomalyRow | null, rootCause: AnyRecord | null): WorkflowState {
  return {
    status: (anomaly?.status || "待确认") as WorkflowState["status"],
    notificationStatus: anomaly?.notificationStatus || "未发送",
    confirmedBy: "",
    assignee: anomaly?.routeOwner || anomaly?.owner || "",
    finalReason: "",
    closeNote: "",
    isFalsePositive: false,
    timeline: [
      {
        time: new Date().toLocaleTimeString(),
        actor: "Agent",
        action: rootCause ? "完成异常归因和责任分发草稿" : "等待选择异常并运行分析"
      }
    ]
  };
}

function workflowFromServer(workflow: AnyRecord, anomaly: AnomalyRow | null, rootCause: AnyRecord | null): WorkflowState {
  const events = Array.isArray(workflow.events) ? workflow.events : [];
  const timeline = events.length
    ? events.map((event: AnyRecord) => ({
      time: String(event.created_at || "").replace("T", " ").slice(0, 19) || "-",
      actor: String(event.actor || "系统"),
      action: String(event.action || event.note || "更新处理状态")
    }))
    : createWorkflowState(anomaly, rootCause).timeline;
  return {
    status: String(workflow.process_status || anomaly?.status || "待确认") as WorkflowState["status"],
    notificationStatus: String(workflow.notification_status || anomaly?.notificationStatus || "未发送") as WorkflowState["notificationStatus"],
    confirmedBy: String(workflow.confirmed_by || ""),
    assignee: String(workflow.assignee_role || workflow.assignee_user || anomaly?.routeOwner || anomaly?.owner || ""),
    finalReason: String(workflow.final_reason || ""),
    closeNote: String(workflow.close_note || ""),
    isFalsePositive: Boolean(workflow.is_false_positive),
    timeline
  };
}

function buildWorkflowState(anomaly: AnomalyRow | null, rootCause: AnyRecord | null, store: Record<string, WorkflowState>) {
  const key = anomaly?.key || "";
  return key && store[key] ? store[key] : createWorkflowState(anomaly, rootCause);
}

function summarizeWorkflow(store: Record<string, WorkflowState>, anomalies: AnomalyRow[]) {
  const states = anomalies.map((item) => store[item.key]?.status || item.status || "待确认");
  return {
    total: anomalies.length,
    pending: states.filter((item) => item === "待确认").length,
    confirmed: states.filter((item) => item === "已确认").length,
    dispatched: states.filter((item) => item === "已派发").length,
    processing: states.filter((item) => item === "处理中").length,
    closed: states.filter((item) => item === "已关闭").length,
    falsePositive: states.filter((item) => item === "误报").length
  };
}

function applyWorkflowToRows(rows: AnomalyRow[], store: Record<string, WorkflowState>) {
  return rows.map((row) => {
    const state = store[row.key];
    if (!state) {
      return { ...row, notificationStatus: "未发送" as const };
    }
    return {
      ...row,
      status: state.status,
      notificationStatus: state.notificationStatus,
      owner: state.assignee || row.routeOwner || row.owner
    };
  });
}

function filterAnomalyRowsForRole(rows: AnomalyRow[], role: RoleKey, store: Record<string, WorkflowState>) {
  if (role === "analyst" || role === "manager") return rows;
  return rows.filter((row) => {
    const state = store[row.key];
    return (state?.status === "已派发" || state?.status === "处理中" || state?.status === "已关闭")
      && ownerMatchesRole(state.assignee || row.routeOwner || row.owner, role);
  });
}

function filterRouteRowsForRole(rows: RouteRow[], role: RoleKey) {
  if (role === "analyst" || role === "manager") return rows;
  return rows.filter((row) => ownerMatchesRole(row.owner, role));
}

function ownerMatchesRole(owner: string, role: RoleKey) {
  if (role === "platform") return owner.includes("平台") || owner.includes("经营分析");
  if (role === "category") return owner.includes("类目") || owner.includes("行业");
  if (role === "growth") return owner.includes("增长") || owner.includes("营销");
  if (role === "after_sales") return owner.includes("售后") || owner.includes("治理");
  return true;
}

function roleLabel(role: RoleKey) {
  if (role === "analyst") return "数据分析师";
  if (role === "platform") return "平台运营";
  if (role === "category") return "类目运营";
  if (role === "growth") return "增长运营";
  if (role === "after_sales") return "售后治理";
  return "运营主管";
}

function roleTitle(role: RoleKey) {
  if (role === "analyst") return "数据分析师视角：确认异常、判断可信度、决定是否派发";
  if (role === "platform") return "平台运营视角：看区域和大盘异常，判断是否升级处理";
  if (role === "category") return "类目运营视角：看品类、商品、商家、库存和活动资源";
  if (role === "growth") return "增长运营视角：看用户、流量、转化、人群和投放节奏";
  if (role === "after_sales") return "售后治理视角：看退款、退货、履约和客服问题";
  return "运营主管视角：看异常池状态、闭环进度和误报情况";
}

function roleDescription(role: RoleKey) {
  if (role === "analyst") return "重点看核心结论、主要原因排序、证据来源和责任分发。";
  if (role === "platform") return "重点看平台经营大盘、区域影响范围、是否需要拉齐其他团队。";
  if (role === "category") return "重点看拖累品类、重点商品/商家、库存和活动资源变化。";
  if (role === "growth") return "重点看 DAU、活跃买家、渠道流量、活动曝光和转化承接。";
  if (role === "after_sales") return "重点看退款金额、退款订单、售后原因、物流和客服线索。";
  return "重点看待确认、已派发、处理中、已关闭和误报的整体分布。";
}

function toStringList(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item)).filter(Boolean);
}

function buildRouteRows(routes: AnyRecord[]): RouteRow[] {
  return routes.map((item, index) => ({
    key: String(index),
    owner: item.owner_name || item.owner_role || item.role || item.title || "业务负责人",
    signal: item.reason || item.signal || item.title || item.owner_key || "异常线索",
    problem: item.problem || item.reason || "需要继续定位具体问题",
    evidence: item.evidence || item.description || "需要结合指标继续排查",
    actionPlan: Array.isArray(item.action_plan) && item.action_plan.length
      ? item.action_plan
      : [item.next_action || item.action || item.suggested_action || "确认影响范围，并同步相关业务负责人"],
    drilldown: Array.isArray(item.drilldown_objects) && item.drilldown_objects.length ? item.drilldown_objects.join("；") : "待补充",
    confidence: confidenceLabel(item.confidence || item.evidence_confidence)
  }));
}

function buildCauseRows(rootCause: AnyRecord | null): CauseRow[] {
  const rows = Array.isArray(rootCause?.cause_ranking) && rootCause.cause_ranking.length
    ? rootCause.cause_ranking
    : Array.isArray(rootCause?.sections)
      ? rootCause.sections.filter((item: AnyRecord) => String(item.status || "") === "signal")
      : [];
  return rows.slice(0, 5).map((item: AnyRecord, index: number) => ({
    key: String(item.key || `cause-${index}`),
    title: String(item.title || "异常原因"),
    summary: String(item.summary || item.description || "需要继续补充证据"),
    source: String(item.source || item.data_source || ""),
    rankReason: String(item.rank_reason || "按当前异常类型的排查重点排序")
  }));
}

function buildToolEvidenceRows(rootCause: AnyRecord | null): ToolEvidenceRow[] {
  const sections = Array.isArray(rootCause?.sections) ? rootCause.sections : [];
  return sections.slice(0, 4).map((item: AnyRecord, index: number) => ({
    key: String(item.key || `tool-evidence-${index}`),
    title: String(item.title || item.tool_name || "Tool 结果"),
    summary: String(item.summary || item.description || item.reason || "暂无摘要"),
    source: String(item.source || item.data_source || lineageLabel(item)),
    confidence: confidenceLabel(item.confidence || item.evidence_confidence || rootCause?.evidence_confidence?.overall)
  }));
}

function stripStepPrefix(value: string) {
  return value.replace(/^\s*\d+[.、]\s*/, "");
}

function notificationPreview(value: string) {
  const lines = value
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const summaryLine = lines.find((line) => line.startsWith("核心结论："));
  if (summaryLine) {
    return summaryLine.replace("核心结论：", "");
  }
  return lines.slice(0, 2).join(" ");
}

function dataSourcePie(rootCause: AnyRecord | null) {
  const serialized = JSON.stringify(rootCause?.data_lineage || rootCause?.facts?.data_lineage || {});
  const olist = (serialized.match(/olist_public_dataset/g) || []).length;
  const demo = (serialized.match(/demo_seed|demo/g) || []).length;
  return [
    { type: "Olist 公开数据", value: Math.max(olist, 1) },
    { type: "Demo 补齐口径", value: Math.max(demo, 1) }
  ];
}

function lineageText(rootCause: AnyRecord | null, keyword: string) {
  const lineage = rootCause?.data_lineage || rootCause?.facts?.data_lineage || [];
  const serialized = JSON.stringify(lineage).toLowerCase();
  if (serialized.includes(keyword)) {
    return keyword === "olist" ? "区域 / 订单 / 品类优先使用 Olist 公开数据" : "用户 / 漏斗 / 退款使用 demo 补齐口径";
  }
  return keyword === "olist" ? "Olist 支线可用时优先使用" : "行为流与售后明细不足时使用补齐口径";
}

function lineageLabel(value: AnyRecord) {
  const serialized = JSON.stringify(value).toLowerCase();
  if (serialized.includes("olist")) return "Olist 公开数据";
  if (serialized.includes("demo")) return "Demo 补齐口径";
  return "当前分析结果";
}

function confidenceLabel(value: unknown) {
  const text = String(value || "");
  if (text.includes("high") || text === "高") return "高";
  if (text.includes("medium") || text === "中") return "中";
  if (text.includes("low") || text === "低") return "低";
  return "中";
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}

ReactDOM.createRoot(document.getElementById("root")!).render(<App />);
