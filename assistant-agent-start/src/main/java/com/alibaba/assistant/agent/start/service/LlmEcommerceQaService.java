package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.tool.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 驱动的电商问答服务。
 *
 * LLM 通过 function calling 自主决定调用哪些数据工具，再基于真实数据生成自然语言回答。
 * 相比规则路由版本，无需预定义意图和字符串模板。
 */
@Service
public class LlmEcommerceQaService {

    private static final String SYSTEM_PROMPT = """
            你是一个电商运营数据分析助手，负责回答关于电商业务指标的问题。

            你可以调用以下工具查询真实的业务数据：
            - GmvQueryTool：查询指定日期的大盘 GMV、支付订单数、活跃买家数、DAU、退款率
            - OrderQueryTool：查询订单量、支付订单数、退款订单、客单价、总支付金额；支持按区域/品类筛选
            - UserMetricTool：查询 DAU、活跃买家数、买家激活率；支持按区域筛选
            - RegionPerformanceQueryTool：查询各区域 GMV、订单表现
            - CategoryRankTool：查询品类 GMV 排行；支持按区域筛选
            - RefundAnalysisTool：查询退款集中的品类分布
            - FunnelAnalysisTool：查询从浏览到支付的漏斗各步骤转化率

            【数据范围】
            - 当前接的是 Olist 巴西电商公开数据集，覆盖 2016-09 至 2018-09。
            - 数据量较大的代表日期：2017-11-24（黑五，全年峰值）、2018-05 多个高峰日。
            - 典型环比下跌可演示日期：2017-11-25（黑五次日 -60%）、2018-05-17（月中 -44%）。

            【工作规范】
            - 收到问题后，主动调用相关工具获取数据，不要猜测数据
            - 若问题涉及原因归因（"为什么跌"、"什么原因"），先查大盘，再查区域/品类/退款/漏斗多个维度
            - 数据查询完毕后，用简洁中文给出分析结论，指出关键数字
            - 日期若未明确，使用 2018-08-29（数据集中有完整快照的日期）
            - 回答聚焦业务数据，不要输出工具调用的原始 JSON
            """;

    private final ChatClient chatClient;
    private final Map<String, List<Message>> sessionHistories = new ConcurrentHashMap<>();

    public LlmEcommerceQaService(ChatModel chatModel,
                                  GmvQueryTool gmvQueryTool,
                                  OrderQueryTool orderQueryTool,
                                  UserMetricTool userMetricTool,
                                  RegionPerformanceQueryTool regionPerformanceQueryTool,
                                  CategoryRankTool categoryRankTool,
                                  RefundAnalysisTool refundAnalysisTool,
                                  FunnelAnalysisTool funnelAnalysisTool) {
        ToolCallback[] tools = {
                gmvQueryTool, orderQueryTool, userMetricTool,
                regionPerformanceQueryTool, categoryRankTool,
                refundAnalysisTool, funnelAnalysisTool
        };
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(tools)
                .build();
    }

    public Map<String, Object> answer(String sessionId, String question) {
        List<Message> history = sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());

        try {
            String answer = chatClient.prompt()
                    .messages(history)
                    .user(question)
                    .call()
                    .content();

            history.add(new UserMessage(question));
            history.add(new AssistantMessage(answer));
            if (history.size() > 20) {
                history.subList(0, history.size() - 20).clear();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("question", question);
            result.put("answer", answer);
            result.put("path_type", "llm_function_calling");
            result.put("session_id", sessionId);
            return result;
        }
        catch (Exception ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("question", question);
            result.put("message", "LLM 分析失败：" + ex.getMessage());
            result.put("path_type", "llm_function_calling");
            result.put("session_id", sessionId);
            return result;
        }
    }

    public void clearSession(String sessionId) {
        sessionHistories.remove(sessionId);
    }
}
